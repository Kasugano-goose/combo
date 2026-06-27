package com.example.combo.scene.domain;

import jakarta.websocket.Session;
import lombok.Data;

import java.util.concurrent.ScheduledFuture;

@Data
public class Scene {
    private Long sceneId;
    private PlayerPosition player1;
    private PlayerPosition player2;
    private int sceneWidth;
    private int sceneHeight;
    private int speed = 10;
    private volatile SceneStatus status;
    private volatile ScheduledFuture<?> gameLoopTask;

    // 存储双方的 Scene WebSocket Session（游戏循环直接用它发消息）
    private volatile Session player1Session;
    private volatile Session player2Session;

    public enum SceneStatus {
        ACTIVE,
        CLOSED
    }

    public PlayerPosition getPlayerPosition(Long playerId) {
        if (player1.getPlayerId().equals(playerId)) {
            return player1;
        } else if (player2.getPlayerId().equals(playerId)) {
            return player2;
        }
        return null;
    }

    public PlayerPosition getOpponentPosition(Long playerId) {
        if (player1.getPlayerId().equals(playerId)) {
            return player2;
        } else if (player2.getPlayerId().equals(playerId)) {
            return player1;
        }
        return null;
    }

    public Long getOpponentId(Long playerId) {
        PlayerPosition opponent = getOpponentPosition(playerId);
        return opponent != null ? opponent.getPlayerId() : null;
    }

    /**
     * 根据 playerId 设置对应的 Scene WS Session
     */
    public void setPlayerSession(Long playerId, Session session) {
        if (player1.getPlayerId().equals(playerId)) {
            this.player1Session = session;
        } else if (player2.getPlayerId().equals(playerId)) {
            this.player2Session = session;
        }
    }

    /**
     * 根据 playerId 获取对应的 Scene WS Session
     */
    public Session getPlayerSession(Long playerId) {
        if (player1.getPlayerId().equals(playerId)) {
            return player1Session;
        } else if (player2.getPlayerId().equals(playerId)) {
            return player2Session;
        }
        return null;
    }
}
