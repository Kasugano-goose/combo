package com.example.combo.friendship.service;

import com.example.combo.common.websocket.WebSocketSessionManager;
import com.example.combo.friendship.domain.Friendship;
import com.example.combo.friendship.domain.Friendship.FriendshipStatus;
import com.example.combo.friendship.repository.FriendshipRepository;
import com.example.combo.player.domain.Player;
import com.example.combo.player.repository.PlayerRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FriendshipService {
    private final PlayerRepository playerRepository;
    private final FriendshipRepository friendshipRepository;
    private final WebSocketSessionManager webSocketSessionManager;

    @Transactional
    public void sendFriend(Long senderId, Long receiverId) {
        if (senderId == null) {
            throw new IllegalArgumentException("请先登录");
        }
        if (receiverId == null) {
            throw new IllegalArgumentException("好友ID不能为空");
        }
        if (senderId.equals(receiverId)) {
            throw new IllegalArgumentException("不允许添加自己为好友");
        }
        if (!playerRepository.existsById(receiverId)) {
            throw new IllegalArgumentException("请输入有效的ID");
        }
        if (hasActiveFriendship(senderId, receiverId)) {
            throw new IllegalArgumentException("好友申请已存在或已经是好友");
        }

        Player sender = playerRepository.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("申请者不存在"));

        LocalDateTime now = LocalDateTime.now();
        Friendship friendship = Friendship.builder()
                .requesterId(senderId)
                .addresseeId(receiverId)
                .status(FriendshipStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();
        friendshipRepository.save(friendship);

        String notification = buildFriendRequestNotification(sender, friendship);
        webSocketSessionManager.sendToUser(receiverId, notification);
    }

    @Transactional
    public void acceptFriend(Long friendshipId, Long receiverId) {
        if (friendshipId == null) {
            throw new IllegalArgumentException("好友申请ID不能为空");
        }
        if (receiverId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }

        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new IllegalArgumentException("好友申请不存在"));

        if (!friendship.getAddresseeId().equals(receiverId)) {
            throw new IllegalArgumentException("无权操作此申请");
        }

        if (friendship.getStatus() == FriendshipStatus.ACCEPTED) {
            throw new IllegalArgumentException("已经是好友");
        }
        if (friendship.getStatus() == FriendshipStatus.REJECTED) {
            throw new IllegalArgumentException("申请已被拒绝");
        }

        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendship.setUpdatedAt(LocalDateTime.now());
        friendshipRepository.save(friendship);

        Player acceptor = playerRepository.findById(receiverId).orElse(null);
        if (acceptor != null) {
            String notification = buildAcceptNotification(acceptor, friendship);
            webSocketSessionManager.sendToUser(friendship.getRequesterId(), notification);
        }
    }

    @Transactional
    public void rejectFriend(Long friendshipId, Long receiverId) {
        if (friendshipId == null) {
            throw new IllegalArgumentException("好友申请ID不能为空");
        }
        if (receiverId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }

        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new IllegalArgumentException("好友申请不存在"));

        if (!friendship.getAddresseeId().equals(receiverId)) {
            throw new IllegalArgumentException("无权操作此申请");
        }

        if (friendship.getStatus() == FriendshipStatus.ACCEPTED) {
            throw new IllegalArgumentException("已经是好友，无法拒绝");
        }
        if (friendship.getStatus() == FriendshipStatus.REJECTED) {
            throw new IllegalArgumentException("申请已被拒绝");
        }

        friendship.setStatus(FriendshipStatus.REJECTED);
        friendship.setUpdatedAt(LocalDateTime.now());
        friendshipRepository.save(friendship);

        Player rejector = playerRepository.findById(receiverId).orElse(null);
        if (rejector != null) {
            String notification = buildRejectNotification(rejector, friendship);
            webSocketSessionManager.sendToUser(friendship.getRequesterId(), notification);
        }
    }

    public List<Map<String, Object>> getPendingRequests(Long playerId) {
        List<Friendship> received = friendshipRepository
                .findByAddresseeIdAndStatusOrderByUpdatedAtDesc(playerId, FriendshipStatus.PENDING);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Friendship f : received) {
            Player requester = playerRepository.findById(f.getRequesterId()).orElse(null);
            Map<String, Object> item = new HashMap<>();
            item.put("friendshipId", f.getId());
            item.put("requesterId", f.getRequesterId());
            item.put("requesterName", requester != null ? requester.getUsername() : "未知");
            item.put("createdAt", f.getCreatedAt().toString());
            result.add(item);
        }
        return result;
    }

    public List<Map<String, Object>> getFriendList(Long playerId) {
        List<Friendship> sent = friendshipRepository
                .findByRequesterIdAndStatusOrderByUpdatedAtDesc(playerId, FriendshipStatus.ACCEPTED);
        List<Friendship> received = friendshipRepository
                .findByAddresseeIdAndStatusOrderByUpdatedAtDesc(playerId, FriendshipStatus.ACCEPTED);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Friendship f : sent) {
            Player friend = playerRepository.findById(f.getAddresseeId()).orElse(null);
            if (friend != null) {
                result.add(buildFriendInfo(f, friend));
            }
        }
        for (Friendship f : received) {
            Player friend = playerRepository.findById(f.getRequesterId()).orElse(null);
            if (friend != null) {
                result.add(buildFriendInfo(f, friend));
            }
        }
        return result;
    }

    private boolean hasActiveFriendship(Long playerId, Long friendId) {
        List<FriendshipStatus> activeStatuses = List.of(FriendshipStatus.PENDING, FriendshipStatus.ACCEPTED);
        return friendshipRepository.existsByRequesterIdAndAddresseeIdAndStatusIn(playerId, friendId, activeStatuses)
                || friendshipRepository.existsByRequesterIdAndAddresseeIdAndStatusIn(friendId, playerId, activeStatuses);
    }

    private Map<String, Object> buildFriendInfo(Friendship f, Player friend) {
        Map<String, Object> item = new HashMap<>();
        item.put("friendshipId", f.getId());
        item.put("playerId", friend.getId());
        item.put("username", friend.getUsername());
        item.put("rankScore", friend.getRankScore());
        item.put("status", friend.getStatus().name());
        return item;
    }

    private String buildFriendRequestNotification(Player sender, Friendship friendship) {
        return String.format("""
                {
                    "type": "FRIEND_REQUEST",
                    "friendshipId": %d,
                    "fromUserId": %d,
                    "fromUsername": "%s",
                    "message": "%s 请求添加你为好友"
                }
                """,
                friendship.getId(),
                sender.getId(),
                sender.getUsername(),
                sender.getUsername()
        );
    }

    private String buildRejectNotification(Player rejector, Friendship friendship) {
        return String.format("""
                {
                    "type": "FRIEND_REJECTED",
                    "friendshipId": %d,
                    "fromUserId": %d,
                    "fromUsername": "%s",
                    "message": "%s 拒绝了你的好友申请"
                }
                """,
                friendship.getId(),
                rejector.getId(),
                rejector.getUsername(),
                rejector.getUsername()
        );
    }

    private String buildAcceptNotification(Player acceptor, Friendship friendship) {
        return String.format("""
                {
                    "type": "FRIEND_ACCEPTED",
                    "friendshipId": %d,
                    "fromUserId": %d,
                    "fromUsername": "%s",
                    "message": "%s 已接受你的好友申请"
                }
                """,
                friendship.getId(),
                acceptor.getId(),
                acceptor.getUsername(),
                acceptor.getUsername()
        );
    }
}
