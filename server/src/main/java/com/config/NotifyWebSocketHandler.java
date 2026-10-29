package com.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 站内消息实时推送 WebSocket 处理器。
 * 连接按「type:uid」分组（type：1学生 2教师）；该键由 {@link WebSocketAuthInterceptor}
 * 在握手阶段依据服务端会话写入，客户端携带的 query 参数不参与订阅。
 * 当有新站内消息/通知产生时，向对应用户的所有在线连接推送事件。
 */
@Component
public class NotifyWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** "type:uid" -> 该用户当前在线的 WebSocket 连接集合 */
    private final Map<String, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String key = parseKey(session);
        if (key != null) {
            userSessions.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(session);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String key = parseKey(session);
        if (key != null) {
            Set<WebSocketSession> set = userSessions.get(key);
            if (set != null) {
                set.remove(session);
                if (set.isEmpty()) {
                    userSessions.remove(key);
                }
            }
        }
    }

    /**
     * 向指定用户推送新消息事件。
     *
     * @param receivertype 接收者类型：1学生 2教师
     * @param receiverid   接收者ID
     * @param title        消息标题
     * @param content      消息内容
     */
    public void pushMessage(Integer receivertype, Integer receiverid, String title, String content) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "message");
        payload.put("title", title);
        payload.put("content", content);
        push(receivertype, receiverid, payload);
    }

    /**
     * 向指定用户推送自定义事件载荷。
     */
    public void push(Integer receivertype, Integer receiverid, Map<String, Object> payload) {
        if (receivertype == null || receiverid == null) return;
        Set<WebSocketSession> set = userSessions.get(receivertype + ":" + receiverid);
        if (set == null || set.isEmpty()) return;
        try {
            TextMessage message = new TextMessage(objectMapper.writeValueAsString(payload));
            for (WebSocketSession ws : set) {
                try {
                    if (ws.isOpen()) {
                        synchronized (ws) {
                            ws.sendMessage(message);
                        }
                    }
                } catch (Exception ignored) {
                    // 单连接发送失败不影响其它连接
                }
            }
        } catch (Exception ignored) {
            // 推送失败不影响主业务
        }
    }

    /**
     * 订阅键取自握手阶段由服务端写入的会话属性，不再解析客户端 query。
     *
     * @see WebSocketAuthInterceptor#beforeHandshake
     */
    private String parseKey(WebSocketSession session) {
        Object key = session.getAttributes().get(WebSocketAuthInterceptor.ATTR_NOTIFY_KEY);
        return key == null ? null : String.valueOf(key);
    }
}
