package com.r2d.api;

import java.time.Instant;
import java.util.List;

import com.r2d.anomaly.AnomalyCandidate;
import com.r2d.anomaly.AnomalyCandidateRepository;
import com.r2d.cell.RoadCell;
import com.r2d.cell.RoadCellRepository;
import com.r2d.common.R2dException;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 지도 레이어.
 *
 * <p>여기서 내려보내는 것은 <b>셀 단위로 집계된 도로 상태</b>뿐입니다. 다른 이용자의 전체 경로,
 * 원시 센서, 정확한 위치, 기기 식별자는 어떤 파라미터로도 조회할 수 없습니다.
 *
 * <p>확정된 이상만 위험 레이어에 반영합니다. 의심 단계는 아직 검증되지 않았으므로 안전 경로
 * 안내의 근거로 쓰지 않습니다.
 */
@RestController
@RequestMapping("/api/v1/map")
public class MapController {

    /** 한 번에 조회할 수 있는 최대 사각 영역(도). 대략 5.5km × 4.4km입니다. */
    private static final double MAX_SPAN_DEG = 0.05;

    private final RoadCellRepository cellRepository;
    private final AnomalyCandidateRepository anomalyRepository;

    public MapController(RoadCellRepository cellRepository, AnomalyCandidateRepository anomalyRepository) {
        this.cellRepository = cellRepository;
        this.anomalyRepository = anomalyRepository;
    }

    @GetMapping("/cells")
    public List<CellResponse> cells(@RequestParam double minLat, @RequestParam double maxLat,
                                    @RequestParam double minLon, @RequestParam double maxLon) {
        assertBounds(minLat, maxLat, minLon, maxLon);
        return cellRepository.findInBoundingBox(minLat, maxLat, minLon, maxLon).stream()
                .map(CellResponse::of).toList();
    }

    @GetMapping("/anomalies")
    public List<AnomalyResponse> anomalies(@RequestParam double minLat, @RequestParam double maxLat,
                                           @RequestParam double minLon, @RequestParam double maxLon) {
        assertBounds(minLat, maxLat, minLon, maxLon);
        return anomalyRepository.findActiveInBoundingBox(minLat, maxLat, minLon, maxLon).stream()
                .map(AnomalyResponse::of).toList();
    }

    /**
     * 조회 범위를 제한합니다.
     *
     * <p>범위 제한이 없으면 전국 셀을 한 번에 받아 이동 패턴을 역산할 수 있습니다. 셀 단위로
     * 집계했더라도 전량 조회는 허용하지 않습니다.
     */
    private void assertBounds(double minLat, double maxLat, double minLon, double maxLon) {
        if (maxLat <= minLat || maxLon <= minLon) {
            throw R2dException.badRequest("INVALID_BOUNDS", "조회 영역이 올바르지 않습니다.");
        }
        if (maxLat - minLat > MAX_SPAN_DEG || maxLon - minLon > MAX_SPAN_DEG) {
            throw R2dException.badRequest("BOUNDS_TOO_LARGE",
                    "한 번에 조회할 수 있는 영역은 " + MAX_SPAN_DEG + "도까지입니다.");
        }
    }

    public record CellResponse(String cellId, double lat, double lon, String state, double confidence,
                               int observationCount, int distinctObservers, Instant lastObservedAt,
                               boolean bikeAccessible, String exclusionReason) {
        static CellResponse of(RoadCell cell) {
            return new CellResponse(cell.getCellId(), cell.getCenterLat(), cell.getCenterLon(),
                    cell.getState().getLabel(), cell.getConfidence(), cell.getObservationCount(),
                    cell.getDistinctObserverCount(), cell.getLastObservedAt(),
                    cell.isBikeAccessible(), cell.getExclusionReason());
        }
    }

    public record AnomalyResponse(String cellId, double lat, double lon, String anomalyClass, String state,
                                  double confidence, int distinctObservers, Instant firstSeenAt,
                                  boolean usableForSafetyLayer, String note) {
        static AnomalyResponse of(AnomalyCandidate candidate) {
            boolean confirmed = candidate.getState() == com.r2d.domain.AnomalyState.CONFIRMED;
            return new AnomalyResponse(candidate.getCellId(), candidate.getLat(), candidate.getLon(),
                    candidate.getAnomalyClass().getLabel(), candidate.getState().getLabel(),
                    candidate.getConfidence(), candidate.getDistinctObservers(), candidate.getFirstSeenAt(),
                    confirmed,
                    confirmed ? "검증된 정보입니다. 위험 레이어와 안전 경로에 반영됩니다."
                            : "아직 검증되지 않은 의심 상태입니다. 안전 경로 안내에는 쓰지 않습니다.");
        }
    }
}
