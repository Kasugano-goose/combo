package com.example.combo.common.websocket;

import jakarta.servlet.http.HttpSession;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.Session;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;

/**
 * WebSocket 握手配置器：将 HTTP 握手阶段的 HttpSession 暴露给 WS 端点，
 * 使端点能基于登录态（session 中的 playerId）进行鉴权。
 */
public class HttpSessionHandshakeConfigurator extends ServerEndpointConfig.Configurator {

    @Override
    public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
        HttpSession httpSession = (HttpSession) request.getHttpSession();
        // 未登录/无 HTTP 会话时 httpSession 为 null；ConcurrentHashMap 不允许 null 值，
        // 这里跳过存储即可，端点侧 getAuthorizedPlayerId 取不到值会判为未授权并关闭连接。
        if (httpSession != null) {
            sec.getUserProperties().put(HttpSession.class.getName(), httpSession);
        }
    }

    /**
     * 从 WS Session 中读取握手时登录用户的 playerId。
     * 未登录或无 HttpSession 时返回 null。
     */
    public static Long getAuthorizedPlayerId(Session session) {
        Object obj = session.getUserProperties().get(HttpSession.class.getName());
        if (obj instanceof HttpSession httpSession) {
            Object playerId = httpSession.getAttribute("playerId");
            if (playerId instanceof Long longId) {
                return longId;
            }
        }
        return null;
    }
}
