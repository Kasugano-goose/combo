package com.example.combo.match.service;

import com.example.combo.common.websocket.WebSocketSessionManager;
import com.example.combo.player.domain.Player;
import com.example.combo.scene.domain.Scene;
import com.example.combo.scene.service.SceneService;
import com.example.combo.player.repository.PlayerRepository;
import com.example.combo.player.service.PlayerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
    private static final String MATCH_PAIR_PREFIX = "match:pair:";
    private static final String MATCH_PLAYER_PAIR_PREFIX = "match:player:pair:";
    private static final String MATCH_LOCK_PREFIX = "lock:match:player:";
    private static final long MATCH_TIMEOUT_MS = 60000L;
    private static final long MATCH_PAIRS_TTL_SECONDS = 30L;
    private static final long LOCK_WAIT_TIMEOUT_MS = 3000L;
    private static final long LOCK_HOLD_TIMEOUT_SECONDS = 5L;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    private RedisScript<Long> confirmScript;
    private RedisScript<Long> matchAndRemoveScript;
    private RedisScript<Long> unlockScript;

    @PostConstruct
    public void init() {
        // 加载 Lua 脚本
        this.confirmScript = loadScript("lua/confirm.lua");
        this.matchAndRemoveScript = loadScript("lua/match_and_remove.lua");
        this.unlockScript = loadScript("lua/unlock.lua");
        log.info("MatchService 初始化完成，Lua 脚本已加载");
    }

    private RedisScript<Long> loadScript(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        return RedisScript.of(resource, Long.class);
    }

    // ===== 分布式锁实现 =====

    /**
     * 尝试获取分布式锁
     * @param lockKey 锁的 key
     * @param lockValue 锁的 value（用于标识持有者）
     * @param waitTimeout 等待超时时间（毫秒）
     * @param holdTimeout 锁持有时间（秒）
     * @return 锁的 value，获取失败返回 null
     */
    private String tryLock(String lockKey, String lockValue, long waitTimeout, long holdTimeout) {
        long deadline = System.currentTimeMillis() + waitTimeout;
        while (System.currentTimeMillis() < deadline) {
            Boolean success = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockValue, Duration.ofSeconds(holdTimeout));
            if (Boolean.TRUE.equals(success)) {
                return lockValue;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    /**
     * 释放分布式锁（仅释放自己持有的锁，使用 Lua 脚本保证原子性）
     * @param lockKey 锁的 key
     * @param lockValue 锁的 value（用于验证持有者）
     */
    private void unlock(String lockKey, String lockValue) {
        List<String> keys = Arrays.asList(lockKey);
        Long result = redisTemplate.execute(unlockScript, keys, lockValue);
        if (result != null && result == 1) {
            log.debug("释放锁成功: {}", lockKey);
        }
    }

    /**
     * 加入匹配池（使用分布式锁防止并发竞态）
     */
    public void joinMatch(Long playerId) {
        String lockKey = MATCH_LOCK_PREFIX + playerId;
        String lockValue = UUID.randomUUID().toString();

        if (tryLock(lockKey, lockValue, LOCK_WAIT_TIMEOUT_MS, LOCK_HOLD_TIMEOUT_SECONDS) == null) {
            throw new RuntimeException("系统繁忙，请稍后重试");
        }

        try {
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

            log.info("玩家 {} 加入匹配池，当前匹配池人数: {}", playerId, getWaitingCount());

            tryMatch(player);
        } finally {
            unlock(lockKey, lockValue);
        }
    }

    /**
     * 退出匹配池（使用分布式锁确保与 joinMatch 互斥）
     */
    public void leaveMatch(Long playerId) {
        String lockKey = MATCH_LOCK_PREFIX + playerId;
        String lockValue = UUID.randomUUID().toString();

        if (tryLock(lockKey, lockValue, LOCK_WAIT_TIMEOUT_MS, LOCK_HOLD_TIMEOUT_SECONDS) == null) {
            throw new RuntimeException("系统繁忙，请稍后重试");
        }

        try {
            redisTemplate.opsForZSet().remove(MATCH_POOL_KEY, playerId.toString());
            redisTemplate.delete(MATCH_PLAYER_PREFIX + playerId);
            log.info("玩家 {} 退出匹配池，当前匹配池人数: {}", playerId, getWaitingCount());
        } finally {
            unlock(lockKey, lockValue);
        }
    }

    /**
     * 尝试匹配（使用 Lua 脚本保证原子性移除双方）
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
                // 使用 Lua 脚本原子性地检查并移除双方
                String playerKey = MATCH_PLAYER_PREFIX + player.getId();
                String opponentKey = MATCH_PLAYER_PREFIX + opponentId;
                List<String> keys = Arrays.asList(MATCH_POOL_KEY, playerKey, opponentKey);
                Long result = redisTemplate.execute(
                        matchAndRemoveScript, keys,
                        player.getId().toString(), opponentId.toString()
                );

                if (result == null || result == 0) {
                    // 对手已被其他人匹配，继续尝试下一个
                    log.info("玩家 {} 已被其他人匹配，跳过", opponentId);
                    continue;
                }

                log.info("匹配成功！玩家 {} 与玩家 {}", player.getId(), opponentId);

                // 创建匹配对存储到 Redis
                createMatchPairInRedis(player.getId(), opponentId);

                // 通知双方匹配成功（无 sceneId）
                notifyMatchFound(player, opponent);
                notifyMatchFound(opponent, player);
                return;
            }
        }

        log.info("玩家 {} 暂未找到兼容对手，等待中...", player.getId());
    }

    /**
     * 在 Redis 中创建匹配对
     */
    private void createMatchPairInRedis(Long player1Id, Long player2Id) {
        String pairId = UUID.randomUUID().toString();
        String pairKey = MATCH_PAIR_PREFIX + pairId;
        String player1Key = MATCH_PLAYER_PAIR_PREFIX + player1Id;
        String player2Key = MATCH_PLAYER_PAIR_PREFIX + player2Id;

        // 存储匹配对详情
        Map<String, String> pairData = new HashMap<>();
        pairData.put("player1Id", player1Id.toString());
        pairData.put("player2Id", player2Id.toString());
        pairData.put("player1Confirmed", "0");
        pairData.put("player2Confirmed", "0");
        pairData.put("createdAt", String.valueOf(System.currentTimeMillis()));

        redisTemplate.opsForHash().putAll(pairKey, pairData);
        redisTemplate.expire(pairKey, Duration.ofSeconds(MATCH_PAIRS_TTL_SECONDS));

        // 存储玩家索引
        redisTemplate.opsForValue().set(player1Key, pairId, MATCH_PAIRS_TTL_SECONDS);
        redisTemplate.opsForValue().set(player2Key, pairId, MATCH_PAIRS_TTL_SECONDS);

        log.info("创建匹配对 Redis: pairId={}, player1={}, player2={}", pairId, player1Id, player2Id);
    }

    /**
     * 玩家确认进入场景（使用 Lua 脚本保证原子性）
     */
    public void confirmMatch(Long playerId) {
        String playerKey = MATCH_PLAYER_PAIR_PREFIX + playerId;
        String pairId = redisTemplate.opsForValue().get(playerKey);

        if (pairId == null) {
            throw new IllegalArgumentException("没有待确认的匹配");
        }

        String pairKey = MATCH_PAIR_PREFIX + pairId;

        // 执行 Lua 脚本
        List<String> keys = Arrays.asList(pairKey, playerKey);
        Long result = redisTemplate.execute(confirmScript, keys, playerId.toString());

        if (result == null) {
            throw new RuntimeException("Redis 执行异常");
        }

        log.info("玩家 {} 确认匹配，Lua 返回结果: {}", playerId, result);

        switch (result.intValue()) {
            case -1:
                throw new IllegalArgumentException("匹配对已超时或不存在");
            case -2:
                throw new IllegalArgumentException("你不属于该匹配对");
            case 0:
                throw new IllegalArgumentException("你已确认，等待对方确认");
            case 1:
                // 双方都已确认，创建场景
                log.info("双方都已确认，创建场景");
                handleBothConfirmed(pairKey, playerId);
                break;
            case 2:
                // 仅当前玩家确认，通知对手
                log.info("玩家 {} 已确认，等待对方确认", playerId);
                Long opponentId = getOpponentIdFromPair(pairKey, playerId);
                if (opponentId != null) {
                    notifyOpponentConfirmed(opponentId);
                }
                break;
            default:
                log.error("未知的 Lua 返回值: {}", result);
                throw new RuntimeException("未知的确认结果");
        }
    }

    /**
     * 从匹配对中获取对手 ID
     */
    private Long getOpponentIdFromPair(String pairKey, Long playerId) {
        Map<Object, Object> pairData = redisTemplate.opsForHash().entries(pairKey);

        if (pairData.isEmpty()) {
            return null;
        }

        Long player1Id = Long.parseLong(pairData.get("player1Id").toString());
        Long player2Id = Long.parseLong(pairData.get("player2Id").toString());

        return player1Id.equals(playerId) ? player2Id : player1Id;
    }

    /**
     * 处理双方都确认的情况：创建场景并通知
     */
    private void handleBothConfirmed(String pairKey, Long confirmPlayerId) {
        // 从临时 key 获取玩家信息
        String tempKey = "match:temp:confirmed:" + pairKey;
        String playerIds = redisTemplate.opsForValue().get(tempKey);

        if (playerIds == null) {
            log.error("无法获取匹配对玩家信息，临时 key 已过期: {}", tempKey);
            return;
        }

        // 解析玩家 ID
        String[] ids = playerIds.split(":");
        Long player1Id = Long.parseLong(ids[0]);
        Long player2Id = Long.parseLong(ids[1]);

        // 删除临时 key
        redisTemplate.delete(tempKey);

        // 查找玩家信息
        Player player1 = playerRepository.findById(player1Id).orElse(null);
        Player player2 = playerRepository.findById(player2Id).orElse(null);

        if (player1 == null || player2 == null) {
            log.error("创建场景失败：玩家不存在 player1={}, player2={}", player1Id, player2Id);
            return;
        }

        // 创建场景
        Scene scene = sceneService.createScene(player1, player2);

        // 通知双方进入场景
        notifySceneReady(player1Id, scene.getSceneId());
        notifySceneReady(player2Id, scene.getSceneId());
    }

    /**
     * 通知匹配对超时
     */
    public void notifyConfirmTimeout(Long playerId) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "CONFIRM_TIMEOUT");
        notification.put("message", "确认超时，匹配已取消，请重新匹配");
        sendWithRetry(playerId, notification);
    }

    /**
     * 从匹配池中移除玩家
     */
    private void removePlayerFromPool(Long playerId) {
        redisTemplate.opsForZSet().remove(MATCH_POOL_KEY, playerId.toString());
        redisTemplate.delete(MATCH_PLAYER_PREFIX + playerId);
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

    /**
     * 定时清理残留的玩家索引（当匹配对已过期但索引未清理时）
     */
    @Scheduled(fixedRate = 10000)
    public void cleanExpiredMatchPairIndexes() {
        Set<String> playerKeys = redisTemplate.keys(MATCH_PLAYER_PAIR_PREFIX + "*");
        if (playerKeys == null || playerKeys.isEmpty()) return;

        for (String playerKey : playerKeys) {
            String pairId = redisTemplate.opsForValue().get(playerKey);
            if (pairId == null) {
                // 索引值为空，删除
                redisTemplate.delete(playerKey);
                continue;
            }

            String pairKey = MATCH_PAIR_PREFIX + pairId;
            Boolean pairExists = redisTemplate.hasKey(pairKey);

            if (pairExists == null || !pairExists) {
                // 匹配对已不存在（已过期），删除残留索引并通知超时
                String playerIdStr = playerKey.replace(MATCH_PLAYER_PAIR_PREFIX, "");
                Long playerId = Long.parseLong(playerIdStr);
                redisTemplate.delete(playerKey);
                log.info("清理残留匹配索引: playerId={}", playerId);
                notifyConfirmTimeout(playerId);
            }
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
