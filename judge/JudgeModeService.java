package com.r2d.judge;

import java.util.ArrayList;
import java.util.List;

import com.r2d.analysis.AnomalyDetection;
import com.r2d.analysis.AnomalyDetector;
import com.r2d.analysis.QualityGateService;
import com.r2d.analysis.QualityReport;
import com.r2d.analysis.RideSegment;
import com.r2d.analysis.TransportAssessment;
import com.r2d.analysis.TransportModeClassifier;
import com.r2d.cell.RoadCell;
import com.r2d.cell.RoadCellRepository;
import com.r2d.config.GameRules;
import com.r2d.domain.CellState;
import com.r2d.domain.RideMode;
import com.r2d.player.Player;
import com.r2d.player.PlayerRepository;
import com.r2d.ride.ImuWindow;
import com.r2d.ride.ImuWindowRepository;
import com.r2d.ride.RideService;
import com.r2d.ride.RideSession;
import com.r2d.ride.SensorBatchCommand;
import com.r2d.ride.TrackPoint;
import com.r2d.ride.TrackPointRepository;
import com.r2d.settlement.RideSettlement;
import com.r2d.settlement.SettlementService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 심사 모드.
 *
 * <p>실제 위치 권한과 자전거 이동 없이 "주행 → 판정 → 보스 → 팩 → 지도 갱신"을 끝까지
 * 확인할 수 있게 합니다. 재생은 실제 주행과 <b>완전히 같은</b> 수집·정산 경로를 탑니다.
 * 다른 점은 로그 출처 표기와 {@link RideMode#JUDGE_SIM} 표시뿐입니다.
 *
 * <p>복수 이용자 관측은 로그별로 서로 다른 심사용 가상 라이더를 배정해 재현합니다.
 * 이 라이더들은 실제 사용자가 아니며 닉네임에 그대로 드러납니다.
 */
@Service
public class JudgeModeService {

    /** 심사용 가상 라이더 닉네임 접두사. 실제 이용자와 절대 섞이지 않도록 표시에 드러냅니다. */
    private static final String SIM_RIDER_PREFIX = "심사용 가상 라이더 ";

    private final JudgeLogRepository logRepository;
    private final JudgeLogSynthesizer synthesizer;
    private final PlayerRepository playerRepository;
    private final RideService rideService;
    private final SettlementService settlementService;
    private final QualityGateService qualityGate;
    private final TransportModeClassifier transportClassifier;
    private final AnomalyDetector anomalyDetector;
    private final TrackPointRepository trackPointRepository;
    private final ImuWindowRepository imuWindowRepository;
    private final RoadCellRepository cellRepository;
    private final GameRules rules;

    public JudgeModeService(JudgeLogRepository logRepository,
                            JudgeLogSynthesizer synthesizer,
                            PlayerRepository playerRepository,
                            RideService rideService,
                            SettlementService settlementService,
                            QualityGateService qualityGate,
                            TransportModeClassifier transportClassifier,
                            AnomalyDetector anomalyDetector,
                            TrackPointRepository trackPointRepository,
                            ImuWindowRepository imuWindowRepository,
                            RoadCellRepository cellRepository,
                            GameRules rules) {
        this.logRepository = logRepository;
        this.synthesizer = synthesizer;
        this.playerRepository = playerRepository;
        this.rideService = rideService;
        this.settlementService = settlementService;
        this.qualityGate = qualityGate;
        this.transportClassifier = transportClassifier;
        this.anomalyDetector = anomalyDetector;
        this.trackPointRepository = trackPointRepository;
        this.imuWindowRepository = imuWindowRepository;
        this.cellRepository = cellRepository;
        this.rules = rules;
    }

    public List<JudgeLog> availableLogs() {
        return logRepository.findAll();
    }

    /**
     * 로그 한 개를 재생하고 끝까지 정산합니다.
     *
     * @param deckCardCodes 편성할 카드 3장. 비워 두면 중립 덱으로 재생합니다.
     * @param playbackSpeed 클라이언트 재생 배속. 서버 계산에는 영향을 주지 않습니다.
     */
    @Transactional
    public JudgeReplayResult replay(String logId, List<String> deckCardCodes, int playbackSpeed) {
        JudgeLog log = logRepository.get(logId);
        Player rider = simulatedRiderFor(log);

        long startEpochMs = System.currentTimeMillis();
        String idempotencyKey = "judge-" + logId + "-" + startEpochMs;
        SensorBatchCommand batch = synthesizer.synthesize(log, startEpochMs, idempotencyKey);

        RideSession ride = rideService.start(rider, log.regionCode(), deckCardCodes,
                RideMode.JUDGE_SIM, log.logId());
        rideService.ingest(ride.getRideId(), batch);

        // 오버레이는 정산 전에 만듭니다. 정산이 셀 상태를 바꾸므로, 그 전 상태를 보여줘야
        // 심사자가 "이 주행이 무엇을 바꿨는지"를 비교할 수 있습니다.
        JudgeOverlay overlay = buildOverlay(ride.getRideId(), log, playbackSpeed);

        RideSettlement settlement = settlementService.settle(ride.getRideId(), 0.0);

        return new JudgeReplayResult(ride.getRideId(), log.logId(), log.provenance(),
                rider.getNickname(), overlay, settlement, checkpoints(log, settlement));
    }

    /**
     * 심사용 분석 오버레이를 만듭니다.
     *
     * <p>여기 쓰이는 판정기는 정산에서 쓰는 것과 같은 빈입니다. 오버레이가 보여 주는 값과
     * 실제 정산에 들어간 값이 다를 수 없도록 하기 위해서입니다.
     */
    public JudgeOverlay buildOverlay(String rideId, JudgeLog log, int playbackSpeed) {
        List<TrackPoint> points = trackPointRepository.findByRideIdOrderByEpochMsAsc(rideId);
        List<ImuWindow> windows = imuWindowRepository.findByRideIdOrderByEpochMsAsc(rideId);

        QualityReport quality = qualityGate.evaluate(points);
        TransportAssessment transport = transportClassifier.classify(quality.segments(), windows);
        List<AnomalyDetection> detections = anomalyDetector.detect(windows);

        boolean contributionAllowed =
                transport.qualifiesForContribution(rules.transport().bicycleConfirmConfidence());

        List<JudgeOverlay.Frame> frames = new ArrayList<>();
        double cumulativeDistance = 0;
        double cumulativeDamage = 0;
        for (RideSegment segment : quality.segments()) {
            CellState state = cellRepository.findById(segment.cellId())
                    .map(RoadCell::getState)
                    .orElse(CellState.UNSURVEYED);

            cumulativeDistance += segment.damageableDistanceM();
            // 주행 중 클라이언트가 로컬에 누적하는 예상 피해와 같은 방식의 근사치입니다.
            // 서버 확정치는 정산에서만 나오며, 결과 화면은 둘을 다른 배지로 구분합니다.
            double estimate = segment.damageableDistanceM() * rules.damage().perMeter()
                    * (segment.isValid() && contributionAllowed ? state.getContributionMultiplier() : 1.0);
            cumulativeDamage += estimate;

            frames.add(new JudgeOverlay.Frame(segment.endEpochMs(),
                    segment.midLat(), segment.midLon(), segment.cellId(), state,
                    segment.worstAccuracyM(), segment.speedMps(),
                    segment.validity(), segment.reason(), cumulativeDistance, cumulativeDamage));
        }

        return new JudgeOverlay(rideId, log.logId(), log.provenance(),
                log.sourceType() == JudgeLog.SourceType.DERIVED, playbackSpeed,
                transport, List.copyOf(frames), detections);
    }

    /**
     * 로그마다 고정된 심사용 가상 라이더를 배정합니다.
     *
     * <p>파생 로그 C1·C2를 서로 다른 라이더로 재생해야 "서로 다른 이용자의 독립 관측"이라는
     * 확정 조건을 재현할 수 있습니다. 다만 이들은 실제 라이더가 아니며, 닉네임과 원장의
     * 심사 재현 표시로 항상 구분됩니다.
     */
    private Player simulatedRiderFor(JudgeLog log) {
        String nickname = SIM_RIDER_PREFIX + log.logId();
        return playerRepository.findAll().stream()
                .filter(p -> nickname.equals(p.getNickname()))
                .findFirst()
                .orElseGet(() -> playerRepository.save(new Player(nickname)));
    }

    private List<String> checkpoints(JudgeLog log, RideSettlement settlement) {
        List<String> items = new ArrayList<>();
        items.add("로그 출처: " + log.provenance());
        items.add("ride_id " + settlement.getRideId() + " 기준으로 거리·센서·정산이 하나로 연결됩니다.");
        items.add("주행 수단 판별: " + settlement.getTransportMode().getLabel()
                + " (신뢰도 " + String.format("%.2f", settlement.getTransportConfidence()) + ")");
        items.add("유효 " + Math.round(settlement.getValidDistanceM()) + "m · 보류 "
                + Math.round(settlement.getPendingDistanceM()) + "m · 무효 "
                + Math.round(settlement.getInvalidDistanceM()) + "m로 구분되었습니다.");
        items.add("신규 " + settlement.getNewCells() + " · 갱신 " + settlement.getUpdatedCells()
                + " · 검증 " + settlement.getVerifiedCells() + " 셀에 기여했습니다.");
        if (settlement.getAnomalyDiscovered() > 0) {
            items.add("이상 후보 " + settlement.getAnomalyDiscovered() + "건을 새로 발견했습니다(의심 상태).");
        }
        if (settlement.getAnomalyConfirmed() > 0) {
            items.add("교차검증으로 " + settlement.getAnomalyConfirmed() + "건이 의심에서 확정으로 전이했습니다.");
        }
        if (settlement.getAnomalyResolved() > 0) {
            items.add("재확인으로 " + settlement.getAnomalyResolved() + "건이 소멸했습니다.");
        }
        items.add("같은 로그를 다시 재생해도 중복 피해와 중복 보상은 발생하지 않습니다.");
        return items;
    }
}
