package com.r2d.anomaly;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AnomalyObservationRepository extends JpaRepository<AnomalyObservation, Long> {

    boolean existsByCandidateIdAndRideId(Long candidateId, String rideId);

    boolean existsByCandidateIdAndPlayerId(Long candidateId, Long playerId);

    boolean existsByCandidateIdAndObservedDate(Long candidateId, LocalDate observedDate);

    List<AnomalyObservation> findByCandidateId(Long candidateId);
}
