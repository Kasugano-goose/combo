package com.example.combo.friendship.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SendFriendRequest {
    @NotNull(message = "接收者ID不能为空")
    private Long receiverId;
}
