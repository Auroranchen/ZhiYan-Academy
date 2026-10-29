package com.controller;

import com.response.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * 讯飞开放平台 WebSocket 接口鉴权（语音听写 IAT + 在线语音合成 TTS）。
 * 签名在服务端完成，避免 APIKey / APISecret 暴露到浏览器；
 * 前端拿到带签名的 wss 地址后直连讯飞服务器。
 */
@RestController
@RequestMapping("/asr")
public class AsrController {

    private static final Logger logger = LoggerFactory.getLogger(AsrController.class);

    // ---- 语音听写（IAT）----
    @Value("${ai.xfyun.app-id:}")
    private String iatAppId;

    @Value("${ai.xfyun.api-key:}")
    private String iatApiKey;

    @Value("${ai.xfyun.api-secret:}")
    private String iatApiSecret;

    @Value("${ai.xfyun.host:iat-api.xfyun.cn}")
    private String iatHost;

    @Value("${ai.xfyun.path:/v2/iat}")
    private String iatPath;

    // ---- 在线语音合成（TTS）----
    @Value("${ai.xfyun-tts.app-id:}")
    private String ttsAppId;

    @Value("${ai.xfyun-tts.api-key:}")
    private String ttsApiKey;

    @Value("${ai.xfyun-tts.api-secret:}")
    private String ttsApiSecret;

    @Value("${ai.xfyun-tts.host:tts-api.xfyun.cn}")
    private String ttsHost;

    @Value("${ai.xfyun-tts.path:/v2/tts}")
    private String ttsPath;

    /**
     * 语音听写 WebSocket 鉴权地址（HMAC-SHA256 签名，有效期约 5 分钟）
     */
    @GetMapping("/sign")
    public Result<Map<String, Object>> sign() {
        return buildSigned(iatAppId, iatApiKey, iatApiSecret, iatHost, iatPath);
    }

    /**
     * 在线语音合成 WebSocket 鉴权地址
     */
    @GetMapping("/tts-sign")
    public Result<Map<String, Object>> ttsSign() {
        return buildSigned(ttsAppId, ttsApiKey, ttsApiSecret, ttsHost, ttsPath);
    }

    private Result<Map<String, Object>> buildSigned(String appId, String apiKey, String apiSecret,
                                                    String host, String path) {
        if (appId == null || appId.isEmpty() || apiKey.isEmpty() || apiSecret.isEmpty()) {
            return Result.error("语音服务未配置");
        }
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
            String date = sdf.format(new Date());

            String signatureOrigin = "host: " + host + "\n" +
                    "date: " + date + "\n" +
                    "GET " + path + " HTTP/1.1";

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = Base64.getEncoder()
                    .encodeToString(mac.doFinal(signatureOrigin.getBytes(StandardCharsets.UTF_8)));

            String authorizationOrigin = String.format(
                    "api_key=\"%s\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"%s\"",
                    apiKey, signature);
            String authorization = Base64.getEncoder()
                    .encodeToString(authorizationOrigin.getBytes(StandardCharsets.UTF_8));

            String url = "wss://" + host + path
                    + "?authorization=" + urlEncode(authorization)
                    + "&date=" + urlEncode(date)
                    + "&host=" + urlEncode(host);

            Map<String, Object> data = new HashMap<>();
            data.put("url", url);
            data.put("appid", appId);
            return Result.success(data);
        } catch (Exception e) {
            logger.error("生成讯飞签名失败 [{}]: {}", path, e.getMessage());
            return Result.error("语音服务签名生成失败");
        }
    }

    private String urlEncode(String s) throws Exception {
        // URLEncoder 将空格编码为 +，而签名校验要求 %20
        return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
    }
}
