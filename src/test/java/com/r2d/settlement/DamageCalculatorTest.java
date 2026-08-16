package com.r2d.settlement;

import java.util.List;
import java.util.Map;

import com.r2d.analysis.RideSegment;
import com.r2d.card.DeckEffect;
import com.r2d.config.TestRules;
import com.r2d.domain.CellState;
import com.r2d.domain.ContributionType;
import com.r2d.domain.Validity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 확정 피해 계산 테스트.
 *
 * <p>"같은 거리를 달려도 어떤 길을 달렸는지에 따라 결과가 달라진다"는 핵심 규칙과,
 * "충격의 크기는 보상을 올리지 않는다"는 안전 규칙을 검증합니다.
 */
class DamageCalculatorTest {

    private final DamageCalculator calculator = new DamageCalculator(TestRules.defaults());

    private static RideSegment segment(String cellId, double distanceM, Validity validity) {
        return new RideSegment(0, 1000, distanceM, distanceM, 8.0, 37.2, 127.1, cellId, validity,
                validity == Validity.VALID ? null : "테스트 사유");
    }

    @Test
    @DisplayName("같은 거리라도 미탐사 구간이 최근 조사된 구간보다 큰 피해를 낸다")
    void newCellBeatsFreshCell() {
        DamageResult onNewCell = calculator.calculate(
                List.of(segment("C1", 1000, Validity.VALID)),
                Map.of("C1", CellState.UNSURVEYED), Map.of(), DeckEffect.neutral(), true, 1.0);
        DamageResult onFreshCell = calculator.calculate(
                List.of(segment("C1", 1000, Validity.VALID)),
                Map.of("C1", CellState.FRESH), Map.of(), DeckEffect.neutral(), true, 1.0);

        assertThat(onNewCell.finalDamage()).isGreaterThan(onFreshCell.finalDamage());
        assertThat(onNewCell.countByType(ContributionType.NEW)).isEqualTo(1);
        assertThat(onFreshCell.countByType(ContributionType.NEW)).isZero();
    }

    @Test
    @DisplayName("무효 구간은 기본 피해에도 들어가지 않는다")
    void invalidSegmentsProduceNoDamage() {
        DamageResult result = calculator.calculate(
                List.of(segment("C1", 1000, Validity.INVALID)),
                Map.of("C1", CellState.UNSURVEYED), Map.of(), DeckEffect.neutral(), true, 1.0);

        assertThat(result.finalDamage()).isZero();
        assertThat(result.baseDamage()).isZero();
    }

    @Test
    @DisplayName("보류 구간은 기본 피해만 인정하고 데이터 기여는 붙이지 않는다")
    void pendingSegmentsGetBaseDamageOnly() {
        DamageResult result = calculator.calculate(
                List.of(segment("C1", 1000, Validity.PENDING)),
                Map.of("C1", CellState.UNSURVEYED), Map.of(), DeckEffect.neutral(), true, 1.0);

        assertThat(result.finalDamage()).isEqualTo(1000.0, within(0.001));
        assertThat(result.countByType(ContributionType.NEW)).isZero();
    }

    @Test
    @DisplayName("주행 수단이 확정되지 않으면 기여 배율 없이 기본 피해만 남는다")
    void withoutTransportConfidenceOnlyBaseDamage() {
        DamageResult result = calculator.calculate(
                List.of(segment("C1", 1000, Validity.VALID)),
                Map.of("C1", CellState.UNSURVEYED), Map.of(), DeckEffect.neutral(), false, 1.0);

        assertThat(result.finalDamage()).isEqualTo(1000.0, within(0.001));
        assertThat(result.cells().get(0).note()).contains("주행 수단");
    }

    @Test
    @DisplayName("반복 방문은 보너스만 줄이고 기본 피해는 유지한다")
    void repeatVisitDecaysBonusNotBase() {
        DamageResult first = calculator.calculate(
                List.of(segment("C1", 1000, Validity.VALID)),
                Map.of("C1", CellState.STALE), Map.of(), DeckEffect.neutral(), true, 1.0);
        DamageResult repeated = calculator.calculate(
                List.of(segment("C1", 1000, Validity.VALID)),
                Map.of("C1", CellState.STALE), Map.of("C1", 4), DeckEffect.neutral(), true, 1.0);

        assertThat(repeated.finalDamage()).isLessThan(first.finalDamage());
        // 보너스가 아무리 깎여도 기본 피해(거리 × 1.0) 아래로는 내려가지 않습니다.
        assertThat(repeated.finalDamage()).isGreaterThanOrEqualTo(1000.0);
        assertThat(repeated.baseDamage()).isEqualTo(first.baseDamage(), within(0.001));
    }

    @Test
    @DisplayName("확정 피해 = 기본 피해 × 기여 배율 × 덱 시너지 × 신뢰도 계수")
    void formulaHolds() {
        DeckEffect deck = new DeckEffect(List.of("A", "B", "C"), 1.30, "상위 시너지",
                Map.of(ContributionType.NEW, 0.5), 0.0, 3);

        DamageResult result = calculator.calculate(
                List.of(segment("C1", 100, Validity.VALID)),
                Map.of("C1", CellState.UNSURVEYED), Map.of(), deck, true, 0.9);

        // 기본 100 × (미탐사 2.0 × 계열보정 1.5) × 시너지 1.30 × 신뢰도 0.9
        assertThat(result.finalDamage()).isEqualTo(100 * 3.0 * 1.30 * 0.9, within(0.001));
        assertThat(result.weightedContributionMultiplier()).isEqualTo(3.0, within(0.001));
    }

    @Test
    @DisplayName("한 셀에 유효 구간이 충분한 비율을 넘지 못하면 셀 전체가 보류로 내려간다")
    void cellValidityUsesSampleRatio() {
        DamageResult result = calculator.calculate(
                List.of(segment("C1", 100, Validity.VALID), segment("C1", 400, Validity.PENDING)),
                Map.of("C1", CellState.UNSURVEYED), Map.of(), DeckEffect.neutral(), true, 1.0);

        assertThat(result.cells()).hasSize(1);
        assertThat(result.cells().get(0).validity()).isEqualTo(Validity.PENDING);
    }

    @Test
    @DisplayName("계열을 섞은 범용 덱은 어떤 상태의 셀을 만나도 최소한의 보정을 받는다")
    void mixedDeckCoversEveryContributionType() {
        DeckEffect mixed = new DeckEffect(List.of("A", "B", "C"), 1.05, "범용 덱",
                Map.of(ContributionType.NEW, 0.2, ContributionType.UPDATE, 0.2,
                        ContributionType.VERIFY, 0.2), 0.0, 1);

        for (CellState state : List.of(CellState.UNSURVEYED, CellState.STALE, CellState.LOW_CONFIDENCE)) {
            DamageResult result = calculator.calculate(
                    List.of(segment("C1", 100, Validity.VALID)),
                    Map.of("C1", state), Map.of(), mixed, true, 1.0);
            assertThat(result.cells().get(0).multiplier())
                    .as("%s 구간에서도 보정을 받아야 합니다", state)
                    .isGreaterThan(state.getContributionMultiplier());
        }
    }
}
