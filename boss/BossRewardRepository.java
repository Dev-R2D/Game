package com.r2d.boss;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BossRewardRepository extends JpaRepository<BossReward, Long> {

    List<BossReward> findByPlayerIdAndClaimedFalseOrderByCreatedAtDesc(Long playerId);

    List<BossReward> findByPlayerIdOrderByCreatedAtDesc(Long playerId);

    List<BossReward> findByBossId(Long bossId);

    boolean existsByBossIdAndKindAndPhase(Long bossId, BossRewardKind kind, int phase);

    Optional<BossReward> findByBossIdAndPlayerIdAndKindAndPhase(Long bossId, Long playerId,
                                                               BossRewardKind kind, int phase);
}
