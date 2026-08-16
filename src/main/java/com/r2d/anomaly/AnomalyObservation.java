package com.r2d.anomaly;

import java.time.Instant;
import java.time.LocalDate;

import com.r2d.domain.AnomalyClass;

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
 * 후보 하나에 대한 개별 관측 이력.
 *
 * <p>(candidateId, rideId)를 유일하게 두어 같은 주행이 재전송되어도 관측 수가 부풀지 않게 합니다.
 * 교차검증은 "몇 번 봤는가"가 아니라 "누가, 언제 봤는가"로 판정하므로 관측자와 날짜를 같이 남깁니다.
 */
@Entity
@Table(name = "anomaly_observations",
        uniqueConstraints = @UniqueConstraint(name = "uk_anomaly_obs_ride",
                columnNames = {"candidateId", "rideId"}),
        indexes = @Index(name = "idx_anomaly_obs_candidate", columnList = "candidateId"))
public class AnomalyObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long candidateId;

    @Column(nullable = false, length = 40)
    private String rideId;

    @Column(nullable = false)
    private Long playerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AnomalyClass observedClass;

    @Column(nullable = false)
    private double confidence;

    @Column(nullable = false)
    private LocalDate observedDate;

    @Column(nullable = false)
    private Instant observedAt;

    /** 심사 재현 로그에서 만들어진 관측인지. 실측과 파생을 절대 섞어 표기하지 않기 위해 남깁니다. */
    @Column(nullable = false)
    private boolean simulated;

    protected AnomalyObservation() {
    }

    public AnomalyObservation(Long candidateId, String rideId, Long playerId, AnomalyClass observedClass,
                              double confidence, Instant observedAt, LocalDate observedDate, boolean simulated) {
        this.candidateId = candidateId;
        this.rideId = rideId;
        this.playerId = playerId;
        this.observedClass = observedClass;
        this.confidence = confidence;
        this.observedAt = observedAt;
        this.observedDate = observedDate;
        this.simulated = simulated;
    }

    public Long getId() {
        return id;
    }

    public Long getCandidateId() {
        return candidateId;
    }

    public String getRideId() {
        return rideId;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public AnomalyClass getObservedClass() {
        return observedClass;
    }

    public double getConfidence() {
        return confidence;
    }

    public LocalDate getObservedDate() {
        return observedDate;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public boolean isSimulated() {
        return simulated;
    }
}
