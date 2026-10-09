package com.example.combo.player.service;

import com.example.combo.player.domain.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 匹配规则单元测试：段位等级差 <= 1 且积分差 <= 300
 *
 * canMatchWith / getMatchScore 不依赖仓储，直接实例化 PlayerService 即可测试。
 */
class PlayerMatchRuleTest {

    private final PlayerService playerService = new PlayerService(null);

    private Player player(long id, Integer rankScore) {
        return Player.builder().id(id).username("p" + id).rankScore(rankScore).build();
    }

    @Test
    @DisplayName("同段位同积分可以匹配")
    void sameRankAndScore() {
        assertTrue(playerService.canMatchWith(player(1, 1500), player(2, 1500)));
    }

    @Test
    @DisplayName("相邻段位、积分差恰好 300（边界）可以匹配")
    void adjacentRankAtScoreGapBoundary() {
        // 900 -> 青铜(level 1)，1200 -> 白银(level 2)，段位差 1、积分差 300
        assertTrue(playerService.canMatchWith(player(1, 900), player(2, 1200)));
    }

    @Test
    @DisplayName("相邻段位但积分差 301 不可以匹配")
    void adjacentRankScoreGapExceeded() {
        assertFalse(playerService.canMatchWith(player(1, 900), player(2, 1201)));
    }

    @Test
    @DisplayName("段位差 2 级即使积分差很小也不能匹配")
    void rankGapTooLarge() {
        // 999 -> 青铜(level 1)，2000 -> 黄金(level 3)，段位差 2
        assertFalse(playerService.canMatchWith(player(1, 999), player(2, 2000)));
    }

    @Test
    @DisplayName("rankScore 为 null 时按所在段位最低分处理")
    void nullScoreTreatedAsRankMinScore() {
        // null -> 青铜，按最低分 0 参与积分差计算
        assertTrue(playerService.canMatchWith(player(1, null), player(2, 300)));   // 差 300，边界内
        assertFalse(playerService.canMatchWith(player(1, null), player(2, 400)));  // 差 400，超限
    }

    @Test
    @DisplayName("空玩家不可匹配")
    void nullPlayersNeverMatch() {
        assertFalse(playerService.canMatchWith(null, player(2, 100)));
        assertFalse(playerService.canMatchWith(player(1, 100), null));
        assertFalse(playerService.canMatchWith(null, null));
    }
}
