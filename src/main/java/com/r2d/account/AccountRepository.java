package com.r2d.account;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {

    /** 조회 전에 반드시 {@link Account#normalizeEmail}을 거친 값을 넘겨야 합니다. */
    Optional<Account> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<Account> findByPlayerId(Long playerId);
}
