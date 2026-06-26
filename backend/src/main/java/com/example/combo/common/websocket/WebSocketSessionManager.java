package com.example.combo.common.websocket;

import jakarta.websocket.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class WebSocketSessionManager {
    // 在线用户映射：playerId -> (endpointType -> Session)
    // 支持同一玩家同时连接多个 WebSocket 端点（如 chat、friend）
    private final ConcurrentHashMap<Long, Map<String, Session>> onlineSessions = new ConcurrentHashMap<>();

    /**
     * 注册会话（线程安全）
     *
     * 加锁保证 remove → put → close 的原子性：
     * 1. 先从 Map 中移除旧会话（此时旧会话已不在 Map 中）
     * 2. 放入新会话
     * 3. 关闭旧会话（即使触发 @OnClose → unregister，身份校验会跳过）
     *
     * 注意：synchronized 是可重入锁，若 close() 在同线程同步触发 @OnClose，
     * @OnClose 中的 unregister 可重入同一把锁，无死锁风险。
     */
    public void register(Long playerId, String endpointType, Session session) {
        Map<String, Session> sessions = onlineSessions.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
        synchronized (sessions) {
            // ① 先移除旧会话（已不在 Map 中）
            Session oldSession = sessions.remove(endpointType);
            // ② 放入新会话
            sessions.put(endpointType, session);
            // ③ 关闭旧会话（此时 Map 中已是新会话，旧会话的 onClose 不会影响它）
            if (oldSession != null && oldSession.isOpen()) {
                log.info("玩家 {} 端点 {} 存在旧会话，关闭并替换", playerId, endpointType);
                try {
                    oldSession.close();
                } catch (IOException e) {
                    log.warn("关闭旧会话异常: playerId={}, endpoint={}", playerId, endpointType, e);
                }
            }
        }
        log.info("玩家 {} 注册端点 {}，当前共 {} 个端点", playerId, endpointType, sessions.size());
    }

    /**
     * 注销指定端点的会话（线程安全 + 身份校验）
     * 仅当当前存储的 session 与传入的 session 是同一个对象时才移除，
     * 防止旧会话的 @OnClose 延迟触发时误删新注册的会话
     *
     * @param playerId 玩家ID
     * @param endpointType 端点类型
     * @param session 要注销的会话（可为 null，表示无条件注销）
     */
    public void unregister(Long playerId, String endpointType, Session session) {
        Map<String, Session> sessions = onlineSessions.get(playerId);
        if (sessions == null) {
            log.debug("玩家 {} 尝试注销端点 {}，但无会话记录", playerId, endpointType);
            return;
        }
        synchronized (sessions) {
            if (session != null) {
                // 身份校验：存储的 session 与传入的不是同一个对象，说明已被替换，跳过
                Session current = sessions.get(endpointType);
                if (current != session) {
                    log.debug("玩家 {} 端点 {} 已被新会话替换，跳过注销", playerId, endpointType);
                    return;
                }
            }
            sessions.remove(endpointType);
            if (sessions.isEmpty()) {
                onlineSessions.remove(playerId);
            }
        }
        log.info("玩家 {} 注销端点 {}，剩余端点: {}", playerId, endpointType, sessions.size());
    }

    /**
     * 注销指定端点的会话（无条件版本，兼容旧代码）
     */
    public void unregister(Long playerId, String endpointType) {
        unregister(playerId, endpointType, null);
    }

    /**
     * 注销玩家的所有会话（兼容旧代码）
     * @param playerId 玩家ID
     */
    public void unregisterAll(Long playerId) {
        Map<String, Session> removed = onlineSessions.remove(playerId);
        if (removed != null) {
            log.info("玩家 {} 所有会话已注销，共 {} 个端点", playerId, removed.size());
        }
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
     * 向指定用户发送消息（线程安全，发送到所有可用端点）
     * @param playerId 玩家ID
     * @param message 消息内容
     * @return true 表示至少一个端点发送成功
     */
    public boolean sendToUser(Long playerId, String message) {
        Map<String, Session> sessions = onlineSessions.get(playerId);
        if (sessions == null || sessions.isEmpty()) {
            log.debug("玩家 {} 无在线会话", playerId);
            return false;
        }

        boolean anySent = false;
        List<String> deadEndpoints = new ArrayList<>();

        synchronized (sessions) {
            for (Map.Entry<String, Session> entry : sessions.entrySet()) {
                Session session = entry.getValue();
                if (session == null || !session.isOpen()) {
                    log.warn("玩家 {} 端点 {} 会话无效: null={}, open={}",
                            playerId, entry.getKey(), session == null,
                            session != null && session.isOpen());
                    deadEndpoints.add(entry.getKey());
                    continue;
                }
                try {
                    // Ping 探测：检测 TCP 半开连接（isOpen() 返回 true 但实际已断开）
                    session.getBasicRemote().sendPing(ByteBuffer.wrap("ping".getBytes()));
                    session.getBasicRemote().sendText(message);
                    anySent = true;
                    log.info("消息已发送给玩家 {} 端点 {} (sessionId={})", playerId, entry.getKey(), session.getId());
                } catch (IOException e) {
                    log.error("发送给玩家 {} 端点 {} 失败 (Ping 或 sendText 异常): {}",
                            playerId, entry.getKey(), e.getMessage());
                    deadEndpoints.add(entry.getKey());
                }
            }

            // 统一清理无效端点
            for (String endpoint : deadEndpoints) {
                sessions.remove(endpoint);
            }
            if (sessions.isEmpty()) {
                onlineSessions.remove(playerId);
            }
        }

        if (!anySent) {
            log.warn("玩家 {} 所有端点发送失败，已清理 {} 个无效会话", playerId, deadEndpoints.size());
        }
        return anySent;
    }

    /**
     * 向指定用户的特定端点发送消息（线程安全）
     * @param playerId 玩家ID
     * @param endpointType 端点类型
     * @param message 消息内容
     */
    public boolean sendToEndpoint(Long playerId, String endpointType, String message) {
        Map<String, Session> sessions = onlineSessions.get(playerId);
        if (sessions == null) {
            return false;
        }

        synchronized (sessions) {
            Session session = sessions.get(endpointType);
            if (session != null && session.isOpen()) {
                try {
                    session.getBasicRemote().sendText(message);
                    return true;
                } catch (IOException e) {
                    log.warn("发送给玩家 {} 端点 {} 失败: {}", playerId, endpointType, e.getMessage());
                    sessions.remove(endpointType);
                    if (sessions.isEmpty()) {
                        onlineSessions.remove(playerId);
                    }
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * 获取指定玩家的会话状态（用于日志诊断）
     */
    public String getSessionStatus(Long playerId) {
        Map<String, Session> sessions = onlineSessions.get(playerId);
        if (sessions == null || sessions.isEmpty()) {
            return "无会话";
        }
        StringBuilder sb = new StringBuilder();
        synchronized (sessions) {
            for (Map.Entry<String, Session> entry : sessions.entrySet()) {
                Session s = entry.getValue();
                sb.append(entry.getKey())
                  .append("(open=").append(s != null && s.isOpen())
                  .append(") ");
            }
        }
        return sb.toString().trim();
    }

    /**
     * 显示在线人数
     */
    public int getOnlineCount() {
        return onlineSessions.size();
    }

    /**
     * 定时清理已关闭的僵死会话（每 15 秒执行一次）
     * 主动检测 isOpen()=false 的会话并移除，避免残留死连接
     */
    @Scheduled(fixedRate = 15000)
    public void cleanStaleSessions() {
        int cleaned = 0;
        for (Map.Entry<Long, Map<String, Session>> playerEntry : onlineSessions.entrySet()) {
            Map<String, Session> sessions = playerEntry.getValue();
            synchronized (sessions) {
                List<String> deadEndpoints = new ArrayList<>();
                for (Map.Entry<String, Session> entry : sessions.entrySet()) {
                    Session session = entry.getValue();
                    if (session == null || !session.isOpen()) {
                        deadEndpoints.add(entry.getKey());
                    }
                }
                for (String endpoint : deadEndpoints) {
                    sessions.remove(endpoint);
                    cleaned++;
                }
                if (sessions.isEmpty()) {
                    onlineSessions.remove(playerEntry.getKey());
                }
            }
        }
        if (cleaned > 0) {
            log.info("定时清理 {} 个僵死会话，当前在线: {}", cleaned, getOnlineCount());
        }
    }
}
