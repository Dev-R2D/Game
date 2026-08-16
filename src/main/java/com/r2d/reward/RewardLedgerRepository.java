package com.r2d.reward;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RewardLedgerRepository extends JpaRepository<RewardLedger, Long> {

    Optional<RewardLedger> findByPlayerId(Long playerId);
}
