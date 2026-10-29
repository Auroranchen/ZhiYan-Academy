package com.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置：注册签到实时推送端点 /ws/checkin 与站内消息推送端点 /ws/notify。
 *
 * <p>两个端点都挂 {@link WebSocketAuthInterceptor}：订阅标识由服务端会话推导，
 * 客户端 query 参数不参与订阅，避免任意登录用户订阅他人消息/班级。</p>
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private CheckinWebSocketHandler checkinWebSocketHandler;

    @Autowired
    private NotifyWebSocketHandler notifyWebSocketHandler;

    @Autowired
    private WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(checkinWebSocketHandler, "/ws/checkin")
                .addInterceptors(webSocketAuthInterceptor)
                .setAllowedOriginPatterns("*");
        registry.addHandler(notifyWebSocketHandler, "/ws/notify")
                .addInterceptors(webSocketAuthInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
