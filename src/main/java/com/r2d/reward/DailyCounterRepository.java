package com.r2d.reward;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyCounterRepository extends JpaRepository<DailyCounter, Long> {

    Optional<DailyCounter> findByPlayerIdAndDay(Long playerId, LocalDate day);
}
