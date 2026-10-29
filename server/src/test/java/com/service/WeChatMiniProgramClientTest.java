package com.service;

import com.config.WeChatMiniProgramProperties;
import com.exception.WeChatAuthException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WeChatMiniProgramClientTest {
    @Test
    void exchangeCodeReturnsOpenidWithoutExposingSessionKey() {
        WeChatMiniProgramProperties properties = properties();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo(startsWith(properties.getCode2SessionUrl().toString())))
                .andExpect(queryParam("appid", "test-app"))
                .andExpect(queryParam("secret", "test-secret"))
                .andExpect(queryParam("js_code", "wx-code"))
                .andExpect(queryParam("grant_type", "authorization_code"))
                .andRespond(withSuccess(
                        "{\"openid\":\"openid-1\",\"session_key\":\"never-store\",\"unionid\":\"unionid-1\"}",
                        MediaType.TEXT_PLAIN));
        WeChatMiniProgramClient client = new WeChatMiniProgramClient(properties, builder, new ObjectMapper());

        WeChatMiniProgramClient.WeChatIdentity identity = client.exchangeCode("wx-code");

        assertEquals("openid-1", identity.openid());
        assertEquals("unionid-1", identity.unionid());
        assertEquals(2, identity.getClass().getRecordComponents().length);
        server.verify();
    }

    @Test
    void invalidWechatCodeIsARequestErrorNotAnAccountPasswordFailure() {
        WeChatMiniProgramProperties properties = properties();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo(startsWith(properties.getCode2SessionUrl().toString())))
                .andRespond(withSuccess("{\"errcode\":40029,\"errmsg\":\"invalid code\"}",
                        MediaType.APPLICATION_JSON));
        WeChatMiniProgramClient client = new WeChatMiniProgramClient(properties, builder, new ObjectMapper());

        WeChatAuthException exception = assertThrows(WeChatAuthException.class,
                () -> client.exchangeCode("bad-code"));

        assertEquals(400, exception.getCode());
        server.verify();
    }

    @Test
    void missingServerSecretDisablesWechatLoginSafely() {
        WeChatMiniProgramProperties properties = properties();
        properties.setAppSecret("");
        WeChatMiniProgramClient client = new WeChatMiniProgramClient(
                properties, RestClient.builder(), new ObjectMapper());

        WeChatAuthException exception = assertThrows(WeChatAuthException.class,
                () -> client.exchangeCode("wx-code"));

        assertEquals(503, exception.getCode());
    }

    private WeChatMiniProgramProperties properties() {
        WeChatMiniProgramProperties properties = new WeChatMiniProgramProperties();
        properties.setAppId("test-app");
        properties.setAppSecret("test-secret");
        properties.setCode2SessionUrl(URI.create("https://api.weixin.qq.com/sns/jscode2session"));
        return properties;
    }
}
