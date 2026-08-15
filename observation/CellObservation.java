package com.r2d.observation;

import java.time.Instant;

import com.r2d.domain.CellState;
import com.r2d.domain.ContributionType;
import com.r2d.domain.Validity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 한 주행이 한 셀에 남긴 관측 기록.
 *
 * <p>(rideId, cellId)가 유일하므로 같은 주행이 같은 셀을 여러 번 지나도 기여는 한 번만
 * 쌓입니다. 결과 화면의 "신규구간 크리티컬", "갱신구간 추가데미지" 같은 내역이 전부
 * 이 행에서 나옵니다.
 */
@Entity
@Table(name = "cell_observations",
        uniqueConstraints = @UniqueConstraint(name = "uk_observation_ride_cell", columnNames = {"rideId", "cellId"}),
        indexes = @Index(name = "idx_observation_cell", columnList = "cellId"))
public class CellObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String rideId;

    @Column(nullable = false, length = 32)
    private String cellId;

    @Column(nullable = false)
    private Long playerId;

    @Column(nullable = false)
    private double distanceM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Validity validity;

    /** 관측 시점의 셀 상태. 나중에 셀 상태가 바뀌어도 정산 근거는 그대로 남아야 합니다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CellState stateAtObservation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContributionType contributionType;

    /** 셀 상태 · 덱 계열 보정 · 반복 방문 감쇠까지 반영한 최종 기여 배율. */
    @Column(nullable = false)
    private double appliedMultiplier;

    @Column(nullable = false)
    private double damage;

    /** 같은 이용자가 최근에 이 셀을 몇 번 지났는지. 반복 감쇠의 근거입니다. */
    @Column(nullable = false)
    private int recentRepeatCount;

    @Column(length = 100)
    private String note;

    @Column(nullable = false)
    private Instant observedAt = Instant.now();

    protected CellObservation() {
    }

    public CellObservation(String rideId, String cellId, Long playerId, double distanceM, Validity validity,
                           CellState stateAtObservation, ContributionType contributionType,
                           double appliedMultiplier, double damage, int recentRepeatCount, String note,
                           Instant observedAt) {
        this.rideId = rideId;
        this.cellId = cellId;
        this.playerId = playerId;
        this.distanceM = distanceM;
        this.validity = validity;
        this.stateAtObservation = stateAtObservation;
        this.contributionType = contributionType;
        this.appliedMultiplier = appliedMultiplier;
        this.damage = damage;
        this.recentRepeatCount = recentRepeatCount;
        this.note = note;
        this.observedAt = observedAt;
    }

    public Long getId() {
        return id;
    }

    public String getRideId() {
        return rideId;
    }

    public String getCellId() {
        return cellId;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public double getDistanceM() {
        return distanceM;
    }

    public Validity getValidity() {
        return validity;
    }

    public CellState getStateAtObservation() {
        return stateAtObservation;
    }

    public ContributionType getContributionType() {
        return contributionType;
    }

    public double getAppliedMultiplier() {
        return appliedMultiplier;
    }

    public double getDamage() {
        return damage;
    }

    public int getRecentRepeatCount() {
        return recentRepeatCount;
    }

    public String getNote() {
        return note;
    }

    public Instant getObservedAt() {
        return observedAt;
    }
}
