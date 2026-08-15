package com.r2d.reward;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionContributionLedgerRepository extends JpaRepository<RegionContributionLedger, Long> {

    Optional<RegionContributionLedger> findByPlayerIdAndRegionCode(Long playerId, String regionCode);

    List<RegionContributionLedger> findByPlayerId(Long playerId);
}
