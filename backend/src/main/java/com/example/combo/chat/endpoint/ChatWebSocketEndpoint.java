package com.example.combo.chat.endpoint;

import com.example.combo.chat.service.ChatService;
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
@ServerEndpoint("/ws/chat/{playerId}")
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
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "无效的玩家ID"));
            } catch (IOException e) {
                log.error("关闭连接失败", e);
            }
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
        webSocketSessionManager.unregister(playerId, "chat", session);
    }
}
