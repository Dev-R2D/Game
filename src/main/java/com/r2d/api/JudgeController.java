package com.r2d.api;

import java.util.List;

import com.r2d.judge.JudgeLog;
import com.r2d.judge.JudgeModeService;
import com.r2d.judge.JudgeReplayResult;
import com.r2d.observation.CellObservationRepository;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 심사 모드 API.
 *
 * <p>실제 위치 권한과 자전거 이동 없이 핵심 루프를 끝까지 확인하기 위한 것입니다.
 * 재생 결과는 실제 주행과 같은 파이프라인에서 나오지만, 원장에는 항상 심사 재현으로 남습니다.
 */
@RestController
@RequestMapping("/api/v1/judge")
public class JudgeController {

    private final JudgeModeService judgeModeService;
    private final CellObservationRepository observationRepository;

    public JudgeController(JudgeModeService judgeModeService,
                           CellObservationRepository observationRepository) {
        this.judgeModeService = judgeModeService;
        this.observationRepository = observationRepository;
    }

    /** 재생 가능한 로그 목록. 각 로그의 출처와 목적이 함께 내려갑니다. */
    @GetMapping("/logs")
    public List<LogResponse> logs() {
        return judgeModeService.availableLogs().stream().map(LogResponse::of).toList();
    }

    /**
     * 로그를 재생하고 끝까지 정산합니다.
     *
     * <p>권장 순서: A(신규 구간) → B(이상 후보) → C1(교차검증) → C2(중복 제거) → D 3회(소멸).
     */
    @PostMapping("/replay")
    public ReplayResponse replay(@RequestBody ReplayRequest request) {
        int speed = request.playbackSpeed() <= 0 ? 30 : request.playbackSpeed();
        JudgeReplayResult result = judgeModeService.replay(request.logId(), request.deckCardCodes(), speed);
        return new ReplayResponse(
                result.rideId(),
                result.logId(),
                result.provenance(),
                result.simulatedRider(),
                result.overlay(),
                SettlementResponse.of(result.settlement(),
                        observationRepository.findByRideId(result.rideId())),
                result.checkpoints(),
                "이 화면은 심사용 분석 화면입니다. 실제 주행 중에는 이 정보를 표시하지 않습니다.");
    }

    public record ReplayRequest(String logId, List<String> deckCardCodes, int playbackSpeed) {
    }

    public record ReplayResponse(String rideId, String logId, String provenance, String simulatedRider,
                                 com.r2d.judge.JudgeOverlay overlay, SettlementResponse settlement,
                                 List<String> checkpoints, String banner) {
    }

    public record LogResponse(String logId, String title, String sourceType, String provenance,
                              String purpose, String regionCode, boolean derived, String derivedFrom) {
        static LogResponse of(JudgeLog log) {
            return new LogResponse(log.logId(), log.title(), log.sourceType().getLabel(),
                    log.provenance(), log.purpose(), log.regionCode(),
                    log.sourceType() == JudgeLog.SourceType.DERIVED, log.derivedFrom());
        }
    }
}
