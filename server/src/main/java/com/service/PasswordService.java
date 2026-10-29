package com.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 密码哈希与存量明文兼容迁移。
 */
@Service
public class PasswordService {

    private static final String BCRYPT_PREFIX = "$2";
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    public String encode(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) return false;
        if (isEncoded(storedPassword)) {
            try {
                return encoder.matches(rawPassword, storedPassword);
            } catch (IllegalArgumentException ignored) {
                return false;
            }
        }
        // 仅用于首次登录迁移存量明文密码。
        return constantTimeEquals(rawPassword, storedPassword);
    }

    public boolean needsMigration(String storedPassword) {
        return storedPassword != null && !isEncoded(storedPassword);
    }

    private boolean isEncoded(String password) {
        return password.startsWith(BCRYPT_PREFIX);
    }

    private boolean constantTimeEquals(String left, String right) {
        byte[] a = left.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] b = right.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int diff = a.length ^ b.length;
        int max = Math.max(a.length, b.length);
        for (int i = 0; i < max; i++) {
            byte av = i < a.length ? a[i] : 0;
            byte bv = i < b.length ? b[i] : 0;
            diff |= av ^ bv;
        }
        return diff == 0;
    }
}
