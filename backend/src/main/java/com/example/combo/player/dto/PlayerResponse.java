package com.example.combo.player.dto;

import com.example.combo.player.domain.Player;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PlayerResponse {

    private Long id;

    private String username;

    private String realName;

    private String phone;

    private BigDecimal balance;

    private Player.PlayerStatus status;

    private Player.PlayerRank rank;

    private Integer rankScore;

    private Long selectedRoleId;

    public static PlayerResponse from(Player player) {
        return PlayerResponse.builder()
                .id(player.getId())
                .username(player.getUsername())
                .realName(player.getRealName())
                .phone(player.getPhone())
                .balance(player.getBalance())
                .status(player.getStatus())
                .rank(player.getCurrentRank())
                .rankScore(player.getRankScore())
                .selectedRoleId(player.getSelectedRoleId())
                .build();
    }
}
