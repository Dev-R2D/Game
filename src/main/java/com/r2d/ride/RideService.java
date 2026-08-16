package com.r2d.ride;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.r2d.boss.Boss;
import com.r2d.boss.BossService;
import com.r2d.card.DeckService;
import com.r2d.common.R2dException;
import com.r2d.domain.RideMode;
import com.r2d.player.Player;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주행 세션 생성과 센서 수집.
 *
 * <p>주행 중 서버가 하는 일은 데이터를 받아 쌓는 것뿐입니다. 전투 결과나 보상은 도착 후
 * 정산에서만 계산합니다. 주행 중 실시간 전투 API를 두지 않은 것 자체가 안전 설계입니다.
 */
@Service
public class RideService {

    private final RideSessionRepository rideRepository;
    private final RideBatchRepository batchRepository;
    private final TrackPointRepository trackPointRepository;
    private final ImuWindowRepository imuWindowRepository;
    private final BossService bossService;
    private final DeckService deckService;

    public RideService(RideSessionRepository rideRepository,
                       RideBatchRepository batchRepository,
                       TrackPointRepository trackPointRepository,
                       ImuWindowRepository imuWindowRepository,
                       BossService bossService,
                       DeckService deckService) {
        this.rideRepository = rideRepository;
        this.batchRepository = batchRepository;
        this.trackPointRepository = trackPointRepository;
        this.imuWindowRepository = imuWindowRepository;
        this.bossService = bossService;
        this.deckService = deckService;
    }

    /**
     * 주행을 시작합니다.
     *
     * <p>덱은 출발 전에만 정할 수 있습니다. 주행 중 덱을 바꿀 수 있으면 화면을 볼 이유가
     * 생기므로 API 자체를 열지 않았습니다.
     */
    @Transactional
    public RideSession start(Player player, String regionCode, List<String> deckCardCodes,
                            RideMode mode, String sourceLogId) {
        // 덱이 유효하지 않으면 출발 자체를 막습니다. 정산 시점에 실패하면 주행이 통째로 낭비됩니다.
        deckService.resolve(deckCardCodes);

        Long bossId = null;
        if (regionCode != null && !regionCode.isBlank()) {
            Boss boss = bossService.activeBossOrCreate(regionCode);
            bossId = boss.getId();
        }

        RideSession ride = new RideSession(player, regionCode, bossId, deckCardCodes, mode);
        ride.setSourceLogId(sourceLogId);
        return rideRepository.save(ride);
    }

    /**
     * 센서 배치를 받습니다.
     *
     * @return 이번 호출로 실제 저장이 일어났으면 {@code accepted=true}, 중복이면 false
     */
    @Transactional
    public IngestResult ingest(String rideId, SensorBatchCommand command) {
        RideSession ride = get(rideId);
        ride.assertIngestible();

        if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
            throw R2dException.badRequest("IDEMPOTENCY_KEY_REQUIRED",
                    "재전송 중복을 막기 위해 idempotencyKey가 필요합니다.");
        }

        // 같은 배치가 다시 온 경우. 오류가 아니라 "이미 반영됨"으로 응답합니다.
        Optional<RideBatch> duplicate = batchRepository.findByIdempotencyKey(command.idempotencyKey());
        if (duplicate.isPresent()) {
            return new IngestResult(false, duplicate.get().getBatchSeq(),
                    "이미 반영된 배치입니다(idempotencyKey 중복).");
        }
        if (batchRepository.findByRideIdAndBatchSeq(rideId, command.batchSeq()).isPresent()) {
            return new IngestResult(false, command.batchSeq(),
                    "이미 반영된 배치입니다(batchSeq 중복).");
        }

        List<TrackPoint> points = new ArrayList<>();
        for (SensorBatchCommand.PointCommand p : safe(command.points())) {
            points.add(new TrackPoint(rideId, p.epochMs(), p.lat(), p.lon(), p.accuracyM(), p.speedMps()));
        }
        List<ImuWindow> windows = new ArrayList<>();
        for (SensorBatchCommand.ImuCommand w : safe(command.imuWindows())) {
            windows.add(new ImuWindow(rideId, w.epochMs(), w.lat(), w.lon(), w.durationMs(), w.peakG(),
                    w.rmsG(), w.dominantHz(), w.gyroRmsDps(), w.entrySpeedMps(), w.mountStable()));
        }

        trackPointRepository.saveAll(points);
        imuWindowRepository.saveAll(windows);
        batchRepository.save(new RideBatch(rideId, command.batchSeq(), command.idempotencyKey(),
                points.size(), windows.size()));
        ride.markBatchAccepted(command.batchSeq());

        return new IngestResult(true, command.batchSeq(),
                "위치 " + points.size() + "건, IMU 윈도우 " + windows.size() + "건을 반영했습니다.");
    }

    public RideSession get(String rideId) {
        return rideRepository.findById(rideId)
                .orElseThrow(() -> R2dException.notFound("RIDE_NOT_FOUND", "주행을 찾을 수 없습니다: " + rideId));
    }

    public List<RideSession> historyOf(Player player) {
        return rideRepository.findByPlayerOrderByStartedAtDesc(player);
    }

    /** 앱이 재시작 후 이어받을 수 있도록 진행 중인 주행을 알려줍니다. */
    public Optional<RideSession> activeRideOf(Player player) {
        return rideRepository.findByPlayerAndStatus(player, com.r2d.domain.RideStatus.ACTIVE).stream()
                .findFirst();
    }

    private static <T> List<T> safe(List<T> list) {
        return list == null ? List.of() : list;
    }

    /** 배치 수집 결과. */
    public record IngestResult(boolean accepted, int batchSeq, String message) {
    }
}
