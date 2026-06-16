package com.example.combo.match.controller;

import com.example.combo.match.service.MatchService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/match")
@RequiredArgsConstructor
public class MatchController {
    private final MatchService matchService;

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
     * 查询匹配状态
     */
    @GetMapping("/status")
    public Map<String, Object> getMatchStatus(HttpSession session) {
        Long playerId = (Long) session.getAttribute("playerId");
        if (playerId == null) {
            throw new IllegalArgumentException("请先登录");
        }
        return matchService.getMatchStatus(playerId);
    }

    /**
     * 确认进入场景
     */
    @PostMapping("/confirm")
    public void confirmMatch(HttpSession session) {
        Long playerId = (Long) session.getAttribute("playerId");
        if (playerId == null) {
            throw new IllegalArgumentException("请先登录");
        }
        matchService.confirmMatch(playerId);
    }

    /**
     * 查询匹配池人数
     */
    @GetMapping("/pool")
    public int getPoolSize() {
        return matchService.getWaitingCount();
    }
}
