package com.r2d.settlement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.r2d.analysis.RideSegment;
import com.r2d.card.DeckEffect;
import com.r2d.config.GameRules;
import com.r2d.domain.CellState;
import com.r2d.domain.ContributionType;
import com.r2d.domain.Validity;

import org.springframework.stereotype.Service;

/**
 * 확정 피해 계산.
 *
 * <pre>
 * 확정 피해 = 유효 거리 기반 피해 × 데이터 기여 배율 × 덱 시너지 × 신뢰도 계수
 * </pre>
 *
 * <p><b>안전 규칙이 구조로 강제되는 지점입니다.</b> 이 클래스는 IMU 파형이나 충격 세기에
 * 접근할 수 없습니다. 입력이 거리·셀 상태·덱·신뢰도뿐이므로, 파손 구간을 세게 밟는 행동으로
 * 피해를 올릴 방법이 애초에 존재하지 않습니다. 보상은 희소성·신뢰도·갱신 필요도에서만 나옵니다.
 */
@Service
public class DamageCalculator {

    private final GameRules rules;

    public DamageCalculator(GameRules rules) {
        this.rules = rules;
    }

    /**
     * @param segments 품질 검사를 마친 구간 목록
     * @param cellStates 셀별 관측 시점 상태
     * @param recentRepeats 셀별 최근 반복 방문 횟수(같은 이용자 기준)
     * @param deck 편성된 덱 효과
     * @param dataContributionAllowed 주행 수단 신뢰도가 기준을 넘겼는지. false면 기본 피해만 나옵니다.
     * @param confidenceCoefficient 위치·수단·품질을 요약한 신뢰도 계수
     */
    public DamageResult calculate(List<RideSegment> segments,
                                  Map<String, CellState> cellStates,
                                  Map<String, Integer> recentRepeats,
                                  DeckEffect deck,
                                  boolean dataContributionAllowed,
                                  double confidenceCoefficient) {
        if (segments.isEmpty()) {
            return DamageResult.empty();
        }

        Map<String, CellAccumulator> perCell = new LinkedHashMap<>();
        for (RideSegment segment : segments) {
            perCell.computeIfAbsent(segment.cellId(), CellAccumulator::new).add(segment);
        }

        GameRules.Damage d = rules.damage();
        double enduranceFactor = 1.0 + deck.enduranceBonus();

        List<CellContribution> contributions = new ArrayList<>();
        double baseDamageTotal = 0;
        double weightedMultiplierSum = 0;
        double finalDamageTotal = 0;

        for (CellAccumulator acc : perCell.values()) {
            Validity validity = acc.resolveValidity(rules.quality().minValidSampleRatio());
            double damageableDistance = acc.validDistance + acc.pendingDistance;
            if (validity == Validity.INVALID || damageableDistance <= 0) {
                contributions.add(new CellContribution(acc.cellId, acc.totalDistance(), Validity.INVALID,
                        cellStates.getOrDefault(acc.cellId, CellState.UNSURVEYED), ContributionType.NONE,
                        0, 0, 0, 0, acc.reason()));
                continue;
            }

            CellState state = cellStates.getOrDefault(acc.cellId, CellState.UNSURVEYED);
            double baseDamage = damageableDistance * d.perMeter() * enduranceFactor;

            ContributionType type;
            double multiplier;
            String note;

            if (validity == Validity.VALID && dataContributionAllowed && state.getContributionType() != ContributionType.NONE) {
                type = state.getContributionType();
                int repeats = recentRepeats.getOrDefault(acc.cellId, 0);
                multiplier = effectiveMultiplier(state, deck, repeats);
                note = state.getLabel() + " 구간 " + type.getLabel() + " 기여";
                if (repeats > 0) {
                    note += " (최근 " + repeats + "회 반복으로 보너스 감쇠)";
                }
            } else {
                // 보류 구간, 수단 미확정 주행, 이미 최신인 셀은 기본 피해만 인정합니다.
                type = ContributionType.NONE;
                multiplier = 1.0;
                note = validity == Validity.PENDING ? acc.reason()
                        : (!dataContributionAllowed ? "주행 수단 신뢰도 미달로 기여 보류" : "최근 조사된 구간");
            }

            double damage = baseDamage * multiplier * deck.synergy() * confidenceCoefficient;

            contributions.add(new CellContribution(acc.cellId, acc.totalDistance(), validity, state, type,
                    baseDamage, multiplier, damage, recentRepeats.getOrDefault(acc.cellId, 0), note));

            baseDamageTotal += baseDamage;
            weightedMultiplierSum += baseDamage * multiplier;
            finalDamageTotal += damage;
        }

        double weightedMultiplier = baseDamageTotal <= 0 ? 1.0 : weightedMultiplierSum / baseDamageTotal;

        return new DamageResult(List.copyOf(contributions), baseDamageTotal, weightedMultiplier,
                deck.synergy(), confidenceCoefficient, finalDamageTotal);
    }

    /**
     * 셀 상태 · 덱 계열 보정 · 반복 방문 감쇠를 합친 기여 배율.
     *
     * <p>반복 방문은 기본 피해를 깎지 않고 <b>보너스 부분만</b> 줄입니다. 매일 같은 길을 달리는
     * 이용자도 성장은 계속하되, 이미 충분히 관측된 셀을 반복해서 큰 보상을 얻지는 못하게 하려는 것입니다.
     * 셀이 노후화되거나 보수 후 재확인이 필요해지면 보너스 기회가 다시 열립니다.
     */
    private double effectiveMultiplier(CellState state, DeckEffect deck, int recentRepeats) {
        double raw = state.getContributionMultiplier() * deck.bonusFor(state.getContributionType());
        double decay = 1.0 / (1.0 + rules.cell().repeatDecayFactor() * recentRepeats);
        return 1.0 + (raw - 1.0) * decay;
    }

    /** 셀별로 유효·보류·무효 거리를 모으는 집계기. */
    private static final class CellAccumulator {

        private final String cellId;
        private double validDistance;
        private double pendingDistance;
        private double invalidDistance;
        private String firstPendingReason;

        private CellAccumulator(String cellId) {
            this.cellId = cellId;
        }

        private void add(RideSegment segment) {
            switch (segment.validity()) {
                case VALID -> validDistance += segment.distanceM();
                case PENDING -> {
                    pendingDistance += segment.distanceM();
                    if (firstPendingReason == null) {
                        firstPendingReason = segment.reason();
                    }
                }
                case INVALID -> {
                    invalidDistance += segment.distanceM();
                    if (firstPendingReason == null) {
                        firstPendingReason = segment.reason();
                    }
                }
            }
        }

        private double totalDistance() {
            return validDistance + pendingDistance + invalidDistance;
        }

        /**
         * 셀 단위 유효성.
         *
         * <p>구간 하나가 흔들렸다고 셀 전체를 버리지 않고, 고품질 표본이 충분한 비율을
         * 차지할 때만 데이터 기여를 인정합니다.
         */
        private Validity resolveValidity(double minValidRatio) {
            double usable = validDistance + pendingDistance;
            if (usable <= 0) {
                return Validity.INVALID;
            }
            return (validDistance / usable) >= minValidRatio ? Validity.VALID : Validity.PENDING;
        }

        private String reason() {
            return firstPendingReason == null ? "" : firstPendingReason;
        }
    }
}
