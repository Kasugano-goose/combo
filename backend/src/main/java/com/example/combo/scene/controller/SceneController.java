package com.example.combo.scene.controller;

import com.example.combo.scene.service.SceneService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scene")
@RequiredArgsConstructor
public class SceneController {
    private final SceneService sceneService;

    /**
     * 主动退出场景
     */
    @PostMapping("/exit")
    public void exitScene(HttpSession session) {
        Long playerId = (Long) session.getAttribute("playerId");
        if (playerId == null) {
            throw new IllegalArgumentException("请先登录");
        }
        sceneService.handleExit(playerId);
    }

    /**
     * 查询当前活跃场景数
     */
    @GetMapping("/count")
    public int getActiveSceneCount() {
        return sceneService.getActiveSceneCount();
    }
}
