package com.r2d.api;

import java.time.Instant;
import java.util.List;

import com.r2d.common.R2dException;
import com.r2d.auth.CurrentPlayer;
import com.r2d.domain.RideMode;
import com.r2d.observation.CellObservationRepository;
import com.r2d.player.Player;
import com.r2d.ride.RideService;
import com.r2d.ride.RideSession;
import com.r2d.ride.SensorBatchCommand;
import com.r2d.settlement.RideSettlement;
import com.r2d.settlement.SettlementService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * 주행 API.
 *
 * <p>주행 중 호출되는 것은 배치 업로드 하나뿐입니다. 전투·보상·팩 관련 엔드포인트는 주행 중
 * 호출할 수 없습니다. 서버가 그런 API를 제공하지 않는 것이 화면을 볼 이유를 없애는 가장
 * 확실한 방법입니다.
 */
@RestController
@RequestMapping("/api/v1/rides")
public class RideController {

    private final RideService rideService;
    private final SettlementService settlementService;
    private final CellObservationRepository observationRepository;

    public RideController(RideService rideService, SettlementService settlementService,
                          CellObservationRepository observationRepository) {
        this.rideService = rideService;
        this.settlementService = settlementService;
        this.observationRepository = observationRepository;
    }

    /** 출발. 보스와 덱은 여기서만 정할 수 있습니다. */
    @PostMapping
    public RideResponse start(@CurrentPlayer Player player,
                              @Valid @RequestBody StartRideRequest request) {
        RideSession ride = rideService.start(player, request.regionCode(), request.deckCardCodes(),
                RideMode.REAL, null);
        return RideResponse.of(ride);
    }

    /**
     * 센서 배치 업로드.
     *
     * <p>같은 배치가 다시 와도 오류가 아닙니다. 오프라인 큐의 재전송은 정상 동작이므로
     * {@code accepted=false}로 "이미 반영됨"을 알려 줍니다.
     */
    @PostMapping("/{rideId}/batches")
    public RideService.IngestResult ingest(@CurrentPlayer Player player,
                                           @PathVariable String rideId,
                                           @RequestBody SensorBatchCommand batch) {
        assertOwner(player, rideId);
        return rideService.ingest(rideId, batch);
    }

    /**
     * 주행 종료와 정산.
     *
     * <p>이미 정산된 주행에 다시 요청하면 새 계산 없이 같은 결과가 돌아옵니다.
     */
    @PostMapping("/{rideId}/finish")
    public SettlementResponse finish(@CurrentPlayer Player player,
                                     @PathVariable String rideId,
                                     @RequestBody(required = false) FinishRideRequest request) {
        assertOwner(player, rideId);
        double estimate = request == null ? 0.0 : request.clientEstimatedDamage();
        RideSettlement settlement = settlementService.settle(rideId, estimate);
        return SettlementResponse.of(settlement, observationRepository.findByRideId(rideId));
    }

    @GetMapping("/{rideId}/settlement")
    public SettlementResponse settlement(@CurrentPlayer Player player,
                                         @PathVariable String rideId) {
        assertOwner(player, rideId);
        RideSettlement settlement = settlementService.findByRideId(rideId)
                .orElseThrow(() -> R2dException.notFound("SETTLEMENT_NOT_FOUND",
                        "아직 정산되지 않은 주행입니다: " + rideId));
        return SettlementResponse.of(settlement, observationRepository.findByRideId(rideId));
    }

    @GetMapping("/{rideId}")
    public RideResponse get(@CurrentPlayer Player player, @PathVariable String rideId) {
        return RideResponse.of(assertOwner(player, rideId));
    }

    /** 앱이 재시작됐을 때 이어받을 주행. 화면 전환·강제 종료에도 ride_id가 유지되어야 합니다. */
    @GetMapping("/active")
    public RideResponse active(@CurrentPlayer Player player) {
        return rideService.activeRideOf(player)
                .map(RideResponse::of)
                .orElseThrow(() -> R2dException.notFound("NO_ACTIVE_RIDE", "진행 중인 주행이 없습니다."));
    }

    private RideSession assertOwner(Player player, String rideId) {
        RideSession ride = rideService.get(rideId);
        if (!ride.getPlayer().getId().equals(player.getId())) {
            // 남의 주행이 존재한다는 사실 자체를 알리지 않기 위해 404로 응답합니다.
            throw R2dException.notFound("RIDE_NOT_FOUND", "주행을 찾을 수 없습니다: " + rideId);
        }
        return ride;
    }

    public record StartRideRequest(String regionCode, List<String> deckCardCodes) {
    }

    public record FinishRideRequest(double clientEstimatedDamage) {
    }

    public record RideResponse(String rideId, String regionCode, Long bossId, List<String> deckCardCodes,
                               String mode, String status, Instant startedAt, Instant endedAt,
                               int nextBatchSeq) {
        static RideResponse of(RideSession ride) {
            return new RideResponse(ride.getRideId(), ride.getRegionCode(), ride.getBossId(),
                    ride.getDeckCardCodes(), ride.getMode().getLabel(), ride.getStatus().getLabel(),
                    ride.getStartedAt(), ride.getEndedAt(), ride.getNextBatchSeq());
        }
    }
}
