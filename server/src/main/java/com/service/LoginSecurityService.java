package com.service;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 登录安全服务：登录失败锁定 + 登录接口 IP 限流。
 *
 * <p>状态优先存储在 Redis（启用 redis profile 时），未启用 Redis 时自动降级为本机内存存储，
 * 保证本地开发无需 Redis 也能正常工作，部署服务器时叠加 ,redis 即享受分布式安全。</p>
 */
@Service
public class LoginSecurityService {

    private static final Logger logger = LoggerFactory.getLogger(LoginSecurityService.class);

    private static final String PREFIX = "zya:login:";

    /** 账号连续失败多少次后锁定 */
    @Value("${app.security.login.max-fail:5}")
    private int maxFail;

    /** 锁定时长（分钟） */
    @Value("${app.security.login.lock-minutes:15}")
    private int lockMinutes;

    /** 失败计数的统计窗口（分钟）：在此窗口内累计失败 */
    @Value("${app.security.login.fail-window-minutes:15}")
    private int failWindowMinutes;

    /** 单 IP 每分钟最多允许的登录请求次数 */
    @Value("${app.security.login.ip-rate-per-minute:20}")
    private int ipRatePerMinute;

    /** Redis 可选注入：未启用 redis profile 时为 null，走内存降级。 */
    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    /** 内存降级存储：key -> [值, 过期时间戳ms] */
    private final ConcurrentHashMap<String, long[]> memStore = new ConcurrentHashMap<>();

    private boolean useRedis() {
        return redisTemplate != null;
    }

    // ============================ 登录失败锁定 ============================

    /**
     * 检查账号是否处于锁定状态。
     *
     * @return 剩余锁定秒数；0 表示未锁定。
     */
    public long getLockRemainingSeconds(String account) {
        if (account == null || account.trim().isEmpty()) return 0;
        String key = PREFIX + "lock:" + account.trim().toLowerCase();
        return ttlSeconds(key);
    }

    /**
     * 记录一次登录失败。达到阈值则锁定账号。
     *
     * @return 距离锁定还剩多少次机会；0 表示已锁定。
     */
    public int recordFailure(String account) {
        if (account == null || account.trim().isEmpty()) return maxFail;
        String acc = account.trim().toLowerCase();
        String failKey = PREFIX + "fail:" + acc;
        long count = incr(failKey, failWindowMinutes * 60L);
        if (count >= maxFail) {
            String lockKey = PREFIX + "lock:" + acc;
            setValue(lockKey, "1", lockMinutes * 60L);
            del(failKey);
            logger.warn("账号 [{}] 连续登录失败 {} 次，已锁定 {} 分钟", account, count, lockMinutes);
            return 0;
        }
        return (int) Math.max(0, maxFail - count);
    }

    /** 登录成功，清除该账号的失败计数与锁定。 */
    public void clearFailure(String account) {
        if (account == null || account.trim().isEmpty()) return;
        String acc = account.trim().toLowerCase();
        del(PREFIX + "fail:" + acc);
        del(PREFIX + "lock:" + acc);
    }

    public int getMaxFail() {
        return maxFail;
    }

    public int getLockMinutes() {
        return lockMinutes;
    }

    // ============================ IP 限流 ============================

    /**
     * 校验某 IP 的登录请求频率是否超限。
     *
     * @return true 表示允许；false 表示已超限。
     */
    public boolean allowIp(String ip) {
        if (ip == null || ip.isEmpty()) return true;
        String key = PREFIX + "ip:" + ip;
        long count = incr(key, 60L);
        return count <= ipRatePerMinute;
    }

    /**
     * 从请求中提取真实客户端 IP（兼容 Nginx 反向代理）。
     *
     * <p>取值顺序：{@code X-Real-IP} → {@code X-Forwarded-For} 最右侧一段 → {@code remoteAddr}。
     * {@code X-Real-IP} 由 Nginx 以 {@code $remote_addr} 覆盖写入，最右侧 XFF 段由直连的
     * Nginx 追加，二者都不可被客户端伪造。</p>
     *
     * <p>此前取的是 XFF <b>最左侧</b>一段——那是客户端可以随意伪造的（Nginx 用
     * {@code $proxy_add_x_forwarded_for} 追加而非覆盖），于是每请求换一个
     * {@code X-Forwarded-For} 即可绕过所有按 IP 的限流（登录爆破、入驻申请与上传）。
     * 后端端口若直接对外暴露，请另行用防火墙只放行 Nginx，否则任何 Header 方案都可绕过。</p>
     */
    public String resolveIp(HttpServletRequest request) {
        if (request == null) return "";
        String realIp = request.getHeader("X-Real-IP");
        if (isUsableIp(realIp)) return realIp.trim();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (isUsableIp(forwarded)) {
            int comma = forwarded.lastIndexOf(',');
            return (comma >= 0 ? forwarded.substring(comma + 1) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }

    private boolean isUsableIp(String value) {
        return value != null && !value.isEmpty() && !"unknown".equalsIgnoreCase(value);
    }

    // ============================ 底层存储（Redis / 内存） ============================

    private long incr(String key, long ttlSeconds) {
        if (useRedis()) {
            Long v = redisTemplate.opsForValue().increment(key);
            if (v != null && v == 1L) {
                redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
            }
            return v == null ? 1L : v;
        }
        return memIncr(key, ttlSeconds);
    }

    private void setValue(String key, String value, long ttlSeconds) {
        if (useRedis()) {
            redisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
        } else {
            memStore.put(key, new long[]{1L, System.currentTimeMillis() + ttlSeconds * 1000L});
        }
    }

    private void del(String key) {
        if (useRedis()) {
            redisTemplate.delete(key);
        } else {
            memStore.remove(key);
        }
    }

    private long ttlSeconds(String key) {
        if (useRedis()) {
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            return ttl == null || ttl < 0 ? 0 : ttl;
        }
        long[] e = memStore.get(key);
        if (e == null) return 0;
        long remain = e[1] - System.currentTimeMillis();
        if (remain <= 0) {
            memStore.remove(key);
            return 0;
        }
        return remain / 1000L;
    }

    private long memIncr(String key, long ttlSeconds) {
        long now = System.currentTimeMillis();
        long[] entry = memStore.compute(key, (k, old) -> {
            if (old == null || old[1] <= now) {
                return new long[]{1L, now + ttlSeconds * 1000L};
            }
            return new long[]{old[0] + 1L, old[1]};
        });
        return entry[0];
    }
}
