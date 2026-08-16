package com.r2d.boss;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BossContributionRepository extends JpaRepository<BossContribution, Long> {

    Optional<BossContribution> findByRideId(String rideId);

    boolean existsByRideId(String rideId);

    List<BossContribution> findByBossId(Long bossId);

    @Query("select coalesce(sum(c.damage), 0) from BossContribution c where c.bossId = :bossId")
    double sumDamageByBoss(@Param("bossId") Long bossId);

    @Query("select coalesce(sum(c.damage), 0) from BossContribution c "
            + "where c.bossId = :bossId and c.playerId = :playerId")
    double sumDamageByBossAndPlayer(@Param("bossId") Long bossId, @Param("playerId") Long playerId);

    @Query("select count(distinct c.playerId) from BossContribution c where c.bossId = :bossId")
    long countParticipants(@Param("bossId") Long bossId);
}
