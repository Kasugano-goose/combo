package com.example.combo.chat.service;

import com.example.combo.chat.dto.ChatMessage;
import com.example.combo.common.websocket.WebSocketSessionManager;
import com.example.combo.friendship.domain.Friendship.FriendshipStatus;
import com.example.combo.friendship.repository.FriendshipRepository;
import com.example.combo.player.domain.Player;
import com.example.combo.player.repository.PlayerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {
    private final FriendshipRepository friendshipRepository;
    private final WebSocketSessionManager webSocketSessionManager;
    private final PlayerRepository playerRepository;
    private final ObjectMapper objectMapper;

    /**
     * 处理聊天消息：校验 → 好友判断 → 转发
     */
    public void handleMessage(Long fromPlayerId, String rawMessage) {
        // 1. 反序列化消息
        ChatMessage message;
        try {
            message = objectMapper.readValue(rawMessage, ChatMessage.class);
        } catch (Exception e) {
            sendError(fromPlayerId, "消息格式错误");
            return;
        }

        Long toPlayerId = message.getToPlayerId();
        String content = message.getContent();

        // 2. 参数校验
        if (toPlayerId == null) {
            sendError(fromPlayerId, "接收者ID不能为空");
            return;
        }
        if (content == null || content.trim().isEmpty()) {
            sendError(fromPlayerId, "消息内容不能为空");
            return;
        }

        // 3. 不能给自己发消息
        if (fromPlayerId.equals(toPlayerId)) {
            sendError(fromPlayerId, "不能给自己发消息");
            return;
        }

        // 4. 校验对方玩家是否存在
        if (!playerRepository.existsById(toPlayerId)) {
            sendError(fromPlayerId, "对方玩家不存在");
            return;
        }

        // 5. 校验是否为好友
        if (!areFriends(fromPlayerId, toPlayerId)) {
            sendError(fromPlayerId, "对方不是你的好友，无法发送消息");
            return;
        }

        // 6. 对方是否在线
        if (!webSocketSessionManager.isOnline(toPlayerId)) {
            sendError(fromPlayerId, "对方不在线，无法发送消息");
            return;
        }

        // 7. 构建转发消息并发送给接收者
        Player sender = playerRepository.findById(fromPlayerId).orElse(null);
        String fromUsername = (sender != null) ? sender.getUsername() : "未知玩家";

        ChatMessage forwardMessage = new ChatMessage();
        forwardMessage.setType("CHAT");
        forwardMessage.setFromPlayerId(fromPlayerId);
        forwardMessage.setToPlayerId(toPlayerId);
        forwardMessage.setContent(content);
        forwardMessage.setTimestamp(LocalDateTime.now().toString());

        try {
            String json = objectMapper.writeValueAsString(forwardMessage);
            webSocketSessionManager.sendToUser(toPlayerId, json);
            log.info("消息转发: 玩家{} → 玩家{}", fromPlayerId, toPlayerId);
        } catch (Exception e) {
            log.error("消息转发失败", e);
            sendError(fromPlayerId, "消息发送失败");
        }
    }

    /**
     * 判断两个玩家是否为好友（双向查询）
     */
    public boolean areFriends(Long playerId1, Long playerId2) {
        List<FriendshipStatus> accepted = List.of(FriendshipStatus.ACCEPTED);
        return friendshipRepository.existsByRequesterIdAndAddresseeIdAndStatusIn(playerId1, playerId2, accepted)
                || friendshipRepository.existsByRequesterIdAndAddresseeIdAndStatusIn(playerId2, playerId1, accepted);
    }

    /**
     * 向指定玩家发送错误消息
     */
    private void sendError(Long playerId, String errorMsg) {
        try {
            ChatMessage errorMessage = new ChatMessage();
            errorMessage.setType("ERROR");
            errorMessage.setContent(errorMsg);
            errorMessage.setTimestamp(LocalDateTime.now().toString());
            String json = objectMapper.writeValueAsString(errorMessage);
            webSocketSessionManager.sendToUser(playerId, json);
        } catch (Exception e) {
            log.error("发送错误消息失败: {}", errorMsg, e);
        }
    }
}
