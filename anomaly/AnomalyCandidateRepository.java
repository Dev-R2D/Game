package com.r2d.anomaly;

import java.util.List;
import java.util.Optional;

import com.r2d.domain.AnomalyState;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnomalyCandidateRepository extends JpaRepository<AnomalyCandidate, Long> {

    Optional<AnomalyCandidate> findByCellId(String cellId);

    List<AnomalyCandidate> findByStateIn(List<AnomalyState> states);

    @Query("""
            select a from AnomalyCandidate a
            where a.lat between :minLat and :maxLat
              and a.lon between :minLon and :maxLon
              and a.state <> com.r2d.domain.AnomalyState.RESOLVED
            """)
    List<AnomalyCandidate> findActiveInBoundingBox(@Param("minLat") double minLat,
                                                   @Param("maxLat") double maxLat,
                                                   @Param("minLon") double minLon,
                                                   @Param("maxLon") double maxLon);
}
