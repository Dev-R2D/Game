package com.r2d.boss;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BossRepository extends JpaRepository<Boss, Long> {

    Optional<Boss> findFirstByRegionCodeAndStatusOrderByCreatedAtDesc(String regionCode, BossStatus status);

    List<Boss> findByStatus(BossStatus status);
}
