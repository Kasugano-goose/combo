package com.example.combo.match.service;

import com.example.combo.common.websocket.WebSocketSessionManager;
import com.example.combo.player.domain.Player;
import com.example.combo.scene.domain.Scene;
import com.example.combo.scene.service.SceneService;
import com.example.combo.player.repository.PlayerRepository;
import com.example.combo.player.service.PlayerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MatchService {
    private final PlayerRepository playerRepository;
    private final PlayerService playerService;
    private final StringRedisTemplate redisTemplate;
    private final WebSocketSessionManager webSocketSessionManager;
    private final ObjectMapper objectMapper;
    private final SceneService sceneService;
    private final Executor notifyExecutor;

    // Redis Key 定义
    // 注意：Redis 集群部署时需使用 Hash Tag 确保相关 Key 在同一 slot
    // 例如改为 "{match}:pool"、"{match}:player:{id}" 等
    private static final String MATCH_POOL_KEY = "match:pool";
    private static final String MATCH_PLAYER_PREFIX = "match:player:";
    private static final String MATCH_PAIR_PREFIX = "match:pair:";
    private static final String MATCH_PLAYER_PAIR_PREFIX = "match:player:pair:";
    private static final String MATCH_LOCK_PREFIX = "lock:match:player:";

    // 超时配置
    private static final long MATCH_TIMEOUT_MS = 60000L;           // 匹配池等待超时 60s
    private static final long MATCH_PAIRS_TTL_SECONDS = 120L;      // 匹配对有效期 120s（原 30s 过短，确认期间可能过期）
    private static final long LOCK_WAIT_TIMEOUT_MS = 3000L;        // 获取锁最大等待 3s
    private static final long LOCK_HOLD_TIMEOUT_SECONDS = 30L;     // 锁持有时间 30s（原 5s 过短，含 DB 查询+匹配遍历）

    // 匹配确认状态码（对应 confirm.lua 返回值）
    private static final long CONFIRM_PAIR_NOT_FOUND = -1;  // 匹配对不存在
    private static final long CONFIRM_NOT_IN_PAIR = -2;     // 玩家不属于该匹配对
    private static final long CONFIRM_ALREADY_DONE = 0;     // 玩家已确认，无需重复操作
    private static final long CONFIRM_BOTH_DONE = 1;        // 双方确认完成
    private static final long CONFIRM_ONE_DONE = 2;         // 仅当前玩家确认，等待对方

    public MatchService(PlayerRepository playerRepository,
                        PlayerService playerService,
                        StringRedisTemplate redisTemplate,
                        WebSocketSessionManager webSocketSessionManager,
                        ObjectMapper objectMapper,
                        SceneService sceneService,
                        @Qualifier("notifyExecutor") Executor notifyExecutor) {
        this.playerRepository = playerRepository;
        this.playerService = playerService;
        this.redisTemplate = redisTemplate;
        this.webSocketSessionManager = webSocketSessionManager;
        this.objectMapper = objectMapper;
        this.sceneService = sceneService;
        this.notifyExecutor = notifyExecutor;
    }

    // 匹配遍历优化：优先匹配分数相近的玩家（±200 分），匹配不到再全量遍历
    private static final double MATCH_SCORE_RANGE = 200.0;

    // 通知持久化：未送达通知暂存 Redis，玩家重连后拉取
    private static final String PENDING_NOTIFICATION_PREFIX = "match:pending:notify:";
    private static final long PENDING_NOTIFICATION_TTL_SECONDS = 300L; // 5 分钟过期

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

            // 使用 rankScore 作为 ZSet score，使 rangeByScore 按分数范围查询生效
            double rankScore = player.getRankScore() != null ? player.getRankScore() : 0;
            redisTemplate.opsForZSet().add(MATCH_POOL_KEY, playerId.toString(), rankScore);

            String playerKey = MATCH_PLAYER_PREFIX + playerId;
            Map<String, String> playerInfo = new HashMap<>();
            playerInfo.put("username", player.getUsername());
            playerInfo.put("rankScore", String.valueOf(player.getRankScore()));
            playerInfo.put("rankLevel", String.valueOf(player.getCurrentRank().getLevel()));
            playerInfo.put("selectedRoleId", String.valueOf(player.getSelectedRoleId()));
            playerInfo.put("joinedAt", String.valueOf(System.currentTimeMillis()));
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
     * 优化：优先匹配分数相近的玩家，匹配不到再全量遍历
     */
    private void tryMatch(Player player) {
        long playerId = player.getId();
        int rankScore = player.getRankScore() != null ? player.getRankScore() : 0;

        // 第一轮：优先匹配分数相近的玩家（±MATCH_SCORE_RANGE），减少遍历范围
        Set<String> nearbyMembers = redisTemplate.opsForZSet()
                .rangeByScore(MATCH_POOL_KEY, rankScore - MATCH_SCORE_RANGE, rankScore + MATCH_SCORE_RANGE);

        if (tryMatchFromCandidates(player, nearbyMembers)) return;

        // 第二轮：全量遍历（兜底）
        Set<String> allMembers = redisTemplate.opsForZSet().reverseRange(MATCH_POOL_KEY, 0, -1);
        if (tryMatchFromCandidates(player, allMembers)) return;

        log.info("玩家 {} 暂未找到兼容对手，等待中...", playerId);
    }

    /**
     * 从候选集合中尝试匹配
     *
     * <p>优化：预先批量查询所有候选玩家，避免循环内 N+1 DB 查询。
     * 候选集合通常较小（±200 分范围），批量查询开销可接受。
     *
     * @return true 表示匹配成功
     */
    private boolean tryMatchFromCandidates(Player player, Set<String> candidates) {
        if (candidates == null || candidates.isEmpty()) return false;

        // 批量查询所有候选玩家，避免循环内 N+1 查询
        List<Long> candidateIds = candidates.stream()
                .map(Long::parseLong)
                .filter(id -> !id.equals(player.getId()))
                .collect(Collectors.toList());

        if (candidateIds.isEmpty()) return false;

        Map<Long, Player> opponentMap = playerRepository.findAllById(candidateIds)
                .stream()
                .collect(Collectors.toMap(Player::getId, p -> p));

        for (String memberId : candidates) {
            Long opponentId = Long.parseLong(memberId);
            if (opponentId.equals(player.getId())) continue;

            Player opponent = opponentMap.get(opponentId);
            if (opponent == null || opponent.getStatus() != Player.PlayerStatus.NORMAL) {
                removePlayerFromPool(opponentId);
                continue;
            }

            // 注意：player.getStatus() 和 canMatchWith 在锁外执行，存在时间窗口：
            // 对手状态可能在检查后被其他线程修改（如被封禁）。
            // 当前依赖 matchAndRemoveScript 的原子性兜底，若需严格一致，
            // 应将状态校验纳入 Lua 脚本或在移除后二次校验。
            if (playerService.canMatchWith(player, opponent)) {
                // 使用 Lua 脚本原子性地检查并移除双方
                // 返回值：1=成功移除双方，0=对手已不在池中（被其他人匹配或超时清理）
                String playerKey = MATCH_PLAYER_PREFIX + player.getId();
                String opponentKey = MATCH_PLAYER_PREFIX + opponentId;
                List<String> keys = Arrays.asList(MATCH_POOL_KEY, playerKey, opponentKey);
                Long result = redisTemplate.execute(
                        matchAndRemoveScript, keys,
                        player.getId().toString(), opponentId.toString()
                );

                if (result == null || result == 0) {
                    // 对手已被其他人匹配或已调用 leaveMatch 退出池，继续尝试下一个
                    log.debug("玩家 {} 已不在匹配池中，跳过", opponentId);
                    continue;
                }

                // 防御性校验：Lua 脚本已原子移除双方，验证双方确实不在池中
                if (redisTemplate.opsForZSet().score(MATCH_POOL_KEY, player.getId().toString()) != null
                        || redisTemplate.opsForZSet().score(MATCH_POOL_KEY, opponentId.toString()) != null) {
                    log.warn("匹配后检测到异常：玩家仍在匹配池中，重新清理并跳过: player={}, opponent={}",
                            player.getId(), opponentId);
                    removePlayerFromPool(player.getId());
                    removePlayerFromPool(opponentId);
                    continue;
                }

                log.info("匹配成功！玩家 {} 与玩家 {}", player.getId(), opponentId);

                // 创建匹配对存储到 Redis
                createMatchPairInRedis(player.getId(), opponentId);

                // 通知双方匹配成功（无 sceneId）
                notifyMatchFound(player, opponent);
                notifyMatchFound(opponent, player);
                return true;
            }
        }
        return false;
    }

    /**
     * 在 Redis 中创建匹配对
     * 使用 MULTI/EXEC 事务保证所有操作原子执行，
     * 防止定时任务在 playerIndex 写入后、pairKey 写入前扫描到不一致状态
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

        // 使用事务保证原子性：要么全部成功，要么全部不执行
        redisTemplate.execute(new org.springframework.data.redis.core.SessionCallback<List<Object>>() {
            @Override
            @SuppressWarnings("unchecked")
            public List<Object> execute(org.springframework.data.redis.core.RedisOperations operations) {
                operations.multi();
                operations.opsForHash().putAll(pairKey, pairData);
                operations.expire(pairKey, Duration.ofSeconds(MATCH_PAIRS_TTL_SECONDS));
                operations.opsForValue().set(player1Key, pairId, Duration.ofSeconds(MATCH_PAIRS_TTL_SECONDS));
                operations.opsForValue().set(player2Key, pairId, Duration.ofSeconds(MATCH_PAIRS_TTL_SECONDS));
                return operations.exec();
            }
        });

        log.info("创建匹配对 Redis: pairId={}, player1={}, player2={}", pairId, player1Id, player2Id);
    }

    /**
     * 玩家确认进入场景（使用 Lua 脚本保证原子性）
     */
    public Map<String, Object> confirmMatch(Long playerId) {
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
            case (int) CONFIRM_PAIR_NOT_FOUND:
                log.warn("玩家 {} 确认失败：匹配对已超时或不存在, pairKey={}", playerId, pairKey);
                throw new IllegalArgumentException("匹配对已超时或不存在");
            case (int) CONFIRM_NOT_IN_PAIR:
                log.warn("玩家 {} 确认失败：不属于匹配对 {}", playerId, pairKey);
                throw new IllegalArgumentException("你不属于该匹配对");
            case (int) CONFIRM_ALREADY_DONE:
                log.info("玩家 {} 已确认过，忽略重复操作", playerId);
                Map<String, Object> alreadyResult = new HashMap<>();
                alreadyResult.put("status", "alreadyConfirmed");
                return alreadyResult;
            case (int) CONFIRM_BOTH_DONE:
                // 双方都已确认，创建场景
                log.info("双方都已确认，创建场景, pairKey={}", pairKey);
                handleBothConfirmed(pairKey, playerId);
                Map<String, Object> bothResult = new HashMap<>();
                bothResult.put("status", "bothConfirmed");
                return bothResult;
            case (int) CONFIRM_ONE_DONE:
                // 仅当前玩家确认，通知对手
                log.info("玩家 {} 已确认，等待对方确认", playerId);
                Long opponentId = getOpponentIdFromPair(pairKey, playerId);
                if (opponentId != null) {
                    notifyOpponentConfirmed(opponentId);
                }
                Map<String, Object> oneResult = new HashMap<>();
                oneResult.put("status", "waitingForOpponent");
                return oneResult;
            default:
                log.error("未知的 Lua 返回值: {}, playerId={}, pairKey={}", result, playerId, pairKey);
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
            // 从 pairKey 获取玩家 ID，通知双方确认失败需重新匹配
            Map<Object, Object> pairData = redisTemplate.opsForHash().entries(pairKey);
            if (!pairData.isEmpty()) {
                Long p1 = Long.parseLong(pairData.get("player1Id").toString());
                Long p2 = Long.parseLong(pairData.get("player2Id").toString());
                Map<String, Object> errorNotification = new HashMap<>();
                errorNotification.put("type", "CONFIRM_FAILED");
                errorNotification.put("message", "确认超时，请重新匹配");
                sendWithRetry(p1, errorNotification);
                sendWithRetry(p2, errorNotification);
            }
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
     * 异步发送通知，带渐进式重试和 Redis 持久化兜底。
     *
     * <p><b>端点说明</b>：Match 通知推送到 {@code /ws/friend/{playerId}} 端点（endpointType="friend"）。
     * Match 与 Friend 共用同一 WebSocket 连接，客户端通过消息中的 {@code type} 字段区分业务类型。
     * 若未来拆分为独立 WS 连接，需同步修改此处的端点名称。
     *
     * <p><b>重试策略</b>：总窗口 ~65 秒，渐进式间隔（1s, 2s, 3s, 5s, 5s, 7s, 7s, 10s, 10s, 15s）。
     * 全部失败后存入 Redis List，玩家重连时通过 {@link #pullPendingNotifications} 自动拉取。
     */
    private void sendWithRetry(Long playerId, Map<String, Object> notification) {
        notifyExecutor.execute(() -> {
            try {
                String json = objectMapper.writeValueAsString(notification);
                String type = (String) notification.get("type");
                int[] delays = {1000, 2000, 3000, 5000, 5000, 7000, 7000, 10000, 10000, 15000}; // 渐进式间隔，总计 ~65s

                for (int i = 0; i < delays.length; i++) {
                    // 每次发送前打印当前会话状态
                    String status = webSocketSessionManager.getSessionStatus(playerId);
                    log.info("发送通知给玩家 {} [type={}, 第{}次]，会话状态: {}", playerId, type, i + 1, status);

                    boolean sent = webSocketSessionManager.sendToEndpoint(playerId, "friend", json);
                    if (sent) {
                        log.info("通知已发送给玩家 {} [type={}, 第{}次尝试]", playerId, type, i + 1);
                        return;
                    }
                    log.warn("玩家 {} 发送失败 [type={}，第{}次]，{}ms 后重试", playerId, type, i + 1, delays[i]);
                    Thread.sleep(delays[i]);
                }

                // 全部重试失败，持久化到 Redis
                savePendingNotification(playerId, json);
                log.error("通知最终失败，已持久化: playerId={}, type={}", playerId, type);
            } catch (Exception e) {
                log.error("发送通知异常: playerId={}, type={}", playerId, notification.get("type"), e);
                try {
                    savePendingNotification(playerId, objectMapper.writeValueAsString(notification));
                } catch (Exception ex) {
                    log.error("持久化通知也失败: playerId={}", playerId, ex);
                }
            }
        });
    }

    /**
     * 将未送达通知存入 Redis List
     */
    private void savePendingNotification(Long playerId, String json) {
        String key = PENDING_NOTIFICATION_PREFIX + playerId;
        redisTemplate.opsForList().rightPush(key, json);
        redisTemplate.expire(key, Duration.ofSeconds(PENDING_NOTIFICATION_TTL_SECONDS));
        log.info("通知已存入 Redis: playerId={}, key={}", playerId, key);
    }

    /**
     * 玩家重连时拉取未送达通知（由 WebSocket @OnOpen 调用）
     * 使用 leftPop 循环逐条弹出，保证原子性（不会在 range 和 delete 之间丢失通知）
     */
    public List<String> pullPendingNotifications(Long playerId) {
        String key = PENDING_NOTIFICATION_PREFIX + playerId;
        List<String> notifications = new ArrayList<>();
        String notification;
        while ((notification = redisTemplate.opsForList().leftPop(key)) != null) {
            notifications.add(notification);
        }
        if (!notifications.isEmpty()) {
            log.info("玩家 {} 拉取 {} 条待补偿通知", playerId, notifications.size());
        }
        return notifications;
    }

    /**
     * 定时清理超时玩家
     *
     * TODO 改进：当前定时任务与 joinMatch/confirmMatch 无协调，可能并发冲突。
     * 例如清理时玩家刚好被 tryMatch 选中，导致 Lua 脚本返回 0。
     * 改进方案：
     * 1. 使用分布式锁保护清理逻辑（与主流程共享锁或独立锁）
     * 2. 使用 Redis 事务（MULTI/EXEC）保证读取和删除的原子性
     * 3. 使用 Redis 发布-订阅通知状态变更，避免轮询
     */
    @Scheduled(fixedRate = 10000)
    public void cleanExpiredPlayers() {
        // ZSet score 是 rankScore，不能用于判断超时；改为遍历所有成员并检查 joinedAt
        Set<String> allMembers = redisTemplate.opsForZSet().range(MATCH_POOL_KEY, 0, -1);
        if (allMembers == null || allMembers.isEmpty()) return;

        for (String memberId : allMembers) {
            String playerKey = MATCH_PLAYER_PREFIX + memberId;
            Object joinedAtObj = redisTemplate.opsForHash().get(playerKey, "joinedAt");
            if (joinedAtObj == null) {
                // 无加入时间记录，跳过（不应发生）
                continue;
            }
            long joinedAt = Long.parseLong(joinedAtObj.toString());
            if (System.currentTimeMillis() - joinedAt > MATCH_TIMEOUT_MS) {
                Long playerId = Long.parseLong(memberId);
                log.info("玩家 {} 匹配超时", playerId);
                removePlayerFromPool(playerId);
                notifyMatchTimeout(playerId);
            }
        }
    }

    /**
     * 定时清理残留的玩家索引（当匹配对已过期但索引未清理时）
     *
     * 安全措施：
     * 1. 检查 playerKey 剩余 TTL，刚创建（>100s）的跳过，避免与 createMatchPairInRedis 竞态
     * 2. 二次验证 playerKey 的 pairId 是否仍然匹配，防止误删刚创建的新索引
     */
    @Scheduled(fixedRate = 10000)
    public void cleanExpiredMatchPairIndexes() {
        // 使用 SCAN 替代 KEYS 避免阻塞 Redis
        Set<String> playerKeys = new HashSet<>();
        redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            try (Cursor<byte[]> cursor = connection.scan(
                    ScanOptions.scanOptions().match(MATCH_PLAYER_PAIR_PREFIX + "*").count(100).build())) {
                while (cursor.hasNext()) {
                    playerKeys.add(new String(cursor.next()));
                }
            }
            return null;
        });
        if (playerKeys.isEmpty()) return;

        for (String playerKey : playerKeys) {
            // 安全检查：TTL 过长说明刚创建，跳过（防止与 createMatchPairInRedis 竞态）
            Long ttl = redisTemplate.getExpire(playerKey, TimeUnit.SECONDS);
            if (ttl != null && ttl > (MATCH_PAIRS_TTL_SECONDS - 10)) {
                // 索引创建不足 10 秒，跳过
                continue;
            }

            String pairId = redisTemplate.opsForValue().get(playerKey);
            if (pairId == null) {
                // 索引值为空，删除
                redisTemplate.delete(playerKey);
                continue;
            }

            String pairKey = MATCH_PAIR_PREFIX + pairId;
            Boolean pairExists = redisTemplate.hasKey(pairKey);

            if (pairExists == null || !pairExists) {
                // 二次验证：再次读取 pairId，确认未被新匹配覆盖
                String currentPairId = redisTemplate.opsForValue().get(playerKey);
                if (currentPairId != null && !currentPairId.equals(pairId)) {
                    // pairId 已被新匹配覆盖，跳过
                    log.debug("索引已被新匹配覆盖，跳过: playerKey={}", playerKey);
                    continue;
                }

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
        String playerIdStr = playerId.toString();

        // 1. 优先检查是否已在匹配对中（已匹配成功，等待确认）
        String pairId = redisTemplate.opsForValue().get(MATCH_PLAYER_PAIR_PREFIX + playerIdStr);
        if (pairId != null) {
            String pairKey = MATCH_PAIR_PREFIX + pairId;
            Map<Object, Object> pairData = redisTemplate.opsForHash().entries(pairKey);
            if (!pairData.isEmpty()) {
                String p1 = pairData.get("player1Id").toString();
                String p2 = pairData.get("player2Id").toString();
                Long opponentId = Long.parseLong(p1.equals(playerIdStr) ? p2 : p1);
                Player opponent = playerRepository.findById(opponentId).orElse(null);

                String yourConfirmed = p1.equals(playerIdStr)
                        ? pairData.get("player1Confirmed").toString() : pairData.get("player2Confirmed").toString();
                String opponentConfirmed = p1.equals(playerIdStr)
                        ? pairData.get("player2Confirmed").toString() : pairData.get("player1Confirmed").toString();

                status.put("hasPendingMatch", true);
                status.put("pairId", pairId);
                status.put("opponentId", opponentId);
                status.put("opponentName", opponent != null ? opponent.getUsername() : "未知");
                status.put("opponentScore", opponent != null && opponent.getRankScore() != null ? opponent.getRankScore() : 0);
                status.put("yourConfirmed", "1".equals(yourConfirmed));
                status.put("opponentConfirmed", "1".equals(opponentConfirmed));
                status.put("createdAt", pairData.get("createdAt"));
                status.put("inPool", false);
                status.put("poolSize", getWaitingCount());
                return status;
            }
        }

        // 2. 没有匹配对，再查匹配池（等待匹配中）
        // ZSet score 是 rankScore，超时需从 playerInfo hash 的 joinedAt 计算
        Double score = redisTemplate.opsForZSet().score(MATCH_POOL_KEY, playerIdStr);
        if (score != null) {
            String playerKey = MATCH_PLAYER_PREFIX + playerIdStr;
            Object joinedAtObj = redisTemplate.opsForHash().get(playerKey, "joinedAt");
            long joinedAt = joinedAtObj != null ? Long.parseLong(joinedAtObj.toString()) : System.currentTimeMillis();
            long waitTime = System.currentTimeMillis() - joinedAt;
            status.put("inPool", true);
            status.put("waitingTime", waitTime);
            status.put("timeout", Math.max(0, MATCH_TIMEOUT_MS - waitTime));
            status.put("hasPendingMatch", false);
        } else {
            status.put("inPool", false);
            status.put("waitingTime", 0);
            status.put("timeout", MATCH_TIMEOUT_MS);
            status.put("hasPendingMatch", false);
        }
        status.put("poolSize", getWaitingCount());
        return status;
    }
}
