package com.r2d.player;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    Optional<Player> findByPublicId(String publicId);

    boolean existsByNickname(String nickname);
}
