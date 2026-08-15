package com.r2d.anomaly;

import java.time.Instant;

import com.r2d.domain.AnomalyClass;
import com.r2d.domain.AnomalyState;

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
 * 노면 이상 후보 = 게임 안의 서브몹.
 *
 * <p>한 셀에 하나의 후보만 추적합니다. 관측이 반복될수록 신뢰도가 올라가고, 서로 다른
 * 이용자의 독립 관측이 쌓이면 의심에서 확정으로 전이합니다. 보수 후 재주행에서 패턴이
 * 반복되지 않으면 소멸합니다.
 */
@Entity
@Table(name = "anomaly_candidates",
        uniqueConstraints = @UniqueConstraint(name = "uk_anomaly_cell", columnNames = {"cellId"}),
        indexes = @Index(name = "idx_anomaly_state", columnList = "state"))
public class AnomalyCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String cellId;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lon;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AnomalyClass anomalyClass;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnomalyState state = AnomalyState.SUSPECT;

    /** 누적 신뢰도(0~1). 독립 관측이 쌓일수록 올라갑니다. */
    @Column(nullable = false)
    private double confidence;

    @Column(nullable = false)
    private int distinctObservers;

    @Column(nullable = false)
    private int distinctDays;

    /** 확정 이후 이상 패턴 없이 지나간 주행 수. 소멸 판정의 근거입니다. */
    @Column(nullable = false)
    private int cleanPasses;

    /** 최초 발견자의 가명 식별자. 공개 표시는 동의 여부를 다시 확인한 뒤에만 합니다. */
    @Column(length = 40)
    private String discovererPublicId;

    @Column(nullable = false)
    private Instant firstSeenAt = Instant.now();

    @Column(nullable = false)
    private Instant lastSeenAt = Instant.now();

    private Instant confirmedAt;

    private Instant resolvedAt;

    protected AnomalyCandidate() {
    }

    public AnomalyCandidate(String cellId, double lat, double lon, AnomalyClass anomalyClass,
                            double confidence, String discovererPublicId, Instant observedAt) {
        this.cellId = cellId;
        this.lat = lat;
        this.lon = lon;
        this.anomalyClass = anomalyClass;
        this.confidence = confidence;
        this.discovererPublicId = discovererPublicId;
        this.distinctObservers = 1;
        this.distinctDays = 1;
        this.firstSeenAt = observedAt;
        this.lastSeenAt = observedAt;
    }

    /**
     * 새 관측을 반영합니다.
     *
     * @param newObserver 이 후보를 처음 보는 이용자인지
     * @param newDay 기존 관측과 날짜가 분리되는지
     */
    public void reinforce(double sampleConfidence, boolean newObserver, boolean newDay, Instant observedAt) {
        if (newObserver) {
            distinctObservers++;
        }
        if (newDay) {
            distinctDays++;
        }
        // 독립 관측일 때만 신뢰도를 크게 올립니다. 같은 사람이 같은 날 여러 번 지나는 것으로는 확신이 커지지 않습니다.
        double weight = newObserver ? 0.6 : (newDay ? 0.35 : 0.1);
        this.confidence = Math.min(1.0, confidence + sampleConfidence * (1.0 - confidence) * weight);
        this.lastSeenAt = observedAt;
        this.cleanPasses = 0;
    }

    /** 이상 패턴 없이 지나간 주행을 기록합니다. */
    public void recordCleanPass(Instant observedAt) {
        this.cleanPasses++;
        this.lastSeenAt = observedAt;
    }

    /**
     * 확정 조건을 만족하면 확정으로 전이합니다.
     *
     * @return 이번 호출로 확정 전이가 일어났으면 true
     */
    public boolean tryConfirm(int requiredObservers, int requiredDays, double requiredConfidence, Instant now) {
        if (state != AnomalyState.SUSPECT || confidence < requiredConfidence) {
            return false;
        }
        // 서로 다른 이용자의 독립 관측이 원칙이고, 라이더가 적은 지역에서는 날짜가 분리된 반복 관측으로 대체합니다.
        boolean byObservers = distinctObservers >= requiredObservers;
        boolean byDays = distinctDays >= requiredDays;
        if (!byObservers && !byDays) {
            return false;
        }
        this.state = AnomalyState.CONFIRMED;
        this.confirmedAt = now;
        return true;
    }

    /**
     * 소멸 조건을 만족하면 소멸로 전이합니다.
     *
     * @return 이번 호출로 소멸 전이가 일어났으면 true
     */
    public boolean tryResolve(int requiredCleanPasses, Instant now) {
        if (state != AnomalyState.CONFIRMED || cleanPasses < requiredCleanPasses) {
            return false;
        }
        this.state = AnomalyState.RESOLVED;
        this.resolvedAt = now;
        return true;
    }

    public Long getId() {
        return id;
    }

    public String getCellId() {
        return cellId;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

    public AnomalyClass getAnomalyClass() {
        return anomalyClass;
    }

    public void setAnomalyClass(AnomalyClass anomalyClass) {
        this.anomalyClass = anomalyClass;
    }

    public AnomalyState getState() {
        return state;
    }

    public double getConfidence() {
        return confidence;
    }

    public int getDistinctObservers() {
        return distinctObservers;
    }

    public int getDistinctDays() {
        return distinctDays;
    }

    public int getCleanPasses() {
        return cleanPasses;
    }

    public String getDiscovererPublicId() {
        return discovererPublicId;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
