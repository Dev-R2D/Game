package com.r2d.settlement;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.r2d.analysis.AnomalyDetection;
import com.r2d.analysis.AnomalyDetector;
import com.r2d.analysis.QualityGateService;
import com.r2d.analysis.QualityReport;
import com.r2d.analysis.TransportAssessment;
import com.r2d.analysis.TransportModeClassifier;
import com.r2d.anomaly.AnomalyLifecycleService;
import com.r2d.anomaly.AnomalyOutcome;
import com.r2d.boss.Boss;
import com.r2d.boss.BossContribution;
import com.r2d.boss.BossReward;
import com.r2d.boss.BossRewardService;
import com.r2d.boss.BossService;
import com.r2d.card.DeckEffect;
import com.r2d.card.DeckService;
import com.r2d.cell.CellService;
import com.r2d.cell.RoadCell;
import com.r2d.common.R2dException;
import com.r2d.config.GameRules;
import com.r2d.domain.CellState;
import com.r2d.domain.ContributionType;
import com.r2d.domain.RideMode;
import com.r2d.domain.Validity;
import com.r2d.observation.CellObservation;
import com.r2d.observation.CellObservationRepository;
import com.r2d.player.Player;
import com.r2d.reward.ExplorationPack;
import com.r2d.reward.PackService;
import com.r2d.reward.RewardService;
import com.r2d.ride.ImuWindow;
import com.r2d.ride.ImuWindowRepository;
import com.r2d.ride.RideSession;
import com.r2d.ride.RideSessionRepository;
import com.r2d.ride.TrackPoint;
import com.r2d.ride.TrackPointRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주행 종료 정산 파이프라인.
 *
 * <p>순서는 문서의 코어 루프와 같습니다:
 * 품질 검사 → 셀 매칭 → 유효성 판정 → 기여 이벤트 → 보스·보상 정산.
 *
 * <p>실제 주행과 심사 재현은 <b>같은 메서드</b>를 탑니다. 심사 모드가 별도 경로로 계산한다면
 * 심사에서 확인한 결과가 실제 동작을 보증하지 못하기 때문입니다. 두 경로의 차이는
 * {@link RideMode}와 로그 출처 표기뿐입니다.
 */
@Service
public class SettlementService {

    private final RideSessionRepository rideRepository;
    private final TrackPointRepository trackPointRepository;
    private final ImuWindowRepository imuWindowRepository;
    private final QualityGateService qualityGate;
    private final TransportModeClassifier transportClassifier;
    private final AnomalyDetector anomalyDetector;
    private final AnomalyLifecycleService anomalyLifecycle;
    private final CellService cellService;
    private final CellObservationRepository observationRepository;
    private final DeckService deckService;
    private final DamageCalculator damageCalculator;
    private final BossService bossService;
    private final BossRewardService bossRewardService;
    private final PackService packService;
    private final RewardService rewardService;
    private final RideSettlementRepository settlementRepository;
    private final GameRules rules;

    public SettlementService(RideSessionRepository rideRepository,
                             TrackPointRepository trackPointRepository,
                             ImuWindowRepository imuWindowRepository,
                             QualityGateService qualityGate,
                             TransportModeClassifier transportClassifier,
                             AnomalyDetector anomalyDetector,
                             AnomalyLifecycleService anomalyLifecycle,
                             CellService cellService,
                             CellObservationRepository observationRepository,
                             DeckService deckService,
                             DamageCalculator damageCalculator,
                             BossService bossService,
                             BossRewardService bossRewardService,
                             PackService packService,
                             RewardService rewardService,
                             RideSettlementRepository settlementRepository,
                             GameRules rules) {
        this.rideRepository = rideRepository;
        this.trackPointRepository = trackPointRepository;
        this.imuWindowRepository = imuWindowRepository;
        this.qualityGate = qualityGate;
        this.transportClassifier = transportClassifier;
        this.anomalyDetector = anomalyDetector;
        this.anomalyLifecycle = anomalyLifecycle;
        this.cellService = cellService;
        this.observationRepository = observationRepository;
        this.deckService = deckService;
        this.damageCalculator = damageCalculator;
        this.bossService = bossService;
        this.bossRewardService = bossRewardService;
        this.packService = packService;
        this.rewardService = rewardService;
        this.settlementRepository = settlementRepository;
        this.rules = rules;
    }

