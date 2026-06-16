package com.example.combo.match.service;

import com.example.combo.common.websocket.WebSocketSessionManager;
import com.example.combo.player.domain.Player;
import com.example.combo.scene.domain.Scene;
import com.example.combo.scene.service.SceneService;
import com.example.combo.player.repository.PlayerRepository;
import com.example.combo.player.service.PlayerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {
    private final PlayerRepository playerRepository;
    private final PlayerService playerService;
    private final StringRedisTemplate redisTemplate;
    private final WebSocketSessionManager webSocketSessionManager;
    private final ObjectMapper objectMapper;
    private final SceneService sceneService;

    private static final String MATCH_POOL_KEY = "match:pool";
    private static final String MATCH_PLAYER_PREFIX = "match:player:";
    private static final long MATCH_TIMEOUT_MS = 60000L;

    private final ConcurrentHashMap<Long, Long> waitingPlayers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    // ===== 新增：匹配确认机制 =====
    // 匹配对：key=任意一方playerId, value=MatchPair（包含双方ID和确认状态）
    private final ConcurrentHashMap<Long, MatchPair> matchPairs = new ConcurrentHashMap<>();

    /**
     * 匹配对数据结构
     */
    public static class MatchPair {
        public Long player1Id;
        public Long player2Id;
        public volatile boolean player1Confirmed = false;
        public volatile boolean player2Confirmed = false;

        public MatchPair(Long p1, Long p2) {
            this.player1Id = p1;
            this.player2Id = p2;
        }

        public Long getOpponentId(Long playerId) {
            return player1Id.equals(playerId) ? player2Id : player1Id;
        }

        public boolean isConfirmed(Long playerId) {
            return player1Id.equals(playerId) ? player1Confirmed : player2Confirmed;
        }

        public void confirm(Long playerId) {
            if (player1Id.equals(playerId)) player1Confirmed = true;
            else player2Confirmed = true;
        }

        public boolean isBothConfirmed() {
            return player1Confirmed && player2Confirmed;
        }
    }

    /**
     * 加入匹配池（synchronized 防止并发竞态：多人同时匹配时保证原子性）
     */
    public synchronized void joinMatch(Long playerId) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("玩家不存在"));

        if (player.getStatus() != Player.PlayerStatus.NORMAL) {
            throw new IllegalArgumentException("玩家状态异常，无法匹配");
        }

        Double score = redisTemplate.opsForZSet().score(MATCH_POOL_KEY, playerId.toString());
        if (score != null) {
            throw new IllegalArgumentException("你已在匹配池中，请耐心等待");
        }

        long now = System.currentTimeMillis();
        redisTemplate.opsForZSet().add(MATCH_POOL_KEY, playerId.toString(), (double) now);

        String playerKey = MATCH_PLAYER_PREFIX + playerId;
        Map<String, String> playerInfo = new HashMap<>();
        playerInfo.put("username", player.getUsername());
        playerInfo.put("rankScore", String.valueOf(player.getRankScore()));
        playerInfo.put("rankLevel", String.valueOf(player.getCurrentRank().getLevel()));
        playerInfo.put("selectedRoleId", String.valueOf(player.getSelectedRoleId()));
        redisTemplate.opsForHash().putAll(playerKey, playerInfo);

        waitingPlayers.put(playerId, now);
        log.info("玩家 {} 加入匹配池，当前匹配池人数: {}", playerId, getWaitingCount());

        tryMatch(player);
    }

    /**
     * 退出匹配池
     */
    public void leaveMatch(Long playerId) {
        redisTemplate.opsForZSet().remove(MATCH_POOL_KEY, playerId.toString());
        redisTemplate.delete(MATCH_PLAYER_PREFIX + playerId);
        waitingPlayers.remove(playerId);
        log.info("玩家 {} 退出匹配池，当前匹配池人数: {}", playerId, getWaitingCount());
    }

    /**
     * 尝试匹配
     */
    private void tryMatch(Player player) {
        Set<String> allMembers = redisTemplate.opsForZSet().reverseRange(MATCH_POOL_KEY, 0, -1);
        if (allMembers == null || allMembers.isEmpty()) return;

        for (String memberId : allMembers) {
            Long opponentId = Long.parseLong(memberId);
            if (opponentId.equals(player.getId())) continue;

            Player opponent = playerRepository.findById(opponentId).orElse(null);
            if (opponent == null || opponent.getStatus() != Player.PlayerStatus.NORMAL) {
                removePlayerFromPool(opponentId);
                continue;
            }

            if (playerService.canMatchWith(player, opponent)) {
                // 匹配成功，双方移出匹配池
                removePlayerFromPool(player.getId());
                removePlayerFromPool(opponentId);

                log.info("匹配成功！玩家 {} 与玩家 {}", player.getId(), opponentId);

                // 创建匹配对（不创建场景，等双方确认）
                MatchPair pair = new MatchPair(player.getId(), opponentId);
                matchPairs.put(player.getId(), pair);
                matchPairs.put(opponentId, pair);

                // 通知双方匹配成功（无 sceneId）
                notifyMatchFound(player, opponent);
                notifyMatchFound(opponent, player);
                return;
            }
        }

        log.info("玩家 {} 暂未找到兼容对手，等待中...", player.getId());
    }

    /**
     * 玩家确认进入场景（synchronized 防止两人同时确认时竞态）
     */
    public synchronized void confirmMatch(Long playerId) {
        MatchPair pair = matchPairs.get(playerId);
        if (pair == null) {
            throw new IllegalArgumentException("没有待确认的匹配");
        }

        if (pair.isConfirmed(playerId)) {
            throw new IllegalArgumentException("你已确认，等待对方确认");
        }

        pair.confirm(playerId);
        log.info("玩家 {} 已确认进入场景", playerId);

        // 通知对手：对方已确认
        Long opponentId = pair.getOpponentId(playerId);
        notifyOpponentConfirmed(opponentId);

        // 检查是否双方都确认了
        if (pair.isBothConfirmed()) {
            log.info("双方都已确认，创建场景 {} 和 {}", pair.player1Id, pair.player2Id);

            // 清理匹配对
            matchPairs.remove(pair.player1Id);
            matchPairs.remove(pair.player2Id);

            // 查找玩家信息
            Player player1 = playerRepository.findById(pair.player1Id).orElse(null);
            Player player2 = playerRepository.findById(pair.player2Id).orElse(null);

            if (player1 == null || player2 == null) {
                log.error("创建场景失败：玩家不存在");
                return;
            }

            // 创建场景
            Scene scene = sceneService.createScene(player1, player2);

            // 通知双方进入场景
            notifySceneReady(pair.player1Id, scene.getSceneId());
            notifySceneReady(pair.player2Id, scene.getSceneId());
        }
    }

    /**
     * 从匹配池中移除玩家
     */
    private void removePlayerFromPool(Long playerId) {
        redisTemplate.opsForZSet().remove(MATCH_POOL_KEY, playerId.toString());
        redisTemplate.delete(MATCH_PLAYER_PREFIX + playerId);
        waitingPlayers.remove(playerId);
    }

    // ===== 通知方法 =====

    /**
     * 通知匹配成功（无 sceneId，等待确认）
     */
    private void notifyMatchFound(Player player, Player opponent) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "MATCHED");
        notification.put("opponentId", opponent.getId());
        notification.put("opponentName", opponent.getUsername());
        notification.put("opponentScore", opponent.getRankScore() != null ? opponent.getRankScore() : 0);
        notification.put("yourScore", player.getRankScore() != null ? player.getRankScore() : 0);
        notification.put("timestamp", LocalDateTime.now().toString());
        sendWithRetry(player.getId(), notification);
    }

    /**
     * 通知对手已确认
     */
    private void notifyOpponentConfirmed(Long opponentId) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "OPPONENT_CONFIRMED");
        notification.put("message", "对方已确认，等待你的确认");
        sendWithRetry(opponentId, notification);
    }

    /**
     * 通知双方场景已就绪
     */
    private void notifySceneReady(Long playerId, Long sceneId) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "SCENE_READY");
        notification.put("sceneId", sceneId);
        notification.put("message", "双方已确认，即将进入场景");
        sendWithRetry(playerId, notification);
    }

    /**
     * 通知匹配超时
     */
    private void notifyMatchTimeout(Long playerId) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "MATCH_TIMEOUT");
        notification.put("message", "匹配超时，请重新匹配");
        sendWithRetry(playerId, notification);
    }

    /**
     * 异步发送，带重试
     */
    private void sendWithRetry(Long playerId, Map<String, Object> notification) {
        scheduler.execute(() -> {
            try {
                String json = objectMapper.writeValueAsString(notification);
                for (int i = 0; i < 5; i++) {
                    boolean sent = webSocketSessionManager.sendToUser(playerId, json);
                    if (sent) {
                        log.info("通知已发送给玩家 {} (第{}次)", playerId, i + 1);
                        return;
                    }
                    log.warn("玩家 {} WS session 不可用，重试 (第{}次)", playerId, i + 1);
                    Thread.sleep(200);
                }
                log.error("通知发送失败: playerId={}", playerId);
            } catch (Exception e) {
                log.error("发送通知异常: playerId={}", playerId, e);
            }
        });
    }

    /**
     * 定时清理超时玩家
     */
    @Scheduled(fixedRate = 10000)
    public void cleanExpiredPlayers() {
        long expireThreshold = System.currentTimeMillis() - MATCH_TIMEOUT_MS;
        Set<String> expiredMembers = redisTemplate.opsForZSet()
                .rangeByScore(MATCH_POOL_KEY, 0, expireThreshold);

        if (expiredMembers == null || expiredMembers.isEmpty()) return;

        for (String memberId : expiredMembers) {
            Long playerId = Long.parseLong(memberId);
            log.info("玩家 {} 匹配超时", playerId);
            removePlayerFromPool(playerId);
            notifyMatchTimeout(playerId);
        }
    }

    public int getWaitingCount() {
        Long count = redisTemplate.opsForZSet().zCard(MATCH_POOL_KEY);
        return count == null ? 0 : count.intValue();
    }

    public Map<String, Object> getMatchStatus(Long playerId) {
        Map<String, Object> status = new HashMap<>();
        Double score = redisTemplate.opsForZSet().score(MATCH_POOL_KEY, playerId.toString());

        if (score == null) {
            status.put("inPool", false);
            status.put("waitingTime", 0);
        } else {
            long waitTime = System.currentTimeMillis() - score.longValue();
            status.put("inPool", true);
            status.put("waitingTime", waitTime);
            status.put("timeout", MATCH_TIMEOUT_MS - waitTime);
        }

        status.put("poolSize", getWaitingCount());
        return status;
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
    }
}
