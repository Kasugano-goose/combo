package com.example.combo.simulation;

import com.example.combo.player.repository.PlayerRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 20 人并发匹配模拟（需本地 MySQL + Redis）：
 * 20 个虚拟玩家各自注册登录 → 先建立好友 WS 连接 → 并发 join 匹配 →
 * 收到 MATCHED 后并发 confirm → 收到 SCENE_READY 后汇总校验：
 *   1. 全部匹配成功且恰好形成 10 对（无重复配对、无交叉配对）
 *   2. 全部双方确认并进入场景，同一对双方拿到相同 sceneId
 *   3. 输出匹配耗时统计
 *
 * 默认不执行（@EnabledIfSystemProperty），运行方式：
 *   ./mvnw test -Dsimulate=true -Dtest=MatchSimulationTest
 *
 * 测试账号使用毫秒时间戳生成（13 位），用例结束后自动删除，不影响真实账号。
 */
@EnabledIfSystemProperty(named = "simulate", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MatchSimulationTest {

    private static final int PLAYER_COUNT = 20;
    private static final Duration MATCH_TIMEOUT = Duration.ofSeconds(70);
    private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(60);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private PlayerRepository playerRepository;

    /** 单个虚拟玩家的状态与结果 */
    private static final class SimPlayer {
        final long id;
        final String password = "sim123456";
        final BlockingQueue<String> inbox = new LinkedBlockingQueue<>();

        volatile String jsessionid;
        volatile WebSocketSession ws;

        volatile Long opponentId;
        volatile Long sceneId;
        volatile long matchedAtNanos;
        volatile String error;

        SimPlayer(long id) {
            this.id = id;
        }
    }

    @Test
    @DisplayName("20 人并发匹配：无重复配对、全部确认进场景")
    void simulateTwentyPlayersConcurrentMatch() throws Exception {
        long baseId = System.currentTimeMillis(); // 13 位起始 ID，避免与真实账号冲突
        List<SimPlayer> players = new ArrayList<>();
        for (int i = 0; i < PLAYER_COUNT; i++) {
            players.add(new SimPlayer(baseId + i));
        }

        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        String baseUrl = "http://localhost:" + port;

        try {
            // 1. 注册 + 登录（登录后手动携带 JSESSIONID，所有请求共享一个 HttpClient）
            for (SimPlayer p : players) {
                register(http, baseUrl, p);
                login(http, baseUrl, p);
            }

            // 2. 每个玩家先建立好友 WS（模拟真实客户端：先连 WS 再点匹配）
            websocketConnect(players);

            // 3. 20 个线程同一时刻并发 join
            long joinStart = System.nanoTime();
            runConcurrently(players, p -> join(http, baseUrl, p));

            // 4. 等待 MATCHED
            runConcurrently(players, p -> {
                JsonNode matched = awaitMessage(p, "MATCHED", MATCH_TIMEOUT);
                if (matched == null) {
                    appendError(p, "等待 MATCHED 超时");
                    return;
                }
                p.opponentId = matched.path("opponentId").asLong();
                p.matchedAtNanos = System.nanoTime();
            });

            // 5. 已匹配玩家并发 confirm，等待 SCENE_READY
            List<SimPlayer> matchedPlayers = players.stream()
                    .filter(p -> p.opponentId != null)
                    .toList();
            runConcurrently(matchedPlayers, p -> confirm(http, baseUrl, p));
            runConcurrently(matchedPlayers, p -> {
                JsonNode ready = awaitMessage(p, "SCENE_READY", CONFIRM_TIMEOUT);
                if (ready == null) {
                    appendError(p, "等待 SCENE_READY 超时");
                    return;
                }
                p.sceneId = ready.path("sceneId").asLong();
            });

            // 6. 汇总统计与校验
            long matchedCount = players.stream().filter(p -> p.opponentId != null).count();
            long sceneReadyCount = players.stream().filter(p -> p.sceneId != null).count();

            java.util.Map<Long, SimPlayer> byId = players.stream()
                    .collect(Collectors.toMap(p -> p.id, p -> p));

            // 配对一致性：双方互相指向，且没有交叉/重复配对
            Set<String> pairs = new HashSet<>();
            int inconsistentPairs = 0;
            for (SimPlayer p : players) {
                if (p.opponentId == null) {
                    continue;
                }
                SimPlayer opp = byId.get(p.opponentId);
                if (opp == null || opp.opponentId == null || opp.opponentId != p.id) {
                    inconsistentPairs++;
                    appendError(p, "对手信息不一致: opponentId=" + p.opponentId);
                    continue;
                }
                pairs.add(Math.min(p.id, p.opponentId) + "-" + Math.max(p.id, p.opponentId));
            }

            // 同一对双方必须进入同一个场景
            int sceneMismatch = 0;
            for (SimPlayer p : players) {
                if (p.sceneId == null || p.opponentId == null) {
                    continue;
                }
                SimPlayer opp = byId.get(p.opponentId);
                if (opp != null && opp.sceneId != null && !opp.sceneId.equals(p.sceneId)) {
                    sceneMismatch++;
                }
            }

            List<SimPlayer> failed = players.stream()
                    .filter(p -> p.error != null)
                    .toList();

            double avgMatchMs = players.stream()
                    .filter(p -> p.opponentId != null)
                    .mapToLong(p -> p.matchedAtNanos - joinStart)
                    .average()
                    .orElse(0) / 1_000_000.0;
            long maxMatchMs = players.stream()
                    .filter(p -> p.opponentId != null)
                    .mapToLong(p -> p.matchedAtNanos - joinStart)
                    .max()
                    .orElse(0) / 1_000_000;
            long totalMs = (System.nanoTime() - joinStart) / 1_000_000;

            System.out.println("""
                    ========== 20 人并发匹配模拟结果 ==========
                    参与玩家            : %d
                    匹配成功            : %d/%d（%d 对）
                    确认并进入场景      : %d/%d
                    重复/不一致配对     : %d
                    场景 ID 不一致      : %d
                    失败玩家            : %d
                    join → MATCHED      : 平均 %.0f ms / 最大 %d ms
                    全流程（join → 场景）: %d ms
                    ==========================================
                    """.formatted(
                    PLAYER_COUNT,
                    matchedCount, PLAYER_COUNT, pairs.size(),
                    sceneReadyCount, PLAYER_COUNT,
                    inconsistentPairs,
                    sceneMismatch,
                    failed.size(),
                    avgMatchMs, maxMatchMs,
                    totalMs));

            for (SimPlayer p : failed) {
                System.out.println("  玩家 " + p.id + " 失败原因: " + p.error);
            }

            assertEquals(PLAYER_COUNT, matchedCount, "应全部匹配成功");
            assertEquals(PLAYER_COUNT / 2, pairs.size(), "应恰好形成 10 对，无重复配对");
            assertEquals(0, inconsistentPairs, "不允许重复/不一致配对");
            assertEquals(PLAYER_COUNT, sceneReadyCount, "双方确认后应全部收到 SCENE_READY");
            assertEquals(0, sceneMismatch, "同一对双方必须进入同一场景");
            assertTrue(failed.isEmpty(), "不应存在失败玩家");
        } finally {
            // 7. 清理：关闭 WS、删除测试账号（Redis 匹配键自带 TTL 自动过期）
            for (SimPlayer p : players) {
                try {
                    if (p.ws != null && p.ws.isOpen()) {
                        p.ws.close();
                    }
                } catch (Exception ignored) {
                    // 清理失败不影响断言结果
                }
            }
            try {
                playerRepository.deleteAllById(players.stream().map(p -> p.id).toList());
            } catch (Exception e) {
                System.out.println("清理测试账号失败: " + e.getMessage());
            }
        }
    }

    // ===== HTTP =====

    private void register(HttpClient http, String baseUrl, SimPlayer p) throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("id", p.id);
        body.put("password", p.password);
        body.put("username", "sim_" + p.id);
        body.put("realName", "模拟玩家");
        body.put("phone", String.format("19%09d", p.id % 1_000_000_000L));
        body.put("idCard", String.format("%018d", p.id));

        HttpResponse<String> resp = post(http, baseUrl + "/players/register", body, null);
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("注册失败: HTTP " + resp.statusCode() + " " + resp.body());
        }
    }

    private void login(HttpClient http, String baseUrl, SimPlayer p) throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("id", p.id);
        body.put("password", p.password);

        HttpResponse<String> resp = post(http, baseUrl + "/players/login", body, null);
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("登录失败: HTTP " + resp.statusCode() + " " + resp.body());
        }
        String setCookie = resp.headers().firstValue("Set-Cookie")
                .orElseThrow(() -> new IllegalStateException("登录响应缺少 Set-Cookie"));
        assertNotNull(setCookie);
        p.jsessionid = setCookie.split(";", 2)[0]; // JSESSIONID=xxxx
    }

    private void join(HttpClient http, String baseUrl, SimPlayer p) throws Exception {
        HttpResponse<String> resp = post(http, baseUrl + "/match/join", null, p.jsessionid);
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("join 失败: HTTP " + resp.statusCode() + " " + resp.body());
        }
    }

    private void confirm(HttpClient http, String baseUrl, SimPlayer p) throws Exception {
        HttpResponse<String> resp = post(http, baseUrl + "/match/confirm", null, p.jsessionid);
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("confirm 失败: HTTP " + resp.statusCode() + " " + resp.body());
        }
    }

    private HttpResponse<String> post(HttpClient http, String url, ObjectNode body, String cookie) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15));
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        if (body == null) {
            builder.POST(HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)));
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    // ===== WebSocket =====

    private void websocketConnect(List<SimPlayer> players) throws Exception {
        StandardWebSocketClient client = new StandardWebSocketClient();
        for (SimPlayer p : players) {
            CompletableFuture<WebSocketSession> future = client.execute(new TextWebSocketHandler() {
                @Override
                protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                    p.inbox.add(message.getPayload());
                }
            }, "ws://localhost:" + port + "/ws/friend/" + p.id);
            p.ws = future.get(10, TimeUnit.SECONDS);
        }
    }

    /** 在超时窗口内等待指定类型的消息，其余类型（OPPONENT_CONFIRMED 等）忽略 */
    private JsonNode awaitMessage(SimPlayer p, String type, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                String raw = p.inbox.poll(200, TimeUnit.MILLISECONDS);
                if (raw == null) {
                    continue;
                }
                JsonNode node = MAPPER.readTree(raw);
                if (type.equals(node.path("type").asText(""))) {
                    return node;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                appendError(p, "消息解析失败: " + e.getMessage());
            }
        }
        return null;
    }

    // ===== 并发工具 =====

    @FunctionalInterface
    private interface ThrowingConsumer<T> {
        void accept(T t) throws Exception;
    }

    /** 所有任务在 CountDownLatch 上对齐后同一时刻并发执行 */
    private void runConcurrently(List<SimPlayer> players, ThrowingConsumer<SimPlayer> action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(players.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (SimPlayer p : players) {
                futures.add(pool.submit(() -> {
                    try {
                        start.await();
                        action.accept(p);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        appendError(p, e.getClass().getSimpleName() + ": " + e.getMessage());
                    }
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get(90, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static void appendError(SimPlayer p, String message) {
        synchronized (p) {
            p.error = (p.error == null) ? message : p.error + " | " + message;
        }
    }
}
