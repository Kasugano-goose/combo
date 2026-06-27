package com.example.combo.match.controller;

import com.example.combo.common.exception.BusinessException;
import com.example.combo.match.service.MatchService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
     * 从 Session 中获取 playerId（写操作严格要求 session，不接受参数兜底）
     * 只有查询类接口 getMatchStatus 才允许 paramPlayerId 兜底。
     */
    private Long requireLogin(HttpSession session) {
        Long playerId = (Long) session.getAttribute("playerId");
        if (playerId == null) {
            throw new BusinessException("请先登录", HttpStatus.UNAUTHORIZED);
        }
        return playerId;
    }

    /**
     * 从 Session 或请求参数中获取 playerId（双重兜底，仅查询接口使用）
     * 写操作（join/leave/confirm）必须使用 requireLogin() 而非此方法，
     * 防止 Session 丢失时参数被伪造。
     */
    private Long resolvePlayerId(HttpSession session, Long paramPlayerId) {
        Long playerId = (Long) session.getAttribute("playerId");
        if (playerId == null && paramPlayerId != null) {
            // Session 丢失时，从请求参数取（仅用于查询类接口，不可用于写操作）
            playerId = paramPlayerId;
        }
        if (playerId == null) {
            throw new BusinessException("请先登录", HttpStatus.UNAUTHORIZED);
        }
        return playerId;
    }

    /**
     * 加入匹配池
     */
    @PostMapping("/join")
    public Map<String, Object> joinMatch(HttpSession session) {
        Long playerId = requireLogin(session);
        matchService.joinMatch(playerId);
        return matchService.getMatchStatus(playerId);
    }

    /**
     * 退出匹配池
     */
    @PostMapping("/leave")
    public void leaveMatch(HttpSession session) {
        Long playerId = requireLogin(session);
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
        Long playerId = requireLogin(session);
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
