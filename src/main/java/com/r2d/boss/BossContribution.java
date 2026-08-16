package com.r2d.boss;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 보스에 대한 주행 단위 기여 원장.
 *
 * <p>rideId에 유일 제약을 걸어 둔 것이 중복 피해를 막는 최종 방어선입니다. 오프라인 큐가
 * 같은 정산 요청을 두 번 보내도, 동시에 여러 요청이 들어와도 보스 HP는 한 번만 깎입니다.
 */
@Entity
@Table(name = "boss_contributions",
        uniqueConstraints = @UniqueConstraint(name = "uk_contribution_ride", columnNames = {"rideId"}),
        indexes = @Index(name = "idx_contribution_boss", columnList = "bossId"))
public class BossContribution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long bossId;

    @Column(nullable = false, length = 40)
    private String rideId;

    @Column(nullable = false)
    private Long playerId;

    /** 서버가 확정한 피해. 클라이언트의 예상치는 여기 들어오지 않습니다. */
    @Column(nullable = false)
    private double damage;

    /** 이 주행이 기여한 셀 수. 속도·막타가 아니라 참여 구간 기준으로 보상을 나누기 위한 값입니다. */
    @Column(nullable = false)
    private int contributedCells;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected BossContribution() {
    }

    public BossContribution(Long bossId, String rideId, Long playerId, double damage, int contributedCells) {
        this.bossId = bossId;
        this.rideId = rideId;
        this.playerId = playerId;
        this.damage = damage;
        this.contributedCells = contributedCells;
    }

    public Long getId() {
        return id;
    }

    public Long getBossId() {
        return bossId;
    }

    public String getRideId() {
        return rideId;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public double getDamage() {
        return damage;
    }

    public int getContributedCells() {
        return contributedCells;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
