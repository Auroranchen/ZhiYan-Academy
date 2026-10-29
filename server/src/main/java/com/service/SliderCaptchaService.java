package com.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 滑动拼图验证码服务。
 *
 * <p>服务端生成带缺口的背景图与拼图块，记录正确的缺口横坐标；用户拖动滑块对齐缺口后，
 * 服务端校验偏差并签发一次性令牌。所有状态（缺口位置、令牌）存 Redis（启用 redis profile 时），
 * 未启用 Redis 时自动降级为本机内存，保证本地开发零依赖。</p>
 */
@Service
public class SliderCaptchaService {

    private static final Logger logger = LoggerFactory.getLogger(SliderCaptchaService.class);

    private static final String GAP_PREFIX = "zya:captcha:gap:";
    private static final String TOKEN_PREFIX = "zya:captcha:token:";

    /** 画布尺寸 */
    private static final int WIDTH = 320;
    private static final int HEIGHT = 160;
    /** 拼图块边长 */
    private static final int PIECE = 50;
    /** 校验允许的像素偏差 */
    private static final int TOLERANCE = 6;
    /** 缺口位置 / 令牌有效期（秒） */
    private static final long TTL_SECONDS = 120;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    /** 内存降级：key -> [值, 过期时间戳ms]；gap 存横坐标，token 存 1 */
    private final ConcurrentHashMap<String, long[]> memStore = new ConcurrentHashMap<>();

    private boolean useRedis() {
        return redisTemplate != null;
    }

    // ============================ 对外接口 ============================

    /**
     * 生成一道滑动拼图验证。
     *
     * @return captchaId、背景图(base64)、拼图块(base64)、拼图块纵向位置 y
     */
    public Map<String, Object> generate() {
        int gapX = ThreadLocalRandom.current().nextInt(PIECE + 30, WIDTH - PIECE - 10);
        int gapY = ThreadLocalRandom.current().nextInt(10, HEIGHT - PIECE - 10);

        BufferedImage bg = drawBackground();
        BufferedImage piece = cutPiece(bg, gapX, gapY);
        digHole(bg, gapX, gapY);

        String captchaId = UUID.randomUUID().toString().replace("-", "");
        putGap(captchaId, gapX);

        Map<String, Object> data = new HashMap<>();
        data.put("captchaId", captchaId);
        data.put("image", "data:image/png;base64," + toBase64(bg));
        data.put("block", "data:image/png;base64," + toBase64(piece));
        data.put("y", gapY);
        data.put("width", WIDTH);
        data.put("height", HEIGHT);
        data.put("pieceSize", PIECE);
        return data;
    }

    /**
     * 校验用户拖动的横坐标。
     *
     * @return success=true 时附带一次性 token，登录时回传。
     */
    public Map<String, Object> verify(String captchaId, int x) {
        Map<String, Object> result = new HashMap<>();
        Long gapX = getGap(captchaId);
        // 一次性：无论对错都清除缺口记录，防止重放
        delGap(captchaId);
        if (gapX == null) {
            result.put("success", false);
            result.put("message", "验证已过期，请刷新重试");
            return result;
        }
        if (Math.abs(x - gapX) <= TOLERANCE) {
            String token = UUID.randomUUID().toString().replace("-", "");
            putToken(token);
            result.put("success", true);
            result.put("token", token);
        } else {
            result.put("success", false);
            result.put("message", "拼图未对齐，请重试");
        }
        return result;
    }

    /**
     * 登录时校验并消费令牌（一次性）。
     *
     * @return 令牌有效返回 true，并立即作废。
     */
    public boolean consumeToken(String token) {
        if (token == null || token.trim().isEmpty()) return false;
        String key = TOKEN_PREFIX + token.trim();
        boolean exists;
        if (useRedis()) {
            exists = Boolean.TRUE.equals(redisTemplate.hasKey(key));
            if (exists) redisTemplate.delete(key);
        } else {
            long[] e = memStore.get(key);
            exists = e != null && e[1] > System.currentTimeMillis();
            memStore.remove(key);
        }
        return exists;
    }

