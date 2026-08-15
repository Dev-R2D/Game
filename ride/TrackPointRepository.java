package com.r2d.ride;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackPointRepository extends JpaRepository<TrackPoint, Long> {

    List<TrackPoint> findByRideIdOrderByEpochMsAsc(String rideId);
}
