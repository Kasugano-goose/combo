package com.example.combo.chat.endpoint;

import com.example.combo.chat.service.ChatService;
import com.example.combo.common.websocket.HttpSessionHandshakeConfigurator;
import com.example.combo.common.websocket.WebSocketSessionManager;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@ServerEndpoint(value = "/ws/chat/{playerId}", configurator = HttpSessionHandshakeConfigurator.class)
public class ChatWebSocketEndpoint {

    private static ChatService chatService;
    private static WebSocketSessionManager webSocketSessionManager;

    @Autowired
    public void setChatService(ChatService service) {
        ChatWebSocketEndpoint.chatService = service;
    }

    @Autowired
    public void setWebSocketSessionManager(WebSocketSessionManager manager) {
        ChatWebSocketEndpoint.webSocketSessionManager = manager;
    }

    @OnOpen
    public void onOpen(Session session, @PathParam("playerId") Long playerId) {
        if (playerId == null || playerId <= 0) {
            closeQuietly(session, CloseReason.CloseCodes.VIOLATED_POLICY, "无效的玩家ID");
            return;
        }
        // 鉴权：连接的 playerId 必须与登录态一致，防止冒充他人收发消息
        Long authId = HttpSessionHandshakeConfigurator.getAuthorizedPlayerId(session);
        if (authId == null) {
            closeQuietly(session, CloseReason.CloseCodes.VIOLATED_POLICY, "请先登录");
            return;
        }
        if (!authId.equals(playerId)) {
            closeQuietly(session, CloseReason.CloseCodes.VIOLATED_POLICY, "无权访问他人通道");
            return;
        }
        webSocketSessionManager.register(playerId, "chat", session);
        log.info("玩家 {} 已连接聊天WebSocket，当前在线: {}", playerId, webSocketSessionManager.getOnlineCount());
    }

    @OnMessage
    public void onMessage(String message, @PathParam("playerId") Long playerId) {
        log.info("收到玩家 {} 的聊天消息: {}", playerId, message);
        chatService.handleMessage(playerId, message);
    }

    @OnClose
    public void onClose(Session session, @PathParam("playerId") Long playerId) {
        webSocketSessionManager.unregister(playerId, "chat", session);
        log.info("玩家 {} 已断开聊天WebSocket，当前在线: {}", playerId, webSocketSessionManager.getOnlineCount());
    }

    @OnError
    public void onError(Session session, @PathParam("playerId") Long playerId, Throwable error) {
        log.error("玩家 {} 的聊天WebSocket发生错误: {}", playerId, error.getMessage());
        // @OnClose 会在 @OnError 之后自动调用，无需重复 unregister
    }

    private void closeQuietly(Session session, CloseReason.CloseCodes code, String reason) {
        try {
            session.close(new CloseReason(code, reason));
        } catch (IOException e) {
            log.error("关闭连接失败", e);
        }
    }
}
