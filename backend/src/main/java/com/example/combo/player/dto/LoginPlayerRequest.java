package com.example.combo.player.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class LoginPlayerRequest {

    @NotNull(message = "账号ID不能为空")
    @Positive(message = "账号ID必须为正整数")
    private Long id;

    @NotBlank(message = "密码不能为空")
    private String password;
}
