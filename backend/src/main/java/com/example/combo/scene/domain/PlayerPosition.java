package com.example.combo.scene.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerPosition {
    private Long playerId;
    private String playerName;
    private Long roleId;
    private volatile int x;
    private volatile int y;
    private volatile String direction;   // UP / DOWN / LEFT / RIGHT / STOP
}