    /**
     * 주행을 종료하고 정산합니다.
     *
     * <p>이미 정산된 주행에 다시 요청이 오면 새로 계산하지 않고 기존 결과를 그대로 돌려줍니다.
     * 오프라인 큐의 재전송과 동시 요청에서 중복 피해·중복 보상이 생기지 않아야 하기 때문입니다.
     *
     * @param clientEstimatedDamage 클라이언트가 주행 중 로컬에 누적한 예상 피해. 표시용이며 정산에는 쓰지 않습니다.
     */
    @Transactional
    public RideSettlement settle(String rideId, double clientEstimatedDamage) {
        Optional<RideSettlement> existing = settlementRepository.findByRideId(rideId);
        if (existing.isPresent()) {
            return existing.get();
        }

        RideSession ride = rideRepository.findById(rideId)
                .orElseThrow(() -> R2dException.notFound("RIDE_NOT_FOUND", "주행을 찾을 수 없습니다: " + rideId));
        ride.assertIngestible();

        Player player = ride.getPlayer();
        Instant now = Instant.now();
        List<String> notes = new ArrayList<>();

        // 1. 품질 검사 — 위치 신뢰도 검증
        List<TrackPoint> points = trackPointRepository.findByRideIdOrderByEpochMsAsc(rideId);
        List<ImuWindow> imuWindows = imuWindowRepository.findByRideIdOrderByEpochMsAsc(rideId);
        QualityReport quality = qualityGate.evaluate(points);
        notes.addAll(quality.notes());

        // 2. 주행 수단 신뢰도
        TransportAssessment transport = transportClassifier.classify(quality.segments(), imuWindows);
        boolean contributionAllowed =
                transport.qualifiesForContribution(rules.transport().bicycleConfirmConfidence());
        if (!contributionAllowed) {
            notes.add("주행 수단이 자전거로 확정되지 않아 데이터 기여를 보류했습니다: " + transport.reason());
        }

        // 3. 셀 매칭 — 실제로 지난 셀만 확보하고, 관측 시점의 상태를 스냅샷으로 고정
        Set<String> cellIds = new LinkedHashSet<>();
        quality.segments().forEach(segment -> cellIds.add(segment.cellId()));
        Map<String, RoadCell> cells = cellService.resolveOrCreate(cellIds, ride.getRegionCode());
        Map<String, CellState> statesAtObservation = cellService.statesOf(cells);
        Map<String, Integer> repeats = cellService.recentRepeatCounts(cellIds, player.getId(), now);

        // 4. 확정 피해 계산
        DeckEffect deck = deckService.resolve(ride.getDeckCardCodes());
        double confidenceCoefficient = confidenceCoefficientOf(quality, transport, contributionAllowed);
        DamageResult damage = damageCalculator.calculate(quality.segments(), statesAtObservation, repeats,
                deck, contributionAllowed, confidenceCoefficient);

        // 5. 기여 이벤트 기록 및 셀 상태 갱신
        Set<String> validCells = new LinkedHashSet<>();
        for (CellContribution contribution : damage.cells()) {
            // 독립 관측자 판정은 이번 관측을 저장하기 전에 해야 합니다. 저장 후에는 방금 넣은
            // 자기 행이 걸려 언제나 "기존 관측자"가 되고, 교차검증 지표가 올라가지 않습니다.
            boolean firstTimeObserver = cellService.isFirstTimeObserver(contribution.cellId(), player.getId());

            observationRepository.save(new CellObservation(rideId, contribution.cellId(), player.getId(),
                    contribution.distanceM(), contribution.validity(), contribution.state(),
                    contribution.type(), contribution.multiplier(), contribution.damage(),
                    contribution.recentRepeatCount(), contribution.note(), now));

            if (contribution.validity() == Validity.VALID && contributionAllowed) {
                validCells.add(contribution.cellId());
                RoadCell cell = cells.get(contribution.cellId());
                if (cell != null) {
                    boolean wasRecheck = cell.getState() == CellState.RECHECK_REQUIRED;
                    cellService.applyValidObservation(cell, firstTimeObserver, confidenceCoefficient, now);
                    if (wasRecheck) {
                        cellService.clearRecheck(cell);
                    }
                }
            }
        }

        // 6. 서브몹 상태 전이 — 교차검증이 성장 연출이 되는 지점
        List<AnomalyDetection> detections = anomalyDetector.detect(imuWindows);
        AnomalyOutcome anomaly = contributionAllowed
                ? anomalyLifecycle.apply(rideId, player, detections, validCells,
                        ride.getMode() == RideMode.JUDGE_SIM, now)
                : AnomalyOutcome.empty();

        // 7. 보스 정산 — 서버가 유일한 권위
        double appliedDamage = 0;
        Long bossId = ride.getBossId();
        if (bossId != null) {
            BossContribution contribution = bossService.applyDamage(bossId, rideId, player.getId(),
                    damage.finalDamage(), validCells.size());
            appliedDamage = contribution.getDamage();
            Boss boss = bossService.get(bossId);

            // 처치·단계 돌파 보상은 참여자 전원 몫을 만들어 두고, 각자 수령할 때 지급합니다.
            // 마지막 일격을 넣은 사람만 얻는 보너스는 만들지 않습니다.
            List<BossReward> issued = bossRewardService.issueAfterSettlement(boss);
            for (BossReward mine : bossRewardService.justCreatedFor(issued, player.getId())) {
                notes.add(mine.getKind().getLabel() + " 보상이 도착했습니다 — " + mine.getReason()
                        + " (XP +" + mine.getXp() + ", 코인 +" + mine.getCoins() + ")");
            }
            if (boss.isCleared() && !issued.isEmpty()) {
                notes.add(boss.getName() + " 처치 완료. 참여한 " + issued.size()
                        + "명 전원에게 기여도만큼 보상이 배분되었습니다.");
            }
        }

        // 8. 보상 — 게임 재화와 지역기여 점수를 서로 다른 원장에 적립
        RewardService.Granted granted = rewardService.grant(player.getId(), ride.getRegionCode(),
                damage, anomaly, contributionAllowed);
        if (granted.dailyCapApplied()) {
            notes.add("일일 보상 상한에 도달해 일부 보상이 제한되었습니다. 주행과 도로 데이터는 정상 반영됩니다.");
        }

        Optional<ExplorationPack> pack = packService.createPack(player, rideId, damage, anomaly, deck);
        if (pack.isEmpty()) {
            notes.add("오늘 탐사 팩 상한에 도달해 팩이 지급되지 않았습니다.");
        }

        ride.settle(now);

        RideSettlement settlement = RideSettlement.builder()
                .ride(rideId, player.getId(), ride.getRegionCode(), bossId, ride.getMode(), ride.getSourceLogId())
                .distances(quality.totalDistanceM(), quality.validDistanceM(),
                        quality.pendingDistanceM(), quality.invalidDistanceM())
                .transport(transport.mode(), transport.confidence(), transport.reason())
                .quality(quality.gpsQualityScore(), confidenceCoefficient)
                .damage(damage.baseDamage(), damage.weightedContributionMultiplier(), damage.deckSynergy(),
                        damage.finalDamage(), appliedDamage, clientEstimatedDamage)
                .cells((int) damage.countByType(ContributionType.NEW),
                        (int) damage.countByType(ContributionType.UPDATE),
                        (int) damage.countByType(ContributionType.VERIFY),
                        (int) damage.cells().stream().filter(c -> c.validity() == Validity.INVALID).count())
                .anomalies(anomaly.discovered(), anomaly.confirmed(), anomaly.resolved())
                .rewards(pack.map(ExplorationPack::getGrade).orElse(null),
                        pack.map(ExplorationPack::getId).orElse(null),
                        granted.xp(), granted.coins(), granted.regionContributionPoints(),
                        granted.dailyCapApplied())
                .notes(notes)
                .build();

        return settlementRepository.save(settlement);
    }

    /**
     * 신뢰도 계수.
     *
     * <p>GPS 정확도와 주행 수단 신뢰도를 합쳐 0.5~1.0 범위로 만듭니다. 하한이 0인 대신 0.5인
     * 이유는, 품질이 나쁜 날에도 기본 성장은 남겨야 이용자가 무리해서 좋은 신호를 만들려 하지
     * 않기 때문입니다. 수단이 확정되지 않은 주행은 하한으로 고정합니다.
     */
    private double confidenceCoefficientOf(QualityReport quality, TransportAssessment transport,
                                           boolean contributionAllowed) {
        double floor = rules.quality().minConfidenceCoefficient();
        if (!contributionAllowed) {
            return floor;
        }
        double raw = floor + (1.0 - floor) * (0.6 * quality.gpsQualityScore() + 0.4 * transport.confidence());
        return Math.max(floor, Math.min(1.0, raw));
    }

    public Optional<RideSettlement> findByRideId(String rideId) {
        return settlementRepository.findByRideId(rideId);
    }

    public List<RideSettlement> historyOf(Long playerId) {
        return settlementRepository.findByPlayerIdOrderBySettledAtDesc(playerId);
    }
}