    /** 只校验、不消费，用于验证码通过后的受限上传；最终业务提交时再消费。 */
    public boolean isTokenValid(String token) {
        if (token == null || token.trim().isEmpty()) return false;
        String key = TOKEN_PREFIX + token.trim();
        if (useRedis()) return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        long[] entry = memStore.get(key);
        if (entry == null || entry[1] <= System.currentTimeMillis()) {
            memStore.remove(key);
            return false;
        }
        return true;
    }

    // ============================ 绘图 ============================

    private BufferedImage drawBackground() {
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        // 渐变底色
        Color c1 = Color.getHSBColor(rnd.nextFloat(), 0.45f, 0.85f);
        Color c2 = Color.getHSBColor(rnd.nextFloat(), 0.45f, 0.6f);
        g.setPaint(new GradientPaint(0, 0, c1, WIDTH, HEIGHT, c2));
        g.fillRect(0, 0, WIDTH, HEIGHT);
        // 装饰圆点，增加纹理，避免被简单算法定位
        for (int i = 0; i < 8; i++) {
            int r = rnd.nextInt(20, 60);
            int cx = rnd.nextInt(0, WIDTH);
            int cy = rnd.nextInt(0, HEIGHT);
            g.setColor(new Color(255, 255, 255, rnd.nextInt(20, 70)));
            g.fillOval(cx, cy, r, r);
        }
        g.dispose();
        return img;
    }

    private BufferedImage cutPiece(BufferedImage bg, int gapX, int gapY) {
        BufferedImage piece = new BufferedImage(PIECE, PIECE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = piece.createGraphics();
        pg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        pg.drawImage(bg.getSubimage(gapX, gapY, PIECE, PIECE), 0, 0, null);
        // 白色描边，让拼图块在背景上清晰可辨
        pg.setStroke(new BasicStroke(2f));
        pg.setColor(new Color(255, 255, 255, 220));
        pg.drawRect(1, 1, PIECE - 3, PIECE - 3);
        pg.dispose();
        return piece;
    }

    private void digHole(BufferedImage bg, int gapX, int gapY) {
        Graphics2D g = bg.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0, 0, 0, 130));
        g.fillRect(gapX, gapY, PIECE, PIECE);
        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(255, 255, 255, 90));
        g.drawRect(gapX, gapY, PIECE, PIECE);
        g.dispose();
    }

    private String toBase64(BufferedImage img) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            logger.error("验证码图片编码失败: {}", e.getMessage());
            return "";
        }
    }

    // ============================ 存储（Redis / 内存） ============================

    private void putGap(String captchaId, int gapX) {
        String key = GAP_PREFIX + captchaId;
        if (useRedis()) {
            redisTemplate.opsForValue().set(key, String.valueOf(gapX), TTL_SECONDS, TimeUnit.SECONDS);
        } else {
            memStore.put(key, new long[]{gapX, System.currentTimeMillis() + TTL_SECONDS * 1000L});
        }
    }

    private Long getGap(String captchaId) {
        if (captchaId == null || captchaId.trim().isEmpty()) return null;
        String key = GAP_PREFIX + captchaId.trim();
        if (useRedis()) {
            String v = redisTemplate.opsForValue().get(key);
            return v == null ? null : Long.parseLong(v);
        }
        long[] e = memStore.get(key);
        if (e == null || e[1] <= System.currentTimeMillis()) {
            memStore.remove(key);
            return null;
        }
        return e[0];
    }

    private void delGap(String captchaId) {
        String key = GAP_PREFIX + captchaId;
        if (useRedis()) redisTemplate.delete(key);
        else memStore.remove(key);
    }

    private void putToken(String token) {
        String key = TOKEN_PREFIX + token;
        if (useRedis()) {
            redisTemplate.opsForValue().set(key, "1", TTL_SECONDS, TimeUnit.SECONDS);
        } else {
            memStore.put(key, new long[]{1L, System.currentTimeMillis() + TTL_SECONDS * 1000L});
        }
    }
}
