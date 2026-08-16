package com.r2d.cell;

import java.time.Duration;
import java.time.Instant;

import com.r2d.common.CellGrid;
import com.r2d.domain.CellState;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * 도로 셀.
 *
 * <p>R2D의 맵은 임의의 좌표가 아니라 이 셀의 데이터 상태 위에 만들어집니다. 보스 HP,
 * 미션 후보, 기여 배율, 지역 정화 진행도가 전부 여기서 파생됩니다.
 */
@Entity
@Table(name = "road_cells", indexes = {
        @Index(name = "idx_cell_region", columnList = "regionCode"),
        @Index(name = "idx_cell_state", columnList = "state")
})
public class RoadCell {

    /** 격자 인덱스 기반 셀 ID. {@link CellGrid}가 좌표에서 결정론적으로 계산합니다. */
    @Id
    @Column(length = 32)
    private String cellId;

    @Column(nullable = false, length = 20)
    private String regionCode;

    @Column(nullable = false)
    private double centerLat;

    @Column(nullable = false)
    private double centerLon;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CellState state = CellState.UNSURVEYED;

    /** 누적 관측 신뢰도(0~1). 관측 품질과 독립 관측 수가 올라갈수록 상승합니다. */
    @Column(nullable = false)
    private double confidence = 0.0;

    @Column(nullable = false)
    private int observationCount = 0;

    /** 서로 다른 이용자의 관측 수. 교차검증 판정의 핵심 지표입니다. */
    @Column(nullable = false)
    private int distinctObserverCount = 0;

    private Instant lastObservedAt;

    /**
     * 자전거 통행이 가능한 셀인지. 자동차전용도로·계단·사유지·통제 구간은 false이며,
     * 데이터가 아무리 부족해도 미션·보스 후보가 되지 않습니다.
     */
    @Column(nullable = false)
    private boolean bikeAccessible = true;

    /** 안전 필터에서 제외된 이유. 심사 화면에서 제외 사유를 그대로 보여주기 위해 남깁니다. */
    @Column(length = 100)
    private String exclusionReason;

    protected RoadCell() {
    }

    public RoadCell(String cellId, String regionCode) {
        this.cellId = cellId;
        this.regionCode = regionCode;
        this.centerLat = CellGrid.centerLat(cellId);
        this.centerLon = CellGrid.centerLon(cellId);
    }

    /**
     * 유효 관측 1회를 반영합니다.
     *
     * @param observerIsNew 이 셀을 처음 관측하는 이용자인지
     * @param sampleConfidence 이번 관측의 품질 신뢰도(0~1)
     */
    public void applyObservation(boolean observerIsNew, double sampleConfidence, Instant observedAt) {
        this.observationCount++;
        if (observerIsNew) {
            this.distinctObserverCount++;
        }
        // 신뢰도는 단조 증가하되 포화합니다. 한 번의 고품질 관측만으로 확신하지 않기 위해서입니다.
        this.confidence = Math.min(1.0, this.confidence + sampleConfidence * (1.0 - this.confidence) * 0.6);
        this.lastObservedAt = observedAt;
    }

    /**
     * 관측 이력과 신선도 기준으로 셀 상태를 다시 계산합니다.
     *
     * <p>보수 후 재확인(RECHECK_REQUIRED) 상태는 여기서 자동으로 벗어나지 않습니다.
     * 재확인 주행이 실제로 들어와야 해소되므로 전이는 재확인 처리에서만 일어납니다.
     */
    public void recomputeState(int freshnessDays, double lowConfidenceThreshold, Instant now) {
        if (state == CellState.RECHECK_REQUIRED) {
            return;
        }
        if (lastObservedAt == null || observationCount == 0) {
            this.state = CellState.UNSURVEYED;
            return;
        }
        boolean stale = Duration.between(lastObservedAt, now).toDays() >= freshnessDays;
        if (stale) {
            this.state = CellState.STALE;
        } else if (confidence < lowConfidenceThreshold) {
            this.state = CellState.LOW_CONFIDENCE;
        } else {
            this.state = CellState.FRESH;
        }
    }

    /** 이상이 해소됐다고 보고되어 재확인 대상이 됩니다. */
    public void markRecheckRequired() {
        this.state = CellState.RECHECK_REQUIRED;
    }

    /** 재확인 주행이 들어와 재확인 상태를 해소합니다. */
    public void clearRecheck() {
        if (state == CellState.RECHECK_REQUIRED) {
            this.state = CellState.FRESH;
        }
    }

    public void excludeFromMissions(String reason) {
        this.bikeAccessible = false;
        this.exclusionReason = reason;
    }

    public String getCellId() {
        return cellId;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public double getCenterLat() {
        return centerLat;
    }

    public double getCenterLon() {
        return centerLon;
    }

    public CellState getState() {
        return state;
    }

    public void setState(CellState state) {
        this.state = state;
    }

    public double getConfidence() {
        return confidence;
    }

    public int getObservationCount() {
        return observationCount;
    }

    public int getDistinctObserverCount() {
        return distinctObserverCount;
    }

    public Instant getLastObservedAt() {
        return lastObservedAt;
    }

    public boolean isBikeAccessible() {
        return bikeAccessible;
    }

    public String getExclusionReason() {
        return exclusionReason;
    }
}
