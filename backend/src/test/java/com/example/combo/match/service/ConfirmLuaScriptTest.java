package com.example.combo.match.service;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;

/**
 * confirm.lua 返回值契约测试：直接对本地 Redis 执行真实 Lua 脚本，
 * 验证 -1 / -2 / 0 / 1 / 2 五种返回值的语义与 TTL 续期约定。
 *
 * 需要本地 Redis（localhost:6379，与 application.properties 一致）；
 * Redis 不可用时测试自动跳过（Assumptions.assumeTrue）。
 * 使用独立测试玩家 ID（99xxxxxx）并在用例前后清理，不影响真实匹配数据。
 */
class ConfirmLuaScriptTest {

    private static final long P1 = 990000001L;
    private static final long P2 = 990000002L;

    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate redis;
    private static RedisScript<Long> confirmScript;

    private String pairId;
    private String pairKey;
    private String p1Key;
    private String p2Key;

    @BeforeAll
    static void setUpRedis() {
        factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration("localhost", 6379));
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        confirmScript = RedisScript.of(new ClassPathResource("lua/confirm.lua"), Long.class);
    }

    @AfterAll
    static void tearDownRedis() {
        if (factory != null) {
            factory.destroy();
        }
    }

    private static boolean redisAvailable() {
        try {
            redis.hasKey("__lua_test_probe__");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(redisAvailable(), "本地 Redis 不可用，跳过 confirm.lua 契约测试");

        pairId = "test-" + System.nanoTime();
        pairKey = "match:pair:" + pairId;
        p1Key = "match:player:pair:" + P1;
        p2Key = "match:player:pair:" + P2;

        cleanup();
        // 构造匹配对（与 MatchService.createMatchPairInRedis 相同的数据结构）
        redis.opsForHash().putAll(pairKey, Map.of(
                "player1Id", String.valueOf(P1),
                "player2Id", String.valueOf(P2),
                "player1Confirmed", "0",
                "player2Confirmed", "0",
                "createdAt", String.valueOf(System.currentTimeMillis())));
        redis.expire(pairKey, Duration.ofSeconds(120));
        redis.opsForValue().set(p1Key, pairId, Duration.ofSeconds(150));
        redis.opsForValue().set(p2Key, pairId, Duration.ofSeconds(150));
    }

    @AfterEach
    void cleanup() {
        redis.delete(Arrays.asList(pairKey, p1Key, p2Key));
        redis.delete("match:temp:confirmed:" + pairKey);
    }

    private Long confirm(long playerId) {
        return redis.execute(confirmScript, Arrays.asList(pairKey, p1Key, p2Key), String.valueOf(playerId));
    }

    @Test
    @DisplayName("匹配对不存在返回 -1")
    void pairNotFoundReturnsMinusOne() {
        redis.delete(pairKey);
        Assertions.assertEquals(-1L, confirm(P1));
    }

    @Test
    @DisplayName("玩家不属于匹配对返回 -2")
    void notInPairReturnsMinusTwo() {
        Assertions.assertEquals(-2L, confirm(123456789L));
    }

    @Test
    @DisplayName("单人确认返回 2：对方未确认，匹配对与双方索引均被续期且索引 TTL 更长")
    void firstConfirmReturnsTwoAndRenewsTtl() {
        Assertions.assertEquals(2L, confirm(P1));

        Assertions.assertEquals("1", redis.opsForHash().get(pairKey, "player1Confirmed"));

        Long pairTtl = redis.getExpire(pairKey);
        Long indexTtl = redis.getExpire(p1Key);
        Assertions.assertNotNull(pairTtl);
        Assertions.assertNotNull(indexTtl);
        Assertions.assertTrue(pairTtl > 100, "匹配对应被续期到 120s 附近");
        Assertions.assertTrue(indexTtl > pairTtl, "玩家索引 TTL 必须比匹配对长（超时清理的扫描窗口）");
        // 对手索引同样被续期
        Assertions.assertNotNull(redis.getExpire(p2Key));
        Assertions.assertTrue(redis.getExpire(p2Key) > pairTtl);
    }

    @Test
    @DisplayName("重复确认返回 0，状态不变")
    void duplicateConfirmReturnsZero() {
        Assertions.assertEquals(2L, confirm(P1));
        Assertions.assertEquals(0L, confirm(P1));
    }

    @Test
    @DisplayName("双方确认返回 1：匹配对与双方索引删除，临时 key 写入玩家 ID")
    void bothConfirmedReturnsOneAndCleansUp() {
        Assertions.assertEquals(2L, confirm(P1));
        Assertions.assertEquals(1L, confirm(P2));

        Assertions.assertFalse(redis.hasKey(pairKey));
        Assertions.assertFalse(redis.hasKey(p1Key));
        Assertions.assertFalse(redis.hasKey(p2Key));

        String tempKey = "match:temp:confirmed:" + pairKey;
        Assertions.assertEquals(P1 + ":" + P2, redis.opsForValue().get(tempKey));
        Assertions.assertNotNull(redis.getExpire(tempKey), "临时 key 应带 TTL");
    }
}
