package com.example.combo.player.repository;

import com.example.combo.player.domain.Player;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    boolean existsByUsername(String username);

    boolean existsByPhone(String phone);

    boolean existsByIdCard(String idCard);
}
