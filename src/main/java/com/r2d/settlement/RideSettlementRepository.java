package com.r2d.settlement;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RideSettlementRepository extends JpaRepository<RideSettlement, Long> {

    Optional<RideSettlement> findByRideId(String rideId);

    List<RideSettlement> findByPlayerIdOrderBySettledAtDesc(Long playerId);
}
