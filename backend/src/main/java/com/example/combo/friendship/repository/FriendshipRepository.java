package com.example.combo.friendship.repository;

import com.example.combo.friendship.domain.Friendship;
import com.example.combo.friendship.domain.Friendship.FriendshipStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    boolean existsByRequesterIdAndAddresseeIdAndStatusIn(
            Long requesterId,
            Long addresseeId,
            Collection<FriendshipStatus> statuses);

    Optional<Friendship> findByIdAndAddresseeIdAndStatus(
            Long id,
            Long addresseeId,
            FriendshipStatus status);

    List<Friendship> findByRequesterIdAndStatusOrderByUpdatedAtDesc(
            Long requesterId,
            FriendshipStatus status);

    List<Friendship> findByAddresseeIdAndStatusOrderByUpdatedAtDesc(
            Long addresseeId,
            FriendshipStatus status);
}
