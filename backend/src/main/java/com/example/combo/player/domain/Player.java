package com.example.combo.player.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@ToString(exclude = "password")
@EqualsAndHashCode(of = "id")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "player")
public class Player {

    @Id
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(name = "real_name", nullable = false, length = 64)
    private String realName;

    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    @Column(name = "id_card", nullable = false, unique = true, length = 32)
    private String idCard;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlayerStatus status;

    @Transient
    private PlayerRank rank;

    @Column(name = "rank_id", nullable = false)
    private Long rankId;

    @Column(name = "rank_score", nullable = false)
    private Integer rankScore;

    @Column(name = "selected_role_id")
    private Long selectedRoleId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public PlayerRank getCurrentRank() {
        if (rankScore == null) {
            return rank == null ? PlayerRank.BRONZE : rank;
        }
        return PlayerRank.fromScore(rankScore);
    }

    public enum PlayerStatus {
        NORMAL,
        DISABLED
    }

    public enum PlayerRank {
        BRONZE(1, 0, 999),
        SILVER(2, 1000, 1999),
        GOLD(3, 2000, 2999),
        PLATINUM(4, 3000, 3999),
        DIAMOND(5, 4000, 4999),
        MASTER(6, 5000, Integer.MAX_VALUE);

        private final int level;
        private final int minScore;
        private final int maxScore;

        PlayerRank(int level, int minScore, int maxScore) {
            this.level = level;
            this.minScore = minScore;
            this.maxScore = maxScore;
        }

        public int getLevel() {
            return level;
        }

        public int getMinScore() {
            return minScore;
        }

        public int getMaxScore() {
            return maxScore;
        }

        public static PlayerRank fromScore(int score) {
            for (PlayerRank rank : values()) {
                if (score >= rank.minScore && score <= rank.maxScore) {
                    return rank;
                }
            }
            return BRONZE;
        }
    }
}
