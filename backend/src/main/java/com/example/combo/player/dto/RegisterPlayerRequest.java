package com.example.combo.player.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterPlayerRequest {

    @NotNull(message = "账号ID不能为空")
    @Positive(message = "账号ID必须为正整数")
    private Long id;

    @NotBlank(message = "密码不能为空")
    @Size(min = 4, max = 32, message = "密码长度为4-32位")
    private String password;

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "真实姓名不能为空")
    private String realName;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "身份证号不能为空")
    private String idCard;
}
