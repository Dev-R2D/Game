package com.r2d.ride;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ImuWindowRepository extends JpaRepository<ImuWindow, Long> {

    List<ImuWindow> findByRideIdOrderByEpochMsAsc(String rideId);
}
