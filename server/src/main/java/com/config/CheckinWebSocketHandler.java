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
 * 签到实时推送 WebSocket 处理器。
 * 客户端连接时携带 ?classid=xxx，按班级分组；
 * 签到发起 / 签到成功 / 代签 / 结束 时，向对应班级的所有在线连接广播事件。
 */
@Component
public class CheckinWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** classid -> 该班级当前在线的 WebSocket 连接集合 */
    private final Map<Integer, Set<WebSocketSession>> classSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Integer classid = parseClassid(session);
        if (classid != null) {
            classSessions.computeIfAbsent(classid, k -> ConcurrentHashMap.newKeySet()).add(session);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Integer classid = parseClassid(session);
        if (classid != null) {
            Set<WebSocketSession> set = classSessions.get(classid);
            if (set != null) {
                set.remove(session);
                if (set.isEmpty()) {
                    classSessions.remove(classid);
                }
            }
        }
    }

    /**
     * 向某班级所有在线连接广播签到事件。
     *
     * @param classid    班级ID
     * @param type       事件类型：session_created / checkin / closed
     * @param sessionId  签到会话ID
     * @param coursename 课程名（用于学生端提醒文案）
     */
    public void broadcast(Integer classid, String type, Integer sessionId, String coursename) {
        if (classid == null) return;
        Set<WebSocketSession> set = classSessions.get(classid);
        if (set == null || set.isEmpty()) return;

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", type);
            payload.put("classid", classid);
            payload.put("sessionId", sessionId);
            payload.put("coursename", coursename);
            String json = objectMapper.writeValueAsString(payload);
            TextMessage message = new TextMessage(json);

            for (WebSocketSession ws : set) {
                try {
                    if (ws.isOpen()) {
                        synchronized (ws) {
                            ws.sendMessage(message);
                        }
                    }
                } catch (Exception ignored) {
                    // 单个连接发送失败不影响其它连接
                }
            }
        } catch (Exception ignored) {
            // 广播失败不影响主业务
        }
    }

    /**
     * 班级 ID 取自握手阶段由服务端写入的会话属性，不再解析客户端 query。
     *
     * @see WebSocketAuthInterceptor#beforeHandshake
     */
    private Integer parseClassid(WebSocketSession session) {
        Object classid = session.getAttributes().get(WebSocketAuthInterceptor.ATTR_CHECKIN_CLASSID);
        return classid instanceof Integer value ? value : null;
    }
}
