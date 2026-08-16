package com.r2d.reward;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExplorationPackRepository extends JpaRepository<ExplorationPack, Long> {

    Optional<ExplorationPack> findByRideId(String rideId);

    List<ExplorationPack> findByPlayerIdAndOpenedFalse(Long playerId);
}
