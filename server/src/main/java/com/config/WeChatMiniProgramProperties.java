package com.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

/** 微信小程序服务端配置。AppSecret 只能由本地密钥文件或环境变量提供。 */
@ConfigurationProperties(prefix = "wechat.mini-program")
public class WeChatMiniProgramProperties {
    private String appId;
    private String appSecret;
    private URI code2SessionUrl;
    private String proxyHost;
    private int proxyPort;

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public URI getCode2SessionUrl() {
        return code2SessionUrl;
    }

    public void setCode2SessionUrl(URI code2SessionUrl) {
        this.code2SessionUrl = code2SessionUrl;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public boolean isConfigured() {
        return hasText(appId) && hasText(appSecret) && code2SessionUrl != null;
    }

    public boolean isProxyConfigured() {
        return hasText(proxyHost) && proxyPort > 0 && proxyPort <= 65535;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
