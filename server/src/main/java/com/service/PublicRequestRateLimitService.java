package com.service;

import com.config.EnterpriseSecurityProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/** 公开入口独立限流；启用 Redis 时自动支持多实例。 */
@Service
public class PublicRequestRateLimitService {
    private static final String PREFIX = "zya:public-rate:";
    private static final long WINDOW_MS = Duration.ofHours(1).toMillis();

    private final StringRedisTemplate redis;
    private final EnterpriseSecurityProperties properties;
    private final ConcurrentHashMap<String, Window> local = new ConcurrentHashMap<>();

    public PublicRequestRateLimitService(ObjectProvider<StringRedisTemplate> redisProvider,
                                         EnterpriseSecurityProperties properties) {
        this.redis = redisProvider.getIfAvailable();
        this.properties = properties;
    }

    public boolean allowUpload(String ip) {
        return allow("upload", ip, properties.getPublicEndpoints().getUploadRequestsPerHour());
    }

    public boolean allowApply(String ip) {
        return allow("apply", ip, properties.getPublicEndpoints().getApplyRequestsPerHour());
    }

    private boolean allow(String scope, String ip, int limit) {
        String normalizedIp = ip == null || ip.isBlank() ? "unknown" : ip.trim();
        String key = PREFIX + scope + ":" + normalizedIp;
        if (redis != null) {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) redis.expire(key, Duration.ofHours(1));
            return count == null || count <= limit;
        }
        long now = System.currentTimeMillis();
        Window value = local.compute(key, (ignored, old) ->
                old == null || old.expiresAt <= now
                        ? new Window(1, now + WINDOW_MS)
                        : new Window(old.count + 1, old.expiresAt));
        if (local.size() > 10_000) {
            local.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
        }
        return value.count <= limit;
    }

    private record Window(long count, long expiresAt) {}
}
