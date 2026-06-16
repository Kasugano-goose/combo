package com.example.combo.match.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchResult {
    private Long playerId;
    private String username;
    private Integer rankScore;
    private Integer rankLevel;
    private Long selectedRoleId;
}
