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

        // 获取申请者信息（用于通知）
        Player sender = playerRepository.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("申请者不存在"));

        // 创建好友申请
        LocalDateTime now = LocalDateTime.now();
        Friendship friendship = Friendship.builder()
                .requesterId(senderId)
                .addresseeId(receiverId)
                .status(FriendshipStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();
        friendshipRepository.save(friendship);

        // ===== WebSocket 推送通知 =====
        // 只有 receiverId 才能收到这条通知
        String notification = buildFriendRequestNotification(sender, friendship);
        webSocketSessionManager.sendToUser(receiverId, notification);
    }

    @Transactional
    public void acceptFriend(Long friendshipId, Long receiverId) {
        // 1. 参数校验
        if (friendshipId == null) {
            throw new IllegalArgumentException("好友申请ID不能为空");
        }
        if (receiverId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }

        // 2. 查询好友申请记录
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new IllegalArgumentException("好友申请不存在"));

        // 3. 验证权限：只有被申请者才能接受
        if (!friendship.getAddresseeId().equals(receiverId)) {
            throw new IllegalArgumentException("无权操作此申请");
        }

        // 4. 验证状态
        if (friendship.getStatus() == FriendshipStatus.ACCEPTED) {
            throw new IllegalArgumentException("已经是好友");
        }
        if (friendship.getStatus() == FriendshipStatus.REJECTED) {
            throw new IllegalArgumentException("申请已被拒绝");
        }

        // 5. 更新状态
        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendship.setUpdatedAt(LocalDateTime.now());
        friendshipRepository.save(friendship);

        // ===== WebSocket 通知申请者 =====
        // 通知发起申请的用户，对方已接受
        @Transactional
        public void acceptFriend(Long friendshipId, Long receiverId) {
            // 1. 参数校验
            if (friendshipId == null) {
                throw new IllegalArgumentException("好友申请ID不能为空");
            }
            if (receiverId == null) {
                throw new IllegalArgumentException("用户ID不能为空");
            }

            // 2. 查询好友申请记录
            Friendship friendship = friendshipRepository.findById(friendshipId)
                    .orElseThrow(() -> new IllegalArgumentException("好友申请不存在"));

            // 3. 验证权限：只有被申请者才能接受
            if (!friendship.getAddresseeId().equals(receiverId)) {
                throw new IllegalArgumentException("无权操作此申请");
            }

            // 4. 验证状态
            if (friendship.getStatus() == FriendshipStatus.ACCEPTED) {
                throw new IllegalArgumentException("已经是好友");
            }
            if (friendship.getStatus() == FriendshipStatus.REJECTED) {
                throw new IllegalArgumentException("申请已被拒绝");
            }

            // 5. 更新状态
            friendship.setStatus(FriendshipStatus.ACCEPTED);
            friendship.setUpdatedAt(LocalDateTime.now());
            friendshipRepository.save(friendship);

            // ===== WebSocket 通知申请者 =====
            // 通知发起申请的用户，对方已接受
            Player acceptor = playerRepository.findById(receiverId)
                    .orElse(null);
            if (acceptor != null) {
                String notification = buildAcceptNotification(acceptor, friendship);
                webSocketSessionManager.sendToUser(friendship.getRequesterId(), notification);
            }
        }

        private boolean hasActiveFriendship(Long playerId, Long friendId) {
            List<FriendshipStatus> activeStatuses = List.of(FriendshipStatus.PENDING, FriendshipStatus.ACCEPTED);
            return friendshipRepository.existsByRequesterIdAndAddresseeIdAndStatusIn(playerId, friendId, activeStatuses)
                    || friendshipRepository.existsByRequesterIdAndAddresseeIdAndStatusIn(friendId, playerId, activeStatuses);
        }

    private boolean hasActiveFriendship(Long playerId, Long friendId) {
        List<FriendshipStatus> activeStatuses = List.of(FriendshipStatus.PENDING, FriendshipStatus.ACCEPTED);
        return friendshipRepository.existsByRequesterIdAndAddresseeIdAndStatusIn(playerId, friendId, activeStatuses)
                || friendshipRepository.existsByRequesterIdAndAddresseeIdAndStatusIn(friendId, playerId, activeStatuses);
    }

    @Transactional
    public void rejectFriend(Long friendshipId, Long receiverId) { // 补全：拒绝好友申请方法
        // 1. 参数校验 // 补全：校验好友申请ID和用户ID
        if (friendshipId == null) { // 补全：好友申请ID不能为空
            throw new IllegalArgumentException("好友申请ID不能为空");
        }
        if (receiverId == null) { // 补全：用户ID不能为空
            throw new IllegalArgumentException("用户ID不能为空");
        }

        // 2. 查询好友申请记录 // 补全：根据ID查找好友申请
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new IllegalArgumentException("好友申请不存在"));

        // 3. 验证权限：只有被申请者才能拒绝 // 补全：权限校验
        if (!friendship.getAddresseeId().equals(receiverId)) { // 补全：非被申请者无权拒绝
            throw new IllegalArgumentException("无权操作此申请");
        }

        // 4. 验证状态 // 补全：检查当前申请状态是否允许拒绝
        if (friendship.getStatus() == FriendshipStatus.ACCEPTED) { // 补全：已是好友不能拒绝
            throw new IllegalArgumentException("已经是好友，无法拒绝");
        }
        if (friendship.getStatus() == FriendshipStatus.REJECTED) { // 补全：已拒绝不能重复拒绝
            throw new IllegalArgumentException("申请已被拒绝");
        }

        // 5. 更新状态为已拒绝 // 补全：设置REJECTED状态并保存
        friendship.setStatus(FriendshipStatus.REJECTED); // 补全：将状态设为REJECTED
        friendship.setUpdatedAt(LocalDateTime.now()); // 补全：更新修改时间
        friendshipRepository.save(friendship); // 补全：持久化到数据库

        // ===== WebSocket 通知申请者 ===== // 补全：通知发起申请的用户被拒绝
        Player rejector = playerRepository.findById(receiverId) // 补全：获取拒绝者信息
                .orElse(null);
        if (rejector != null) { // 补全：拒绝者存在时发送通知
            String notification = buildRejectNotification(rejector, friendship); // 补全：构建拒绝通知消息
            webSocketSessionManager.sendToUser(friendship.getRequesterId(), notification); // 补全：推送给申请者
        }
    }

    /**
     * 查询待处理的好友申请（别人发给我的）
     */
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

    /**
     * 查询好友列表（已接受的好友）
     */
    public List<Map<String, Object>> getFriendList(Long playerId) {
        // 我发起的、已接受的
        List<Friendship> sent = friendshipRepository
                .findByRequesterIdAndStatusOrderByUpdatedAtDesc(playerId, FriendshipStatus.ACCEPTED);
        // 我收到的、已接受的
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

    private Map<String, Object> buildFriendInfo(Friendship f, Player friend) {
        Map<String, Object> item = new HashMap<>();
        item.put("friendshipId", f.getId());
        item.put("playerId", friend.getId());
        item.put("username", friend.getUsername());
        item.put("rankScore", friend.getRankScore());
        item.put("status", friend.getStatus().name());
        return item;
    }

    /**
     * 构建好友申请通知消息（JSON格式）
     */
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

    /**
     * 构建拒绝好友通知消息（JSON格式） // 补全：拒绝通知消息构建方法
     */
    private String buildRejectNotification(Player rejector, Friendship friendship) { // 补全：构建拒绝通知
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

    /**
     * 构建接受好友通知消息（JSON格式）
     */
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
