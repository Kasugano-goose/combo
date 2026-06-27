package com.example.combo.match.controller;

import com.example.combo.match.service.MatchService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/match")
@RequiredArgsConstructor
public class MatchController {
    private final MatchService matchService;

    /**
     * 从 Session 或请求参数中获取 playerId（双重兜底）
     */
    private Long resolvePlayerId(HttpSession session, Long paramPlayerId) {
        Long playerId = (Long) session.getAttribute("playerId");
        if (playerId == null && paramPlayerId != null) {
            // Session 丢失时，从请求参数取（仅用于查询类接口）
            playerId = paramPlayerId;
        }
        if (playerId == null) {
            throw new IllegalArgumentException("请先登录");
        }
        return playerId;
    }

    /**
     * 加入匹配池
     */
    @PostMapping("/join")
    public Map<String, Object> joinMatch(HttpSession session) {
        Long playerId = (Long) session.getAttribute("playerId");
        if (playerId == null) {
            throw new IllegalArgumentException("请先登录");
        }
        matchService.joinMatch(playerId);
        return matchService.getMatchStatus(playerId);
    }

    /**
     * 退出匹配池
     */
    @PostMapping("/leave")
    public void leaveMatch(HttpSession session) {
        Long playerId = (Long) session.getAttribute("playerId");
        if (playerId == null) {
            throw new IllegalArgumentException("请先登录");
        }
        matchService.leaveMatch(playerId);
    }

    /**
     * 查询匹配状态（支持从请求参数取 playerId，解决轮询时 Session 丢失问题）
     */
    @GetMapping("/status")
    public Map<String, Object> getMatchStatus(HttpSession session,
                                              @RequestParam(required = false) Long playerId) {
        Long resolvedId = resolvePlayerId(session, playerId);
        return matchService.getMatchStatus(resolvedId);
    }

    /**
     * 确认进入场景
     */
    @PostMapping("/confirm")
    public Map<String, Object> confirmMatch(HttpSession session) {
        Long playerId = (Long) session.getAttribute("playerId");
        if (playerId == null) {
            throw new IllegalArgumentException("请先登录");
        }
        return matchService.confirmMatch(playerId);
    }

    /**
     * 查询匹配池人数
     */
    @GetMapping("/pool")
    public int getPoolSize() {
        return matchService.getWaitingCount();
    }
}
