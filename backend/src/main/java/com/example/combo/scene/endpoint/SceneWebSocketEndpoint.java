package com.example.combo.scene.endpoint;

import com.example.combo.scene.domain.Scene;
import com.example.combo.scene.service.SceneService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Component
@ServerEndpoint("/ws/scene/{sceneId}/{playerId}")
public class SceneWebSocketEndpoint {
    private static SceneService sceneService;
    private static ObjectMapper objectMapper;

    @Autowired
    public void setSceneService(SceneService service) {
        SceneWebSocketEndpoint.sceneService = service;
    }

    @Autowired
    public void setObjectMapper(ObjectMapper mapper) {
        SceneWebSocketEndpoint.objectMapper = mapper;
    }

    @OnOpen
    public void onOpen(Session session, @PathParam("sceneId") Long sceneId, @PathParam("playerId") Long playerId) {
        if (playerId == null || playerId <= 0) {
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "无效的玩家ID"));
            } catch (IOException e) {
                log.error("关闭连接失败", e);
            }
            return;
        }

        // 将 session 存入 Scene 对象（游戏循环直接用它发消息）
        Scene scene = sceneService.getSceneByPlayerId(playerId);
        if (scene == null) {
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "你不在任何场景中"));
            } catch (IOException e) {
                log.error("关闭连接失败", e);
            }
            return;
        }

        scene.setPlayerSession(playerId, session);
        log.info("玩家 {} 连接场景 {} WebSocket，session 已存储", playerId, sceneId);
    }

    @OnMessage
    public void onMessage(String message, @PathParam("playerId") Long playerId) {
        log.debug("收到玩家 {} 的场景指令: {}", playerId, message);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(message, Map.class);
            String type = (String) data.get("type");

            if (type == null) return;

            switch (type) {
                case "MOVE" -> {
                    String direction = (String) data.get("direction");
                    if (direction != null && isValidDirection(direction)) {
                        sceneService.handleMove(playerId, direction);
                    }
                }
                case "STOP" -> sceneService.handleStop(playerId);
                case "EXIT" -> sceneService.handleExit(playerId);
                default -> log.warn("未知的场景指令类型: {}", type);
            }
        } catch (Exception e) {
            log.error("解析场景指令失败: {}", message, e);
        }
    }

    @OnClose
    public void onClose(@PathParam("playerId") Long playerId) {
        log.info("玩家 {} 断开场景 WebSocket", playerId);
        sceneService.handleDisconnect(playerId);
    }

    @OnError
    public void onError(Session session, @PathParam("playerId") Long playerId, Throwable error) {
        log.error("玩家 {} 的场景WebSocket发生错误: {}", playerId, error.getMessage());
        sceneService.handleDisconnect(playerId);
    }

    private boolean isValidDirection(String direction) {
        return "UP".equals(direction) || "DOWN".equals(direction)
                || "LEFT".equals(direction) || "RIGHT".equals(direction);
    }
}
