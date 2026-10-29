package com.config;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手鉴权。
 *
 * <p>订阅标识（站内消息的 {@code type:uid}、签到的 {@code classid}）一律由
 * <b>服务端会话</b>推导后写入 WebSocket 会话属性；客户端 query 参数不再被信任。</p>
 *
 * <p>此前订阅标识完全取自 query，任意已登录用户连接
 * {@code /ws/notify?type=2&uid=<他人ID>} 即可实时收到他人的私信标题与内容，
 * 连接 {@code /ws/checkin?classid=<他人班级>} 即可旁听他人班级的签到事件。</p>
 */
@Component
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    /** WebSocket 会话属性：站内消息订阅键，形如 "1:123"（type:uid，type 1学生 2教师） */
    public static final String ATTR_NOTIFY_KEY = "notifyKey";

    /** WebSocket 会话属性：签到订阅的班级 ID */
    public static final String ATTR_CHECKIN_CLASSID = "checkinClassid";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        HttpSession session = resolveSession(request);
        if (session == null) {
            // 未登录不放行，交由前端重连逻辑处理
            return false;
        }

        Object role = session.getAttribute("role");
        if ("student".equals(role)) {
            Object studentid = session.getAttribute("studentid");
            if (studentid != null) {
                attributes.put(ATTR_NOTIFY_KEY, "1:" + studentid);
            }
            Object classid = session.getAttribute("classid");
            if (classid != null) {
                attributes.put(ATTR_CHECKIN_CLASSID, classid);
            }
        } else if ("teacher".equals(role)) {
            Object teacherid = session.getAttribute("teacherid");
            if (teacherid != null) {
                attributes.put(ATTR_NOTIFY_KEY, "2:" + teacherid);
            }
        }
        // 管理员当前不接收站内消息、也不参与签到，无需订阅键
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需处理
    }

    private HttpSession resolveSession(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            return servletRequest.getServletRequest().getSession(false);
        }
        return null;
    }
}
