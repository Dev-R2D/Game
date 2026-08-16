package com.r2d.observation;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CellObservationRepository extends JpaRepository<CellObservation, Long> {

    List<CellObservation> findByRideId(String rideId);

    boolean existsByRideId(String rideId);

    /** 같은 이용자가 최근 기간에 이 셀을 유효하게 지난 횟수. 반복 방문 감쇠에 씁니다. */
    long countByCellIdAndPlayerIdAndObservedAtAfter(String cellId, Long playerId, Instant after);

    /** 이 셀을 이 이용자가 지금까지 한 번이라도 관측했는지. 독립 관측자 수 집계에 씁니다. */
    boolean existsByCellIdAndPlayerId(String cellId, Long playerId);
}
