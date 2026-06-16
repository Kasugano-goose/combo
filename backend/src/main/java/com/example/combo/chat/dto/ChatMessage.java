package com.example.combo.chat.dto;

import lombok.Data;

@Data
public class ChatMessage {
    private  Long fromPlayerId;
    private  Long toPlayerId;
    private String content;     // 聊天内容
    private String type;        // 消息类型: "CHAT" / "ERROR" / "SYSTEM"
    private String timestamp;   // 时间戳（出站时由服务端填入）
}
