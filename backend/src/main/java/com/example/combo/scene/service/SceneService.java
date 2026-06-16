package com.example.combo.scene.service;

import com.example.combo.common.websocket.WebSocketSessionManager;
import com.example.combo.player.domain.Player;
import com.example.combo.scene.domain.PlayerPosition;
import com.example.combo.scene.domain.Scene;
import com.example.combo.scene.domain.Scene.SceneStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import jakarta.websocket.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SceneService {
    private final WebSocketSessionManager webSocketSessionManager;
    private final ObjectMapper objectMapper;

    // 场景存储：sceneId → Scene
    private final ConcurrentHashMap<Long, Scene> scenes = new ConcurrentHashMap<>();
    // 反向索引：playerId → sceneId（快速查找玩家所在场景）
    private final ConcurrentHashMap<Long, Long> playerSceneMap = new ConcurrentHashMap<>();
    // 场景ID自增
    private long sceneIdCounter = 0;

    // 游戏循环线程池
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    // 场景配置
    private static final int DEFAULT_SCENE_WIDTH = 800;
    private static final int DEFAULT_SCENE_HEIGHT = 600;
    private static final int DEFAULT_SPEED = 10;
    private static final int GAME_LOOP_INTERVAL_MS = 100;   // 每100ms一帧

    /**
     * 创建场景（匹配成功后调用）
     */
    public Scene createScene(Player player1, Player player2) {
        // 检查玩家是否已在其他场景中
        if (playerSceneMap.containsKey(player1.getId())) {
            throw new IllegalArgumentException("玩家 " + player1.getId() + " 已在其他场景中");
        }
        if (playerSceneMap.containsKey(player2.getId())) {
            throw new IllegalArgumentException("玩家 " + player2.getId() + " 已在其他场景中");
        }

        Long sceneId = ++sceneIdCounter;

        // 创建双方初始位置（左右两侧）
        PlayerPosition pos1 = PlayerPosition.builder()
                .playerId(player1.getId())
                .playerName(player1.getUsername())
                .roleId(player1.getSelectedRoleId())
                .x(100)
                .y(DEFAULT_SCENE_HEIGHT / 2)
                .direction("STOP")
                .build();

        PlayerPosition pos2 = PlayerPosition.builder()
                .playerId(player2.getId())
                .playerName(player2.getUsername())
                .roleId(player2.getSelectedRoleId())
                .x(DEFAULT_SCENE_WIDTH - 100)
                .y(DEFAULT_SCENE_HEIGHT / 2)
                .direction("STOP")
                .build();

        // 创建场景
        Scene scene = new Scene();
        scene.setSceneId(sceneId);
        scene.setPlayer1(pos1);
        scene.setPlayer2(pos2);
        scene.setSceneWidth(DEFAULT_SCENE_WIDTH);
        scene.setSceneHeight(DEFAULT_SCENE_HEIGHT);
        scene.setSpeed(DEFAULT_SPEED);
        scene.setStatus(SceneStatus.ACTIVE);

        // 存储场景
        scenes.put(sceneId, scene);
        playerSceneMap.put(player1.getId(), sceneId);
        playerSceneMap.put(player2.getId(), sceneId);

        // 启动游戏循环
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(
                () -> gameLoop(sceneId),
                GAME_LOOP_INTERVAL_MS,
                GAME_LOOP_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
        scene.setGameLoopTask(task);

        log.info("场景 {} 已创建，玩家 {} 与玩家 {}", sceneId, player1.getId(), player2.getId());

        // 通知双方场景开始
        notifySceneStart(scene);

        return scene;
    }

    /**
     * 处理移动指令
     */
    public void handleMove(Long playerId, String direction) {
        Long sceneId = playerSceneMap.get(playerId);
        if (sceneId == null) {
            return;
        }
        Scene scene = scenes.get(sceneId);
        if (scene == null || scene.getStatus() != SceneStatus.ACTIVE) {
            return;
        }

        PlayerPosition pos = scene.getPlayerPosition(playerId);
        if (pos != null) {
            pos.setDirection(direction);
            log.debug("玩家 {} 方向设为 {}", playerId, direction);
        }
    }

    /**
     * 处理停止指令
     */
    public void handleStop(Long playerId) {
        Long sceneId = playerSceneMap.get(playerId);
        if (sceneId == null) {
            return;
        }
        Scene scene = scenes.get(sceneId);
        if (scene == null || scene.getStatus() != SceneStatus.ACTIVE) {
            return;
        }

        PlayerPosition pos = scene.getPlayerPosition(playerId);
        if (pos != null) {
            pos.setDirection("STOP");
            log.debug("玩家 {} 停止移动", playerId);
        }
    }

    /**
     * 处理退出指令
     */
    public void handleExit(Long playerId) {
        Long sceneId = playerSceneMap.get(playerId);
        if (sceneId == null) {
            return;
        }
        Scene scene = scenes.get(sceneId);
        if (scene == null) {
            return;
        }

        Long opponentId = scene.getOpponentId(playerId);
        log.info("玩家 {} 主动退出场景 {}", playerId, sceneId);

        // 先通知对手（场景销毁前 session 还可用）
        if (opponentId != null) {
            notifySceneEnd(scene, opponentId, "对方已退出场景");
        }

        // 销毁场景
        destroyScene(sceneId, "玩家 " + playerId + " 已退出场景");
    }

    /**
     * 处理玩家断开连接（WebSocket @OnClose）
     */
    public void handleDisconnect(Long playerId) {
        Long sceneId = playerSceneMap.get(playerId);
        if (sceneId == null) {
            return;
        }
        Scene scene = scenes.get(sceneId);
        if (scene == null) {
            return;
        }

        Long opponentId = scene.getOpponentId(playerId);
        log.info("玩家 {} 断开连接，场景 {} 销毁", playerId, sceneId);

        // 先通知对手
        if (opponentId != null) {
            notifySceneEnd(scene, opponentId, "对方已断开连接，场景结束");
        }

        // 销毁场景
        destroyScene(sceneId, "玩家 " + playerId + " 断开连接");
    }

    /**
     * 游戏循环：每帧执行一次
     */
    private void gameLoop(Long sceneId) {
        Scene scene = scenes.get(sceneId);
        if (scene == null || scene.getStatus() != SceneStatus.ACTIVE) {
            return;
        }

        try {
            // 1. 根据方向计算新位置
            updatePosition(scene, scene.getPlayer1());
            updatePosition(scene, scene.getPlayer2());

            // 2. 边界校验
            clampPosition(scene, scene.getPlayer1());
            clampPosition(scene, scene.getPlayer2());

            // 3. 广播最新状态给双方
            broadcastState(scene);
        } catch (Exception e) {
            log.error("场景 {} 游戏循环异常", sceneId, e);
        }
    }

    /**
     * 根据方向更新位置
     */
    private void updatePosition(Scene scene, PlayerPosition pos) {
        String dir = pos.getDirection();
        int speed = scene.getSpeed();

        switch (dir) {
            case "UP" -> pos.setY(pos.getY() - speed);
            case "DOWN" -> pos.setY(pos.getY() + speed);
            case "LEFT" -> pos.setX(pos.getX() - speed);
            case "RIGHT" -> pos.setX(pos.getX() + speed);
            case "STOP" -> {} // 不移动
        }
    }

    /**
     * 边界校验：不能超出场景范围
     */
    private void clampPosition(Scene scene, PlayerPosition pos) {
        if (pos.getX() < 0) pos.setX(0);
        if (pos.getX() > scene.getSceneWidth()) pos.setX(scene.getSceneWidth());
        if (pos.getY() < 0) pos.setY(0);
        if (pos.getY() > scene.getSceneHeight()) pos.setY(scene.getSceneHeight());
    }

    /**
     * 广播场景状态给双方（通过 Scene WS Session 直接发送）
     */
    private void broadcastState(Scene scene) {
        try {
            Map<String, Object> state = new HashMap<>();
            state.put("type", "STATE");
            state.put("sceneId", scene.getSceneId());
            state.put("player1", buildPositionMap(scene.getPlayer1()));
            state.put("player2", buildPositionMap(scene.getPlayer2()));
            state.put("timestamp", LocalDateTime.now().toString());

            String json = objectMapper.writeValueAsString(state);
            sendToSceneSession(scene, scene.getPlayer1().getPlayerId(), json);
            sendToSceneSession(scene, scene.getPlayer2().getPlayerId(), json);
        } catch (Exception e) {
            log.error("广播场景状态失败: sceneId={}", scene.getSceneId(), e);
        }
    }

    /**
     * 构建玩家位置信息Map
     */
    private Map<String, Object> buildPositionMap(PlayerPosition pos) {
        Map<String, Object> map = new HashMap<>();
        map.put("playerId", pos.getPlayerId());
        map.put("playerName", pos.getPlayerName());
        map.put("roleId", pos.getRoleId());
        map.put("x", pos.getX());
        map.put("y", pos.getY());
        map.put("direction", pos.getDirection());
        return map;
    }

    /**
     * 通知双方场景开始
     */
    private void notifySceneStart(Scene scene) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "SCENE_START");
            notification.put("sceneId", scene.getSceneId());
            notification.put("sceneWidth", scene.getSceneWidth());
            notification.put("sceneHeight", scene.getSceneHeight());
            notification.put("player1", buildPositionMap(scene.getPlayer1()));
            notification.put("player2", buildPositionMap(scene.getPlayer2()));
            notification.put("timestamp", LocalDateTime.now().toString());

            String json = objectMapper.writeValueAsString(notification);
            webSocketSessionManager.sendToUser(scene.getPlayer1().getPlayerId(), json);
            webSocketSessionManager.sendToUser(scene.getPlayer2().getPlayerId(), json);
        } catch (Exception e) {
            log.error("发送场景开始通知失败", e);
        }
    }

    /**
     * 通知玩家场景结束（通过 Scene WS Session 发送）
     */
    private void notifySceneEnd(Scene scene, Long playerId, String reason) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "SCENE_END");
            notification.put("reason", reason);
            notification.put("timestamp", LocalDateTime.now().toString());

            String json = objectMapper.writeValueAsString(notification);
            sendToSceneSession(scene, playerId, json);
        } catch (Exception e) {
            log.error("发送场景结束通知失败: playerId={}", playerId, e);
        }
    }

    /**
     * 通过 Scene WS Session 发送消息
     */
    private void sendToSceneSession(Scene scene, Long playerId, String message) {
        Session session = scene.getPlayerSession(playerId);
        if (session != null && session.isOpen()) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (Exception e) {
                log.error("Scene WS 发送失败: playerId={}", playerId, e);
            }
        } else {
            log.warn("玩家 {} 的 Scene WS Session 不可用", playerId);
        }
    }

    /**
     * 根据 playerId 获取所在场景
     */
    public Scene getSceneByPlayerId(Long playerId) {
        Long sceneId = playerSceneMap.get(playerId);
        if (sceneId == null) return null;
        return scenes.get(sceneId);
    }

    /**
     * 销毁场景
     */
    private void destroyScene(Long sceneId, String reason) {
        Scene scene = scenes.remove(sceneId);
        if (scene == null) {
            return;
        }

        // 停止游戏循环
        if (scene.getGameLoopTask() != null) {
            scene.getGameLoopTask().cancel(false);
        }

        // 移除反向索引
        playerSceneMap.remove(scene.getPlayer1().getPlayerId());
        playerSceneMap.remove(scene.getPlayer2().getPlayerId());

        scene.setStatus(SceneStatus.CLOSED);
        log.info("场景 {} 已销毁: {}", sceneId, reason);
    }

    /**
     * 查询玩家是否在场景中
     */
    public boolean isInScene(Long playerId) {
        return playerSceneMap.containsKey(playerId);
    }

    /**
     * 获取当前活跃场景数
     */
    public int getActiveSceneCount() {
        return scenes.size();
    }

    /**
     * 应用关闭时清理所有场景
     */
    @PreDestroy
    public void shutdown() {
        log.info("关闭场景服务，清理所有场景...");
        for (Long sceneId : scenes.keySet()) {
            destroyScene(sceneId, "服务关闭");
        }
        scheduler.shutdown();
    }
}
