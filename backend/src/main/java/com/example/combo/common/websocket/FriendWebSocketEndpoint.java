package com.example.combo.common.websocket;

import com.example.combo.match.service.MatchService;
import jakarta.websocket.*;

import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
    private static MatchService matchService;

    @Autowired
    public void setSessionManager(WebSocketSessionManager manager) {
        FriendWebSocketEndpoint.sessionManager = manager;
    }

    @Autowired
    public void setMatchService(MatchService service) {
        FriendWebSocketEndpoint.matchService = service;
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
        sessionManager.register(playerId, "friend", session);
        log.info("玩家 {} 已连接好友 WebSocket，当前在线: {}", playerId, sessionManager.getOnlineCount());

        // 异步拉取未送达的待补偿通知
        CompletableFuture.runAsync(() -> deliverPendingNotifications(playerId, session));
    }

    /**
     * 连接关闭时
     * 传入 session 对象，确保只注销自己，不误删新注册的会话
     */
    @OnClose
    public void onClose(Session session, @PathParam("playerId") Long playerId) {
        sessionManager.unregister(playerId, "friend", session);
        log.info("玩家 {} 已断开好友 WebSocket，当前在线: {}", playerId, sessionManager.getOnlineCount());
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
     * 传入 session 对象，确保只注销自己，不误删新注册的会话
     */
    @OnError
    public void onError(Session session, @PathParam("playerId") Long playerId, Throwable error) {
        log.error("玩家 {} 的好友 WebSocket 发生错误: {}", playerId, error.getMessage());
        sessionManager.unregister(playerId, "friend", session);
    }

    /**
     * 拉取并发送未送达的待补偿通知
     */
    private void deliverPendingNotifications(Long playerId, Session session) {
        try {
            List<String> pending = matchService.pullPendingNotifications(playerId);
            if (pending.isEmpty()) return;

            log.info("玩家 {} 重连，推送 {} 条待补偿通知", playerId, pending.size());
            for (String json : pending) {
                if (session.isOpen()) {
                    try {
                        session.getBasicRemote().sendText(json);
                    } catch (IOException e) {
                        log.error("推送待补偿通知失败: playerId={}", playerId, e);
                        break;
                    }
                } else {
                    log.warn("玩家 {} 会话已关闭，停止推送待补偿通知", playerId);
                    break;
                }
            }
        } catch (Exception e) {
            log.error("拉取待补偿通知异常: playerId={}", playerId, e);
        }
    }
}
