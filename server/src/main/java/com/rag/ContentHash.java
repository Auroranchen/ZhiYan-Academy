package com.rag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 内容的 SHA-256 指纹——**索引失效判断的依据**。
 *
 * <p>刻意独立于 {@link EmbeddingService}：它跟用什么向量器毫无关系，
 * 只是回答「这段文本变了吗」。混在向量服务里会让人误以为换模型也要跟着改指纹算法，
 * 而一旦指纹算法变了，全部历史索引会被判定为过期并触发一次全量重建。
 *
 * <p>因此本类的行为必须保持**字节稳定**：任何改动都意味着一次全量重建。
 */
public final class ContentHash {

    private ContentHash() {
    }

    public static String of(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(text).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : digest) out.append(String.format("%02x", b));
            return out.toString();
        } catch (Exception e) {
            // SHA-256 在所有 JVM 上都必须存在；退路只是不让整个索引流程崩掉
            return Integer.toHexString(String.valueOf(text).hashCode());
        }
    }
}
