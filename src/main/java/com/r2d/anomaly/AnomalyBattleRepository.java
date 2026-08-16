package com.r2d.anomaly;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AnomalyBattleRepository extends JpaRepository<AnomalyBattle, Long> {

    Optional<AnomalyBattle> findByCellIdAndPlayerId(String cellId, Long playerId);
}
