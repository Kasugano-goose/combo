package com.example.combo.player.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class SelectRoleRequest {

    @NotNull(message = "角色ID不能为空")
    @Positive(message = "角色ID必须为正整数")
    private Long roleId;
}
