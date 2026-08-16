package com.r2d.api;

import java.time.Instant;
import java.util.List;

import com.r2d.auth.CurrentPlayer;
import com.r2d.boss.Boss;
import com.r2d.boss.BossReward;
import com.r2d.boss.BossRewardRepository;
import com.r2d.boss.BossService;
import com.r2d.player.Player;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 특정 보스 조회.
 *
 * <p>{@code GET /regions/{code}/boss}는 <b>진행 중인</b> 보스를 돌려주므로, 처치가 끝나면
 * 곧바로 다음 보스가 잡힙니다. 방금 쓰러뜨린 보스의 결과 화면을 그리려면 정산 응답에 담긴
 * {@code bossId}로 이 API를 호출해야 합니다.
 */
@RestController
@RequestMapping("/api/v1/bosses")
public class BossController {

    private final BossService bossService;
    private final BossRewardRepository rewardRepository;

    public BossController(BossService bossService, BossRewardRepository rewardRepository) {
        this.bossService = bossService;
        this.rewardRepository = rewardRepository;
    }

    /** 처치 완료된 보스도 그대로 조회됩니다. 처치 축하 화면에 씁니다. */
    @GetMapping("/{bossId}")
    public BossDetailResponse get(@CurrentPlayer Player player, @PathVariable Long bossId) {
        Boss boss = bossService.get(bossId);

        List<BossReward> mine = rewardRepository.findByBossId(bossId).stream()
                .filter(r -> r.getPlayerId().equals(player.getId()))
                .toList();

        return new BossDetailResponse(
                boss.getId(), boss.getRegionCode(), boss.getName(),
                boss.getMaxHp(), boss.getCurrentHp(),
                boss.currentPhase(), boss.getPhaseCount(), boss.progressRatio(),
                boss.getTier().getLabel(), boss.getStatus().getLabel(), boss.isCleared(),
                boss.getDataNeed(),
                bossService.participantCount(bossId),
                bossService.personalContributionRatio(bossId, player.getId()),
                mine.stream().map(MyRewardLine::of).toList(),
                boss.getClearedAt());
    }

    public record BossDetailResponse(Long id, String regionCode, String name,
                                     double maxHp, double currentHp,
                                     int phase, int phaseCount, double progressRatio,
                                     String tier, String status, boolean cleared,
                                     double dataNeed, long participants,
                                     double myContributionRatio,
                                     List<MyRewardLine> myRewards,
                                     Instant clearedAt) {
    }

    public record MyRewardLine(Long rewardId, String kind, int phase, int xp, int coins,
                               int regionContributionPoints, boolean claimed, String reason) {
        static MyRewardLine of(BossReward r) {
            return new MyRewardLine(r.getId(), r.getKind().getLabel(), r.getPhase(),
                    r.getXp(), r.getCoins(), r.getRegionContributionPoints(),
                    r.isClaimed(), r.getReason());
        }
    }
}
