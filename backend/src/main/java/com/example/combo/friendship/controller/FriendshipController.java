package com.example.combo.friendship.controller;

import com.example.combo.friendship.dto.SendFriendRequest;
import com.example.combo.friendship.service.FriendshipService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/friends")
@RequiredArgsConstructor
public class FriendshipController {
    private final FriendshipService friendshipService;

    @PostMapping("/requests")
    public void sendRequest(@Valid @RequestBody SendFriendRequest request, HttpSession session) {
        Long senderId = (Long) session.getAttribute("playerId");
        if (senderId == null) {
            throw new IllegalArgumentException("请先登录");
        }
        friendshipService.sendFriend(senderId, request.getReceiverId());
    }

    @PostMapping("/requests/{requestId}/accept")
    public void acceptRequest(@PathVariable("requestId") Long requestId, HttpSession session) {
        // 从 session 获取当前登录用户 ID（即被申请者）
        Long receiverId = (Long) session.getAttribute("playerId");
        if (receiverId == null) {
            throw new IllegalArgumentException("请先登录");
        }
        // 调用 Service 层接受好友申请
        friendshipService.acceptFriend(requestId, receiverId);
    }

    @PostMapping("/requests/{requestId}/reject") // 补全：拒绝好友申请接口
    public void rejectRequest(@PathVariable Long requestId, HttpSession session) { // 补全：添加session参数获取当前用户
        // 从 session 获取当前登录用户 ID（即被申请者） // 补全：参照acceptRequest获取用户ID
        Long receiverId = (Long) session.getAttribute("playerId"); // 补全：从session中取当前登录用户
        if (receiverId == null) { // 补全：未登录则抛异常
            throw new IllegalArgumentException("请先登录");
        }
        // 调用 Service 层拒绝好友申请 // 补全：调用service的rejectFriend方法
        friendshipService.rejectFriend(requestId, receiverId); // 补全：传入申请ID和当前用户ID
    }

    /**
     * 查询好友列表
     */
    @GetMapping
    public List<Map<String, Object>> listFriends(HttpSession session) {
        Long playerId = (Long) session.getAttribute("playerId");
        if (playerId == null) {
            throw new IllegalArgumentException("请先登录");
        }
        return friendshipService.getFriendList(playerId);
    }

    /**
     * 查询待处理的好友申请
     */
    @GetMapping("/requests")
    public List<Map<String, Object>> listPendingRequests(HttpSession session) {
        Long playerId = (Long) session.getAttribute("playerId");
        if (playerId == null) {
            throw new IllegalArgumentException("请先登录");
        }
        return friendshipService.getPendingRequests(playerId);
    }
}
