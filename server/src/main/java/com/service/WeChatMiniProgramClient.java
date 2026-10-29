package com.service;

import com.config.WeChatMiniProgramProperties;
import com.exception.WeChatAuthException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;

/** 调用微信 code2Session，仅提取身份字段，不持久化 session_key。 */
@Service
public class WeChatMiniProgramClient {
    private static final Logger log = LoggerFactory.getLogger(WeChatMiniProgramClient.class);
    private final WeChatMiniProgramProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public WeChatMiniProgramClient(WeChatMiniProgramProperties properties,
                                   RestClient.Builder builder,
                                   ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        if (properties.isProxyConfigured()) {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setProxy(new Proxy(Proxy.Type.HTTP,
                    new InetSocketAddress(properties.getProxyHost(), properties.getProxyPort())));
            requestFactory.setConnectTimeout(10_000);
            requestFactory.setReadTimeout(15_000);
            builder.requestFactory(requestFactory);
        }
        this.restClient = builder.build();
    }

    public WeChatIdentity exchangeCode(String code) {
        if (!properties.isConfigured()) {
            throw new WeChatAuthException(503, "微信登录尚未完成服务端配置");
        }
        URI uri = UriComponentsBuilder.fromUri(properties.getCode2SessionUrl())
                .queryParam("appid", properties.getAppId())
                .queryParam("secret", properties.getAppSecret())
                .queryParam("js_code", code)
                .queryParam("grant_type", "authorization_code")
                .build()
                .encode()
                .toUri();
        try {
            String responseBody = restClient.get().uri(uri).retrieve().body(String.class);
            if (responseBody == null || responseBody.isBlank()) {
                throw new WeChatAuthException(502, "微信登录服务暂时无响应");
            }
            Code2SessionResponse response = parseResponse(responseBody);
            if (response.errcode() != null && response.errcode() != 0) {
                throw new WeChatAuthException(responseCode(response.errcode()), friendlyMessage(response.errcode()));
            }
            if (response.openid() == null || response.openid().isBlank()) {
                throw new WeChatAuthException(502, "微信登录未返回用户标识");
            }
            return new WeChatIdentity(response.openid(), response.unionid());
        } catch (WeChatAuthException exception) {
            throw exception;
        } catch (RestClientException exception) {
            log.warn("WeChat code2Session connection failed: {}", rootCauseSummary(exception));
            throw new WeChatAuthException(502,
                    "后端无法连接微信登录服务，请检查服务器网络、DNS 或代理设置");
        }
    }

    private Code2SessionResponse parseResponse(String responseBody) {
        try {
            return objectMapper.readValue(responseBody, Code2SessionResponse.class);
        } catch (JsonProcessingException exception) {
            log.warn("WeChat code2Session returned an invalid payload");
            throw new WeChatAuthException(502, "微信登录服务返回内容异常");
        }
    }

    public String appId() {
        return properties.getAppId();
    }

    private String friendlyMessage(int errcode) {
        return switch (errcode) {
            case 40029 -> "微信登录凭证无效，请重新授权";
            case 45011 -> "微信登录请求过于频繁，请稍后重试";
            case 40226 -> "当前微信账号存在安全风险，暂时无法登录";
            default -> "微信登录校验失败，请稍后重试";
        };
    }

    private int responseCode(int errcode) {
        return switch (errcode) {
            case 45011 -> 429;
            case 40226 -> 403;
            default -> 400;
        };
    }

    private String rootCauseSummary(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) cause = cause.getCause();
        String message = cause.getMessage();
        return cause.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    public record WeChatIdentity(String openid, String unionid) {}

    private record Code2SessionResponse(
            String openid,
            @JsonProperty("session_key") String sessionKey,
            String unionid,
            Integer errcode,
            String errmsg
    ) {}
}
