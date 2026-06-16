package com.example.combo.common.websocket;

import jakarta.websocket.*;

import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 好友通知 WebSocket 端点
 * 客户端连接地址: ws://localhost:8090/ws/friend/{playerId}
 *
 * 只有连接的用户才能收到自己的好友申请通知
 */
@Slf4j
@Component
@ServerEndpoint("/ws/friend/{playerId}")
public class FriendWebSocketEndpoint {

    // 注意: @ServerEndpoint 中不能直接使用 @Autowired
    // 需要通过静态方式注入
    private static WebSocketSessionManager sessionManager;

    @Autowired
    public void setSessionManager(WebSocketSessionManager manager) {
        FriendWebSocketEndpoint.sessionManager = manager;
    }

    /**
     * 连接建立时
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("playerId") Long playerId) {
        // 验证 playerId 有效性
        if (playerId == null || playerId <= 0) {
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "无效的玩家ID"));
            } catch (IOException e) {
                log.error("关闭连接失败", e);
            }
            return;
        }

        // 注册到会话管理器
        sessionManager.register(playerId, session);
        log.info("玩家 {} 已连接 WebSocket，当前在线: {}", playerId, sessionManager.getOnlineCount());
    }

    /**
     * 连接关闭时
     */
    @OnClose
    public void onClose(@PathParam("playerId") Long playerId) {
        sessionManager.unregister(playerId);
        log.info("玩家 {} 已断开 WebSocket，当前在线: {}", playerId, sessionManager.getOnlineCount());
    }

    /**
     * 收到客户端消息时（可选）
     */
    @OnMessage
    public void onMessage(String message, @PathParam("playerId") Long playerId) {
        log.info("收到玩家 {} 的消息: {}", playerId, message);
        // 可以处理客户端的心跳或确认消息
    }

    /**
     * 发生错误时
     */
    @OnError
    public void onError(Throwable error, @PathParam("playerId") Long playerId) {
        log.error("玩家 {} 的 WebSocket 发生错误: {}", playerId, error.getMessage());
        sessionManager.unregister(playerId);
    }
}
