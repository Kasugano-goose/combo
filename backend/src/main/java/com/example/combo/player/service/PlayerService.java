package com.example.combo.player.service;

import com.example.combo.common.exception.BusinessException;
import com.example.combo.player.domain.Player;
import com.example.combo.player.dto.ChangePasswordRequest;
import com.example.combo.player.dto.LoginPlayerRequest;
import com.example.combo.player.dto.RegisterPlayerRequest;
import com.example.combo.player.dto.SelectRoleRequest;
import com.example.combo.player.dto.UpdateProfileRequest;
import com.example.combo.player.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerService {

    private static final long DEFAULT_RANK_ID = 1L;

    private final PlayerRepository playerRepository;

    /**
     * 判断两个玩家是否可以匹配
     * 规则：段位等级差不超过1，积分差不超过300
     */
    public boolean canMatchWith(Player player, Player opponent) {
        if (player == null || opponent == null) {
            return false;
        }

        Player.PlayerRank currentRank = player.getCurrentRank();
        Player.PlayerRank opponentRank = opponent.getCurrentRank();
        int rankGap = Math.abs(currentRank.getLevel() - opponentRank.getLevel());

        if (rankGap > 1) {
            return false;
        }

        return Math.abs(getMatchScore(player) - getMatchScore(opponent)) <= 300;
    }

    /**
     * 获取玩家的匹配分数
     */
    public int getMatchScore(Player player) {
        return player.getRankScore() == null
                ? player.getCurrentRank().getMinScore()
                : player.getRankScore();
    }

    public Player findById(long id) {
        return playerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("玩家不存在"));
    }

    public Player register(RegisterPlayerRequest request) {
        validateRegisterRequest(request);

        LocalDateTime now = LocalDateTime.now();
        String hashedPassword = BCrypt.hashpw(request.getPassword(), BCrypt.gensalt());

        Player player = Player.builder()
                .id(request.getId())
                .username(request.getUsername())
                .password(hashedPassword)
                .realName(request.getRealName())
                .phone(request.getPhone())
                .idCard(request.getIdCard())
                .balance(BigDecimal.ZERO)
                .status(Player.PlayerStatus.NORMAL)
                .rank(Player.PlayerRank.BRONZE)
                .rankId(DEFAULT_RANK_ID)
                .rankScore(0)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return playerRepository.save(player);
    }

    public Player login(LoginPlayerRequest request) {
        Player player = playerRepository.findById(request.getId())
                .orElseThrow(() -> new BusinessException("账号ID或密码不正确"));

        if (!BCrypt.checkpw(request.getPassword(), player.getPassword())) {
            // 兼容旧的明文密码：如果 BCrypt 校验失败但明文匹配，则自动加密迁移
            if (request.getPassword().equals(player.getPassword())) {
                log.info("检测到明文密码，自动加密迁移: playerId={}", player.getId());
                player.setPassword(BCrypt.hashpw(request.getPassword(), BCrypt.gensalt()));
                playerRepository.save(player);
            } else {
                throw new BusinessException("账号ID或密码不正确");
            }
        }

        return player;
    }

    /**
     * 更新个人资料
     */
    public Player updateProfile(Long playerId, UpdateProfileRequest request) {
        Player player = findById(playerId);

        // 校验唯一性（排除自身）
        if (!player.getUsername().equals(request.getUsername())
                && playerRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("用户名已存在");
        }
        if (!player.getPhone().equals(request.getPhone())
                && playerRepository.existsByPhone(request.getPhone())) {
            throw new BusinessException("手机号已存在");
        }
        if (!player.getIdCard().equals(request.getIdCard())
                && playerRepository.existsByIdCard(request.getIdCard())) {
            throw new BusinessException("身份证号已存在");
        }

        player.setUsername(request.getUsername());
        player.setRealName(request.getRealName());
        player.setPhone(request.getPhone());
        player.setIdCard(request.getIdCard());
        player.setUpdatedAt(LocalDateTime.now());

        return playerRepository.save(player);
    }

    /**
     * 修改密码
     */
    public void changePassword(Long playerId, ChangePasswordRequest request) {
        Player player = findById(playerId);

        if (!BCrypt.checkpw(request.getOldPassword(), player.getPassword())) {
            throw new BusinessException("旧密码不正确");
        }

        player.setPassword(BCrypt.hashpw(request.getNewPassword(), BCrypt.gensalt()));
        player.setUpdatedAt(LocalDateTime.now());
        playerRepository.save(player);
    }

    /**
     * 选择角色
     */
    public Player selectRole(Long playerId, Long roleId) {
        Player player = findById(playerId);
        player.setSelectedRoleId(roleId);
        player.setUpdatedAt(LocalDateTime.now());
        return playerRepository.save(player);
    }

    /**
     * 停用账号（软删除）
     */
    public void disablePlayer(Long playerId) {
        Player player = findById(playerId);
        player.setStatus(Player.PlayerStatus.DISABLED);
        player.setUpdatedAt(LocalDateTime.now());
        playerRepository.save(player);
    }

    private void validateRegisterRequest(RegisterPlayerRequest request) {
        if (playerRepository.existsById(request.getId())) {
            throw new BusinessException("账号ID已被占用");
        }
        if (playerRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("用户名已存在");
        }
        if (playerRepository.existsByPhone(request.getPhone())) {
            throw new BusinessException("手机号已存在");
        }
        if (playerRepository.existsByIdCard(request.getIdCard())) {
            throw new BusinessException("身份证号已存在");
        }
    }
}
