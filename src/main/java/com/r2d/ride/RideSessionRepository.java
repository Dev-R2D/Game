package com.r2d.ride;

import java.util.List;

import com.r2d.domain.RideStatus;
import com.r2d.player.Player;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RideSessionRepository extends JpaRepository<RideSession, String> {

    List<RideSession> findByPlayerOrderByStartedAtDesc(Player player);

    List<RideSession> findByPlayerAndStatus(Player player, RideStatus status);
}
