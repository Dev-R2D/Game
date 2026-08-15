package com.r2d.cell;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.r2d.config.GameRules;
import com.r2d.domain.CellState;
import com.r2d.observation.CellObservationRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 도로 셀 조회·생성과 관측 반영. */
@Service
public class CellService {

    private final RoadCellRepository cellRepository;
    private final CellObservationRepository observationRepository;
    private final GameRules rules;

    public CellService(RoadCellRepository cellRepository,
                       CellObservationRepository observationRepository,
                       GameRules rules) {
        this.cellRepository = cellRepository;
        this.observationRepository = observationRepository;
        this.rules = rules;
    }

    /**
     * 주행이 지난 셀을 모두 확보합니다. 아직 없는 셀은 미조사 상태로 새로 만듭니다.
     *
     * <p>처음 달린 길에서 셀이 즉석에서 생기는 것이 정상입니다. 미리 전국 격자를 만들어 두면
     * 자전거가 갈 수 없는 곳까지 데이터 수요로 잡히기 때문에, 실제로 통행한 곳만 셀이 됩니다.
     */
    @Transactional
    public Map<String, RoadCell> resolveOrCreate(Collection<String> cellIds, String regionCode) {
        Map<String, RoadCell> resolved = new LinkedHashMap<>();
        for (RoadCell cell : cellRepository.findByCellIdIn(cellIds)) {
            resolved.put(cell.getCellId(), cell);
        }
        for (String cellId : cellIds) {
            resolved.computeIfAbsent(cellId,
                    id -> cellRepository.save(new RoadCell(id, regionCode == null ? "UNASSIGNED" : regionCode)));
        }
        return resolved;
    }

    /** 셀별 관측 시점 상태 스냅샷. 정산 근거로 그대로 저장됩니다. */
    public Map<String, CellState> statesOf(Map<String, RoadCell> cells) {
        Map<String, CellState> states = new HashMap<>();
        cells.forEach((id, cell) -> states.put(id, cell.getState()));
        return states;
    }

    /**
     * 같은 이용자가 최근 기간에 각 셀을 몇 번 지났는지 셉니다. 반복 방문 보너스 감쇠의 근거입니다.
     */
    public Map<String, Integer> recentRepeatCounts(Collection<String> cellIds, Long playerId, Instant now) {
        Instant since = now.minus(rules.cell().repeatWindowDays(), ChronoUnit.DAYS);
        Map<String, Integer> counts = new HashMap<>();
        for (String cellId : cellIds) {
            long count = observationRepository
                    .countByCellIdAndPlayerIdAndObservedAtAfter(cellId, playerId, since);
            if (count > 0) {
                counts.put(cellId, (int) count);
            }
        }
        return counts;
    }

    /**
     * 이 이용자가 이 셀을 처음 관측하는지 확인합니다.
     *
     * <p><b>반드시 이번 주행의 관측을 저장하기 전에</b> 호출해야 합니다. 저장한 뒤에 물어보면
     * 방금 넣은 자기 자신의 행이 걸려 항상 "기존 관측자"로 나오고, 독립 관측자 수가 영원히
     * 늘지 않습니다. 교차검증 판정이 이 값에 걸려 있으므로 순서가 곧 정확성입니다.
     */
    public boolean isFirstTimeObserver(String cellId, Long playerId) {
        return !observationRepository.existsByCellIdAndPlayerId(cellId, playerId);
    }

    /**
     * 유효 관측을 셀에 반영하고 상태를 다시 계산합니다.
     *
     * <p>보류·무효 구간은 여기 들어오지 않습니다. 품질이 확인되지 않은 관측으로 셀 상태를
     * 바꾸면, 실제로는 조사되지 않은 길이 "최근 조사됨"으로 바뀌어 데이터 수요가 사라집니다.
     *
     * @param observerIsNew {@link #isFirstTimeObserver}로 <b>저장 전에</b> 구한 값
     */
    @Transactional
    public void applyValidObservation(RoadCell cell, boolean observerIsNew, double sampleConfidence,
                                      Instant observedAt) {
        cell.applyObservation(observerIsNew, sampleConfidence, observedAt);
        cell.recomputeState(rules.cell().freshnessDays(), rules.cell().lowConfidenceThreshold(), observedAt);
    }

    /** 재확인 주행이 들어와 보수 후 재확인 상태를 해소합니다. */
    @Transactional
    public void clearRecheck(RoadCell cell) {
        cell.clearRecheck();
    }
}
