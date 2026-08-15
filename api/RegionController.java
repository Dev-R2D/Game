package com.r2d.api;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.r2d.boss.Boss;
import com.r2d.boss.BossService;
import com.r2d.domain.CellState;
import com.r2d.mission.Mission;
import com.r2d.mission.MissionService;
import com.r2d.region.Region;
import com.r2d.region.RegionRepository;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/regions")
public class RegionController {

    private final RegionRepository regionRepository;
    private final BossService bossService;
    private final MissionService missionService;

    public RegionController(RegionRepository regionRepository, BossService bossService,
                            MissionService missionService) {
        this.regionRepository = regionRepository;
        this.bossService = bossService;
        this.missionService = missionService;
    }

    @GetMapping
    public List<RegionResponse> regions() {
        return regionRepository.findAll().stream().map(RegionResponse::of).toList();
    }

    /** 보스가 없으면 현재 데이터 수요로 새로 만들어 돌려줍니다. */
    @GetMapping("/{code}/boss")
    public BossResponse boss(@PathVariable String code) {
        Boss boss = bossService.activeBossOrCreate(code);
        return BossResponse.of(boss, bossService.participantCount(boss.getId()));
    }

    /** 오늘의 미션. 안전 필터를 통과한 셀에서만 만들어집니다. */
    @GetMapping("/{code}/missions")
    public List<Mission> missions(@PathVariable String code) {
        return missionService.todaysMissions(code);
    }

    /** 지역 정화 진행도. 상태별 셀 분포가 지도 색과 시즌 진행도의 근거입니다. */
    @GetMapping("/{code}/progress")
    public RegionProgressResponse progress(@PathVariable String code) {
        Map<CellState, Long> breakdown = bossService.regionCellBreakdown(code);
        long total = breakdown.values().stream().mapToLong(Long::longValue).sum();
        long clean = breakdown.getOrDefault(CellState.FRESH, 0L);
        Map<String, Long> labelled = breakdown.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().getLabel(), Map.Entry::getValue));
        return new RegionProgressResponse(code, total, clean,
                total == 0 ? 0 : (double) clean / total,
                bossService.calculateDataNeed(code), labelled);
    }

    public record RegionResponse(String code, String name, int activeRiders) {
        static RegionResponse of(Region region) {
            return new RegionResponse(region.getCode(), region.getName(), region.getActiveRiders());
        }
    }

    public record BossResponse(Long id, String regionCode, String name, double maxHp, double currentHp,
                               int phase, int phaseCount, double progressRatio, String tier,
                               String status, double dataNeed, long participants, String note) {
        static BossResponse of(Boss boss, long participants) {
            return new BossResponse(boss.getId(), boss.getRegionCode(), boss.getName(),
                    boss.getMaxHp(), boss.getCurrentHp(), boss.currentPhase(), boss.getPhaseCount(),
                    boss.progressRatio(), boss.getTier().getLabel(), boss.getStatus().getLabel(),
                    boss.getDataNeed(), participants,
                    "체력은 이 지역에 남은 데이터 수요에서 계산됩니다. 활성 라이더 수로 나누지 않습니다.");
        }
    }

    public record RegionProgressResponse(String regionCode, long totalCells, long freshCells,
                                         double purificationRatio, double remainingDataNeed,
                                         Map<String, Long> cellsByState) {
    }
}
