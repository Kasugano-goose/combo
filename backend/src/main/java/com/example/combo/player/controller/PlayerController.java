package com.example.combo.player.controller;

import com.example.combo.common.exception.BusinessException;
import com.example.combo.player.domain.Player;
import com.example.combo.player.dto.ChangePasswordRequest;
import com.example.combo.player.dto.LoginPlayerRequest;
import com.example.combo.player.dto.PlayerResponse;
import com.example.combo.player.dto.RegisterPlayerRequest;
import com.example.combo.player.dto.SelectRoleRequest;
import com.example.combo.player.dto.UpdateProfileRequest;
import com.example.combo.player.service.PlayerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/players")
@RequiredArgsConstructor
public class PlayerController {

    private final PlayerService playerService;

    @PostMapping("/register")
    public PlayerResponse register(@Valid @RequestBody RegisterPlayerRequest request) {
        log.info("收到注册请求: id={}, username={}", request.getId(), request.getUsername());
        Player player = playerService.register(request);
        return PlayerResponse.from(player);
    }

    @PostMapping("/login")
    public PlayerResponse login(@Valid @RequestBody LoginPlayerRequest request,
                                HttpServletRequest httpRequest, HttpSession session) {
        Player player = playerService.login(request);
        // 防御会话固定攻击：认证成功后轮换 session id
        httpRequest.changeSessionId();
        session.setAttribute("playerId", player.getId());
        return PlayerResponse.from(player);
    }
    @PostMapping("/logout")
    public void logout(HttpSession session){
        session.invalidate();
    }


    @GetMapping("/me")
    public PlayerResponse me(HttpSession session) {
        Long playerId = (Long) session.getAttribute("playerId");
        if (playerId == null) {
            throw new BusinessException("请先登录", HttpStatus.UNAUTHORIZED);
        }
        Player player = playerService.findById(playerId);
        return PlayerResponse.from(player);
    }

    @PutMapping("/me")
    public PlayerResponse updateProfile(@Valid @RequestBody UpdateProfileRequest request,
                                        HttpSession session) {
        Long playerId = (Long) session.getAttribute("playerId");
        if (playerId == null) {
            throw new BusinessException("请先登录", HttpStatus.UNAUTHORIZED);
        }
        Player player = playerService.updateProfile(playerId, request);
        return PlayerResponse.from(player);
    }

    @PutMapping("/me/password")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request,
                               HttpSession session) {
        Long playerId = (Long) session.getAttribute("playerId");
        if (playerId == null) {
            throw new BusinessException("请先登录", HttpStatus.UNAUTHORIZED);
        }
        playerService.changePassword(playerId, request);
    }

    @PutMapping("/me/role")
    public PlayerResponse selectRole(@Valid @RequestBody SelectRoleRequest request,
                                     HttpSession session) {
        Long playerId = (Long) session.getAttribute("playerId");
        if (playerId == null) {
            throw new BusinessException("请先登录", HttpStatus.UNAUTHORIZED);
        }
        Player player = playerService.selectRole(playerId, request.getRoleId());
        return PlayerResponse.from(player);
    }

    @DeleteMapping("/me")
    public void disableAccount(HttpSession session) {
        Long playerId = (Long) session.getAttribute("playerId");
        if (playerId == null) {
            throw new BusinessException("请先登录", HttpStatus.UNAUTHORIZED);
        }
        playerService.disablePlayer(playerId);
        session.invalidate();
    }
}
