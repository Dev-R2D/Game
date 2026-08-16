package com.r2d.mission;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.r2d.boss.Boss;
import com.r2d.boss.BossService;
import com.r2d.cell.RoadCell;
import com.r2d.cell.RoadCellRepository;
import com.r2d.domain.CardLine;
import com.r2d.domain.CellState;

import org.springframework.stereotype.Service;

/**
 * 오늘의 미션 생성과 안전 필터.
 *
 * <p>데이터 부족도만으로 미션을 만들지 않습니다. 자전거로 접근할 수 있고 경로가 안전한 셀만
 * 후보가 되며, 야간 원정 보너스는 아예 만들지 않습니다. 속도 기반 목표도 두지 않습니다.
 */
@Service
public class MissionService {

    /** 한 미션에 담을 대표 셀 수. 화면에 보여줄 만큼만 잘라 보냅니다. */
    private static final int SAMPLE_LIMIT = 12;

    /** 미션 하나가 성립하기 위한 최소 후보 셀 수. */
    private static final int MIN_CELLS_FOR_MISSION = 3;

    private final RoadCellRepository cellRepository;
    private final BossService bossService;

    public MissionService(RoadCellRepository cellRepository, BossService bossService) {
        this.cellRepository = cellRepository;
        this.bossService = bossService;
    }

    /**
     * 지역의 오늘 미션 목록.
     *
     * <p>후보가 부족하면 미션 수가 줄어듭니다. 억지로 채우면 이미 조사된 길이나 갈 수 없는
     * 곳으로 유도하게 됩니다.
     */
    public List<Mission> todaysMissions(String regionCode) {
        List<RoadCell> candidates = cellRepository.findMissionCandidates(regionCode).stream()
                .filter(this::passesSafetyFilter)
                .toList();

        Map<CellState, List<RoadCell>> byState = candidates.stream()
                .collect(Collectors.groupingBy(RoadCell::getState));

        List<Mission> missions = new ArrayList<>();

        List<RoadCell> unsurveyed = byState.getOrDefault(CellState.UNSURVEYED, List.of());
        if (unsurveyed.size() >= MIN_CELLS_FOR_MISSION) {
            missions.add(new Mission("A", "미탐사 구간 정찰",
                    "아직 아무도 기록하지 않은 " + unsurveyed.size() + "개 구간이 남아 있습니다.",
                    MissionType.EXPLORE, unsurveyed.size(), 20, 35, CardLine.TRAILBLAZE,
                    "탐사 팩 등급 하한 상승 · 신규 기여 배율 최대",
                    sample(unsurveyed),
                    "처음 가는 길입니다. 경로를 미리 확인하고, 주행 중에는 화면을 보지 마세요."));
        }

        List<RoadCell> stale = byState.getOrDefault(CellState.STALE, List.of());
        if (stale.size() >= MIN_CELLS_FOR_MISSION) {
            missions.add(new Mission("B", "노후 데이터 갱신",
                    "마지막 관측이 오래된 " + stale.size() + "개 구간을 다시 확인합니다.",
                    MissionType.UPDATE, stale.size(), 15, 30, CardLine.SCOUT,
                    "갱신 기여 배율 · 정찰 계열 시너지",
                    sample(stale),
                    "평소 다니던 길이라면 그대로 달리기만 하면 됩니다."));
        }

        List<RoadCell> needsVerification = new ArrayList<>();
        needsVerification.addAll(byState.getOrDefault(CellState.LOW_CONFIDENCE, List.of()));
        needsVerification.addAll(byState.getOrDefault(CellState.RECHECK_REQUIRED, List.of()));
        if (needsVerification.size() >= MIN_CELLS_FOR_MISSION) {
            missions.add(new Mission("V", "주간 검증 체인",
                    "신뢰도가 낮거나 보수 후 재확인이 필요한 " + needsVerification.size() + "개 구간입니다.",
                    MissionType.VERIFY, needsVerification.size(), 15, 25, CardLine.VERIFICATION,
                    "검증 기여 배율 · 서브몹 확정·소멸 전이 기여",
                    sample(needsVerification),
                    "고정 출퇴근 코스와 겹치면 따로 돌아갈 필요가 없습니다."));
        }

        // 공동 보스전은 상시 카드입니다. 아직 보스가 만들어지지 않은 지역이면 지금의 데이터 수요로
        // 생성해서라도 내려보냅니다. 첫 실행에서 "오늘의 미션"이 비어 있으면 게임이 시작되지 않습니다.
        missions.add(bossMission(bossService.activeBossOrCreate(regionCode)));

        return missions;
    }

    private Mission bossMission(Boss boss) {
        long participants = bossService.participantCount(boss.getId());
        return new Mission("C", "공동 보스전",
                boss.getName() + " · 참여 " + participants + "명 · 체력 " + boss.currentPhase()
                        + "/" + boss.getPhaseCount() + " 단계",
                MissionType.BOSS, (int) Math.round(boss.getDataNeed()), 25, 35, CardLine.ENDURANCE,
                "공동 보스 기여도 랭킹 반영 · 완주 보상",
                List.of(),
                "혼자 완주하지 못해도 참여 구간만큼 기여가 남습니다. 무리해서 달리지 마세요.");
    }

    /**
     * 안전 필터.
     *
     * <p>자동차전용도로·통행 금지·계단·사유지·공사·통제·도달 불가 셀은 데이터가 아무리 부족해도
     * 미션 후보가 되지 않습니다. 데이터 수요보다 안전이 먼저입니다.
     */
    private boolean passesSafetyFilter(RoadCell cell) {
        return cell.isBikeAccessible() && cell.getExclusionReason() == null;
    }

    private List<String> sample(List<RoadCell> cells) {
        return cells.stream().limit(SAMPLE_LIMIT).map(RoadCell::getCellId).toList();
    }
}
