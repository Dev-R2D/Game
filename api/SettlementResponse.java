package com.r2d.api;

import java.time.Instant;
import java.util.List;

import com.r2d.observation.CellObservation;
import com.r2d.settlement.RideSettlement;

/**
 * 결과 화면에 그대로 대응하는 정산 응답.
 *
 * <p>서버 확정치와 클라이언트 로컬 예상치를 반드시 함께 내려보냅니다. 결과 화면에서 둘을
 * 다른 배지로 표시해야 "무엇이 확정된 값인지"가 명확해지기 때문입니다.
 */
public record SettlementResponse(
        String rideId,
        String mode,
        String sourceLogId,
        /** 이 주행이 공격한 보스. 처치 화면을 그리려면 {@code GET /bosses/{bossId}}로 조회하세요. */
        Long bossId,
        String regionCode,
        DistanceBreakdown distance,
        TransportInfo transport,
        DamageBreakdown damage,
        ContributionSummary contribution,
        AnomalySummary anomaly,
        RewardSummary reward,
        List<CellLine> cellLines,
        List<String> notes,
        Instant settledAt
) {

    public record DistanceBreakdown(double totalM, double validM, double pendingM, double invalidM) {
    }

    public record TransportInfo(String mode, double confidence, String reason) {
    }

    public record DamageBreakdown(
            double baseDamage,
            double contributionMultiplier,
            double deckSynergy,
            double confidenceCoefficient,
            /** 서버가 계산한 확정 피해. */
            double finalDamage,
            /** 보스에 실제로 반영된 피해. 남은 HP보다 크면 잘립니다. */
            double appliedDamage,
            /** 클라이언트가 주행 중 누적한 예상치. 참고용입니다. */
            double clientEstimate,
            String formula
    ) {
    }

    public record ContributionSummary(int newCells, int updatedCells, int verifiedCells, int invalidCells) {
    }

    public record AnomalySummary(int discovered, int confirmed, int resolved, String note) {
    }

    public record RewardSummary(String packGrade, Long packId, int xp, int coins,
                                int regionContributionPoints, boolean dailyCapApplied) {
    }

    /** 셀 단위 데미지 내역 한 줄. */
    public record CellLine(String cellId, String state, String contributionType, String validity,
                           double distanceM, double multiplier, double damage, String note) {
        public static CellLine of(CellObservation observation) {
            return new CellLine(observation.getCellId(), observation.getStateAtObservation().getLabel(),
                    observation.getContributionType().getLabel(), observation.getValidity().getLabel(),
                    observation.getDistanceM(), observation.getAppliedMultiplier(),
                    observation.getDamage(), observation.getNote());
        }
    }

    public static SettlementResponse of(RideSettlement s, List<CellObservation> observations) {
        return new SettlementResponse(
                s.getRideId(),
                s.getMode().getLabel(),
                s.getSourceLogId(),
                s.getBossId(),
                s.getRegionCode(),
                new DistanceBreakdown(s.getTotalDistanceM(), s.getValidDistanceM(),
                        s.getPendingDistanceM(), s.getInvalidDistanceM()),
                new TransportInfo(s.getTransportMode().getLabel(), s.getTransportConfidence(),
                        s.getTransportReason()),
                new DamageBreakdown(s.getBaseDamage(), s.getContributionMultiplier(), s.getDeckSynergy(),
                        s.getConfidenceCoefficient(), s.getFinalDamage(), s.getAppliedDamage(),
                        s.getClientEstimatedDamage(),
                        "확정 피해 = 유효 거리 기반 피해 × 데이터 기여 배율 × 덱 시너지 × 신뢰도 계수"),
                new ContributionSummary(s.getNewCells(), s.getUpdatedCells(), s.getVerifiedCells(),
                        s.getInvalidCells()),
                new AnomalySummary(s.getAnomalyDiscovered(), s.getAnomalyConfirmed(), s.getAnomalyResolved(),
                        "발견 단계는 의심 상태이며, 확정은 교차검증이 끝난 뒤에만 일어납니다."),
                new RewardSummary(s.getPackGrade() == null ? null : s.getPackGrade().getLabel(),
                        s.getPackId(), s.getXpGranted(), s.getCoinsGranted(),
                        s.getRegionContributionPointsGranted(), s.isDailyCapApplied()),
                observations.stream().map(CellLine::of).toList(),
                s.getNotes(),
                s.getSettledAt());
    }
}
