package com.example.combo.friendship.controller;

import com.example.combo.common.exception.BusinessException;
import com.example.combo.friendship.dto.SendFriendRequest;
import com.example.combo.friendship.service.FriendshipService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

    private Long requireLogin(HttpSession session) {
        Long playerId = (Long) session.getAttribute("playerId");
        if (playerId == null) {
            throw new BusinessException("请先登录", HttpStatus.UNAUTHORIZED);
        }
        return playerId;
    }

    /**
     * 发送好友申请
     */
    @PostMapping("/requests")
    public void sendRequest(@Valid @RequestBody SendFriendRequest request, HttpSession session) {
        Long senderId = requireLogin(session);
        friendshipService.sendFriend(senderId, request.getReceiverId());
    }

    /**
     * 接受好友申请
     *
     * @param requestId 好友申请 ID
     */
    @PostMapping("/requests/{requestId}/accept")
    public void acceptRequest(@PathVariable("requestId") Long requestId, HttpSession session) {
        Long receiverId = requireLogin(session);
        friendshipService.acceptFriend(requestId, receiverId);
    }

    /**
     * 拒绝好友申请
     *
     * @param requestId 好友申请 ID
     */
    @PostMapping("/requests/{requestId}/reject")
    public void rejectRequest(@PathVariable Long requestId, HttpSession session) {
        Long receiverId = requireLogin(session);
        friendshipService.rejectFriend(requestId, receiverId);
    }

    /**
     * 查询好友列表
     */
    @GetMapping
    public List<Map<String, Object>> listFriends(HttpSession session) {
        Long playerId = requireLogin(session);
        return friendshipService.getFriendList(playerId);
    }

    /**
     * 查询待处理的好友申请
     */
    @GetMapping("/requests")
    public List<Map<String, Object>> listPendingRequests(HttpSession session) {
        Long playerId = requireLogin(session);
        return friendshipService.getPendingRequests(playerId);
    }
}
