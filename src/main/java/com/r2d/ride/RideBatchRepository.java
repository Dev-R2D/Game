package com.r2d.ride;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RideBatchRepository extends JpaRepository<RideBatch, Long> {

    Optional<RideBatch> findByIdempotencyKey(String idempotencyKey);

    Optional<RideBatch> findByRideIdAndBatchSeq(String rideId, int batchSeq);

    List<RideBatch> findByRideIdOrderByBatchSeqAsc(String rideId);
}
