package com.example.combo.common.websocket;

import jakarta.websocket.Session;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionManager {
    // 在线用户映射：playerId -> (endpointType -> Session)
    // 支持同一玩家同时连接多个 WebSocket 端点（如 chat、friend）
    private final ConcurrentHashMap<Long, Map<String, Session>> onlineSessions = new ConcurrentHashMap<>();

    /**
     * 注册会话
     * @param playerId 玩家ID
     * @param endpointType 端点类型（如 "chat", "friend", "scene"）
     * @param session WebSocket 会话
     */
    public void register(Long playerId, String endpointType, Session session) {
        onlineSessions.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .put(endpointType, session);
    }

    /**
     * 注销指定端点的会话
     * @param playerId 玩家ID
     * @param endpointType 端点类型
     */
    public void unregister(Long playerId, String endpointType) {
        Map<String, Session> sessions = onlineSessions.get(playerId);
        if (sessions != null) {
            sessions.remove(endpointType);
            if (sessions.isEmpty()) {
                onlineSessions.remove(playerId);
            }
        }
    }

    /**
     * 注销玩家的所有会话（兼容旧代码）
     * @param playerId 玩家ID
     */
    public void unregisterAll(Long playerId) {
        onlineSessions.remove(playerId);
    }

    /**
     * 判断用户是否在线（任意端点）
     * @param playerId 玩家ID
     */
    public boolean isOnline(Long playerId) {
        Map<String, Session> sessions = onlineSessions.get(playerId);
        return sessions != null && !sessions.isEmpty();
    }

    /**
     * 向指定用户发送消息（发送到所有端点）
     * @param playerId 玩家ID
     * @param message 消息内容
     */
    public boolean sendToUser(Long playerId, String message) {
        Map<String, Session> sessions = onlineSessions.get(playerId);
        if (sessions == null || sessions.isEmpty()) {
            return false;
        }

        boolean anySent = false;
        for (Map.Entry<String, Session> entry : sessions.entrySet()) {
            Session session = entry.getValue();
            if (session != null && session.isOpen()) {
                try {
                    session.getBasicRemote().sendText(message);
                    anySent = true;
                } catch (IOException e) {
                    // 发送失败，移除无效会话
                    sessions.remove(entry.getKey());
                }
            } else {
                // 会话已关闭，移除
                sessions.remove(entry.getKey());
            }
        }

        // 如果所有会话都已移除，清理玩家条目
        if (sessions.isEmpty()) {
            onlineSessions.remove(playerId);
        }

        return anySent;
    }

    /**
     * 向指定用户的特定端点发送消息
     * @param playerId 玩家ID
     * @param endpointType 端点类型
     * @param message 消息内容
     */
    public boolean sendToEndpoint(Long playerId, String endpointType, String message) {
        Map<String, Session> sessions = onlineSessions.get(playerId);
        if (sessions == null) {
            return false;
        }

        Session session = sessions.get(endpointType);
        if (session != null && session.isOpen()) {
            try {
                session.getBasicRemote().sendText(message);
                return true;
            } catch (IOException e) {
                sessions.remove(endpointType);
                if (sessions.isEmpty()) {
                    onlineSessions.remove(playerId);
                }
                return false;
            }
        }
        return false;
    }

    /**
     * 显示在线人数
     */
    public int getOnlineCount() {
        return onlineSessions.size();
    }
}
