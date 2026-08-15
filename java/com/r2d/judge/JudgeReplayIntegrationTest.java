package com.r2d.judge;

import java.util.List;

import com.r2d.anomaly.AnomalyCandidate;
import com.r2d.anomaly.AnomalyCandidateRepository;
import com.r2d.boss.Boss;
import com.r2d.boss.BossService;
import com.r2d.cell.RoadCell;
import com.r2d.cell.RoadCellRepository;
import com.r2d.domain.AnomalyState;
import com.r2d.domain.RideMode;
import com.r2d.settlement.RideSettlement;
import com.r2d.settlement.SettlementService;

import com.r2d.support.DatabaseCleaner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 심사 모드 통합 테스트.
 *
 * <p>문서가 심사자에게 약속한 순서를 그대로 검증합니다:
 * 주행 → 판정 → 보스 → 팩 → 지도 갱신, 그리고 의심에서 확정으로의 상태 전이.
 */
@SpringBootTest
class JudgeReplayIntegrationTest {

    private static final String REGION = "DONGTAN2";

    @Autowired
    private JudgeModeService judgeModeService;

    @Autowired
    private BossService bossService;

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private AnomalyCandidateRepository anomalyRepository;

    @Autowired
    private RoadCellRepository cellRepository;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @BeforeEach
    void resetDatabase() {
        databaseCleaner.resetToSeedState();
    }

    @Test
    @DisplayName("신규 구간 로그를 재생하면 미탐사 셀 기여와 보스 피해가 끝까지 정산된다")
    void replayNewRouteSettlesEndToEnd() {
        Boss before = bossService.activeBossOrCreate(REGION);
        double hpBefore = before.getCurrentHp();

        JudgeReplayResult result = judgeModeService.replay("A", List.of(), 30);
        RideSettlement settlement = result.settlement();

        assertThat(settlement.getMode()).isEqualTo(RideMode.JUDGE_SIM);
        assertThat(settlement.getSourceLogId()).isEqualTo("A");
        assertThat(settlement.getValidDistanceM()).isGreaterThan(1000);
        assertThat(settlement.getNewCells()).isPositive();
        assertThat(settlement.getFinalDamage()).isPositive();
        assertThat(settlement.getAppliedDamage()).isPositive();

        assertThat(bossService.get(before.getId()).getCurrentHp()).isLessThan(hpBefore);
        assertThat(result.overlay().frames()).isNotEmpty();
        assertThat(result.provenance()).contains("테스트 로그");
    }

    @Test
    @DisplayName("파생 로그를 실측 로그로 표기하지 않는다")
    void derivedLogsAreLabelled() {
        List<JudgeLog> logs = judgeModeService.availableLogs();

        assertThat(logs).anyMatch(log -> log.sourceType() == JudgeLog.SourceType.DERIVED);
        assertThat(logs)
                .filteredOn(log -> log.sourceType() == JudgeLog.SourceType.DERIVED)
                .allSatisfy(log -> {
                    assertThat(log.derivedFrom()).isNotBlank();
                    assertThat(log.provenance()).contains("파생 로그");
                    assertThat(log.sourceLabel()).contains("원본 데이터가 아닙니다");
                });
        assertThat(logs).noneMatch(log -> log.sourceType() == JudgeLog.SourceType.REAL_RIDE);
    }

    @Test
    @DisplayName("한 번의 관측은 의심에 머물고, 다른 라이더의 독립 관측이 들어와야 확정된다")
    void crossVerificationDrivesConfirmation() {
        JudgeReplayResult first = judgeModeService.replay("B", List.of(), 30);

        assertThat(first.settlement().getAnomalyDiscovered()).isPositive();
        assertThat(first.settlement().getAnomalyConfirmed()).isZero();
        assertThat(anomalyRepository.findByStateIn(List.of(AnomalyState.SUSPECT))).isNotEmpty();

        JudgeReplayResult second = judgeModeService.replay("C1", List.of(), 30);

        assertThat(second.settlement().getAnomalyConfirmed()).isPositive();
        assertThat(anomalyRepository.findByStateIn(List.of(AnomalyState.CONFIRMED)))
                .isNotEmpty()
                .allSatisfy(candidate -> assertThat(candidate.getDistinctObservers())
                        .isGreaterThanOrEqualTo(2));
    }

