package com.r2d.settlement;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.r2d.domain.PackGrade;
import com.r2d.domain.RideMode;
import com.r2d.domain.TransportMode;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 주행 정산 결과.
 *
 * <p>도착 후 1~2분 연출에 필요한 모든 근거가 여기 들어 있습니다. 결과 화면은 이 값을 그대로
 * 보여 주며, 서버가 확정한 값과 클라이언트가 주행 중 로컬에 누적한 예상치를 구분해 저장합니다.
 */
@Entity
@Table(name = "ride_settlements",
        uniqueConstraints = @UniqueConstraint(name = "uk_settlement_ride", columnNames = {"rideId"}))
public class RideSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String rideId;

    @Column(nullable = false)
    private Long playerId;

    @Column(length = 20)
    private String regionCode;

    private Long bossId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RideMode mode;

    /** 심사 재현이면 재생한 로그 ID. 실측/파생 구분을 결과에도 남깁니다. */
    @Column(length = 40)
    private String sourceLogId;

    @Column(nullable = false)
    private double totalDistanceM;

    @Column(nullable = false)
    private double validDistanceM;

    @Column(nullable = false)
    private double pendingDistanceM;

    @Column(nullable = false)
    private double invalidDistanceM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransportMode transportMode;

    @Column(nullable = false)
    private double transportConfidence;

    @Column(nullable = false, length = 200)
    private String transportReason;

    @Column(nullable = false)
    private double gpsQualityScore;

    @Column(nullable = false)
    private double confidenceCoefficient;

    @Column(nullable = false)
    private double baseDamage;

    @Column(nullable = false)
    private double contributionMultiplier;

    @Column(nullable = false)
    private double deckSynergy;

    /** 계산된 확정 피해. */
    @Column(nullable = false)
    private double finalDamage;

    /** 보스에 실제로 반영된 피해. 남은 HP보다 큰 피해는 잘리므로 위 값과 다를 수 있습니다. */
    @Column(nullable = false)
    private double appliedDamage;

    /** 클라이언트가 주행 중 로컬에 누적한 예상치. 결과 화면에서 서버 확정치와 다른 배지로 표시합니다. */
    @Column(nullable = false)
    private double clientEstimatedDamage;

    @Column(nullable = false)
    private int newCells;

    @Column(nullable = false)
    private int updatedCells;

    @Column(nullable = false)
    private int verifiedCells;

    @Column(nullable = false)
    private int invalidCells;

    @Column(nullable = false)
    private int anomalyDiscovered;

    @Column(nullable = false)
    private int anomalyConfirmed;

    @Column(nullable = false)
    private int anomalyResolved;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PackGrade packGrade;

    private Long packId;

    @Column(nullable = false)
    private int xpGranted;

    @Column(nullable = false)
    private int coinsGranted;

    @Column(nullable = false)
    private int regionContributionPointsGranted;

    @Column(nullable = false)
    private boolean dailyCapApplied;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ride_settlement_notes", joinColumns = @JoinColumn(name = "settlement_id"))
    @Column(name = "note", length = 200)
    private List<String> notes = new ArrayList<>();

    @Column(nullable = false)
    private Instant settledAt = Instant.now();

    protected RideSettlement() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public Long getId() {
        return id;
    }

    public String getRideId() {
        return rideId;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public Long getBossId() {
        return bossId;
    }

    public RideMode getMode() {
        return mode;
    }

    public String getSourceLogId() {
        return sourceLogId;
    }

    public double getTotalDistanceM() {
        return totalDistanceM;
    }

    public double getValidDistanceM() {
        return validDistanceM;
    }

    public double getPendingDistanceM() {
        return pendingDistanceM;
    }

    public double getInvalidDistanceM() {
        return invalidDistanceM;
    }

    public TransportMode getTransportMode() {
        return transportMode;
    }

    public double getTransportConfidence() {
        return transportConfidence;
    }

    public String getTransportReason() {
        return transportReason;
    }

    public double getGpsQualityScore() {
        return gpsQualityScore;
    }

    public double getConfidenceCoefficient() {
        return confidenceCoefficient;
    }

    public double getBaseDamage() {
        return baseDamage;
    }

    public double getContributionMultiplier() {
        return contributionMultiplier;
    }

    public double getDeckSynergy() {
        return deckSynergy;
    }

    public double getFinalDamage() {
        return finalDamage;
    }

    public double getAppliedDamage() {
        return appliedDamage;
    }

    public double getClientEstimatedDamage() {
        return clientEstimatedDamage;
    }

    public int getNewCells() {
        return newCells;
    }

    public int getUpdatedCells() {
        return updatedCells;
    }

    public int getVerifiedCells() {
        return verifiedCells;
    }

    public int getInvalidCells() {
        return invalidCells;
    }

    public int getAnomalyDiscovered() {
        return anomalyDiscovered;
    }

    public int getAnomalyConfirmed() {
        return anomalyConfirmed;
    }

    public int getAnomalyResolved() {
        return anomalyResolved;
    }

    public PackGrade getPackGrade() {
        return packGrade;
    }

    public Long getPackId() {
        return packId;
    }

    public int getXpGranted() {
        return xpGranted;
    }

    public int getCoinsGranted() {
        return coinsGranted;
    }

    public int getRegionContributionPointsGranted() {
        return regionContributionPointsGranted;
    }

    public boolean isDailyCapApplied() {
        return dailyCapApplied;
    }

    public List<String> getNotes() {
        return notes;
    }

    public Instant getSettledAt() {
        return settledAt;
    }

    /** 필드가 많아 빌더로 조립합니다. */
    public static final class Builder {

        private final RideSettlement s = new RideSettlement();

        public Builder ride(String rideId, Long playerId, String regionCode, Long bossId,
                            RideMode mode, String sourceLogId) {
            s.rideId = rideId;
            s.playerId = playerId;
            s.regionCode = regionCode;
            s.bossId = bossId;
            s.mode = mode;
            s.sourceLogId = sourceLogId;
            return this;
        }

        public Builder distances(double total, double valid, double pending, double invalid) {
            s.totalDistanceM = total;
            s.validDistanceM = valid;
            s.pendingDistanceM = pending;
            s.invalidDistanceM = invalid;
            return this;
        }

        public Builder transport(TransportMode mode, double confidence, String reason) {
            s.transportMode = mode;
            s.transportConfidence = confidence;
            s.transportReason = reason;
            return this;
        }

        public Builder quality(double gpsQualityScore, double confidenceCoefficient) {
            s.gpsQualityScore = gpsQualityScore;
            s.confidenceCoefficient = confidenceCoefficient;
            return this;
        }

        public Builder damage(double base, double contributionMultiplier, double deckSynergy,
                              double finalDamage, double appliedDamage, double clientEstimate) {
            s.baseDamage = base;
            s.contributionMultiplier = contributionMultiplier;
            s.deckSynergy = deckSynergy;
            s.finalDamage = finalDamage;
            s.appliedDamage = appliedDamage;
            s.clientEstimatedDamage = clientEstimate;
            return this;
        }

        public Builder cells(int newCells, int updatedCells, int verifiedCells, int invalidCells) {
            s.newCells = newCells;
            s.updatedCells = updatedCells;
            s.verifiedCells = verifiedCells;
            s.invalidCells = invalidCells;
            return this;
        }

        public Builder anomalies(int discovered, int confirmed, int resolved) {
            s.anomalyDiscovered = discovered;
            s.anomalyConfirmed = confirmed;
            s.anomalyResolved = resolved;
            return this;
        }

        public Builder rewards(PackGrade packGrade, Long packId, int xp, int coins, int contributionPoints,
                               boolean dailyCapApplied) {
            s.packGrade = packGrade;
            s.packId = packId;
            s.xpGranted = xp;
            s.coinsGranted = coins;
            s.regionContributionPointsGranted = contributionPoints;
            s.dailyCapApplied = dailyCapApplied;
            return this;
        }

        public Builder notes(List<String> notes) {
            s.notes = new ArrayList<>(notes);
            return this;
        }

        public RideSettlement build() {
            return s;
        }
    }
}