    @Test
    @DisplayName("서로 다른 라이더가 같은 셀을 지나면 독립 관측자 수가 올라간다")
    void distinctObserverCountGrows() {
        judgeModeService.replay("B", List.of(), 30);

        List<RoadCell> afterFirst = cellRepository.findAll().stream()
                .filter(c -> c.getObservationCount() > 0)
                .toList();
        assertThat(afterFirst).isNotEmpty();
        assertThat(afterFirst).allSatisfy(cell ->
                assertThat(cell.getDistinctObserverCount()).isEqualTo(1));

        // C1은 B와 같은 경로를 다른 심사용 라이더가 달린 것이므로 독립 관측자가 2가 되어야 합니다.
        judgeModeService.replay("C1", List.of(), 30);

        assertThat(cellRepository.findAll())
                .filteredOn(cell -> cell.getObservationCount() >= 2)
                .isNotEmpty()
                .allSatisfy(cell -> assertThat(cell.getDistinctObserverCount()).isEqualTo(2));
    }

    @Test
    @DisplayName("정상 시설물 파형만 있는 로그는 이상 후보를 만들지 않는다")
    void normalFeaturesDoNotCreateCandidates() {
        long before = anomalyRepository.count();

        JudgeReplayResult result = judgeModeService.replay("D", List.of(), 30);

        assertThat(result.settlement().getAnomalyDiscovered()).isZero();
        assertThat(anomalyRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("신호 품질이 나쁜 구간은 무효·보류로 구분되어 결과에 남는다")
    void degradedSignalIsSeparatedInResult() {
        JudgeReplayResult result = judgeModeService.replay("E", List.of(), 30);
        RideSettlement settlement = result.settlement();

        assertThat(settlement.getInvalidDistanceM()).isPositive();
        assertThat(settlement.getPendingDistanceM()).isPositive();
        assertThat(settlement.getNotes())
                .anyMatch(note -> note.contains("무효 구간"))
                .anyMatch(note -> note.contains("보류 구간"));
    }

    @Test
    @DisplayName("같은 주행을 다시 정산해도 중복 피해와 중복 보상이 생기지 않는다")
    void settlingTwiceIsIdempotent() {
        JudgeReplayResult result = judgeModeService.replay("A", List.of(), 30);
        String rideId = result.rideId();

        Boss boss = bossService.get(result.settlement().getBossId());
        double hpAfterFirst = boss.getCurrentHp();

        RideSettlement resettled = settlementService.settle(rideId, 0.0);

        assertThat(resettled.getId()).isEqualTo(result.settlement().getId());
        assertThat(resettled.getAppliedDamage()).isEqualTo(result.settlement().getAppliedDamage());
        assertThat(bossService.get(boss.getId()).getCurrentHp()).isEqualTo(hpAfterFirst);
    }

    @Test
    @DisplayName("확정된 후보는 이상 없는 재주행이 반복되면 소멸한다")
    void repeatedCleanPassesResolveCandidate() {
        judgeModeService.replay("B", List.of(), 30);
        judgeModeService.replay("C1", List.of(), 30);

        List<AnomalyCandidate> confirmed = anomalyRepository.findByStateIn(List.of(AnomalyState.CONFIRMED));
        assertThat(confirmed).isNotEmpty();

        // 소멸 기준은 이상 패턴 없이 지나간 서로 다른 주행 3회입니다.
        for (int i = 0; i < 3; i++) {
            judgeModeService.replay("D", List.of(), 30);
        }

        assertThat(anomalyRepository.findByStateIn(List.of(AnomalyState.RESOLVED))).isNotEmpty();
    }
}
