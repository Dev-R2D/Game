package com.r2d.api;

import java.time.Instant;
import java.util.List;

import com.r2d.auth.CurrentPlayer;
import com.r2d.boss.BossReward;
import com.r2d.boss.BossRewardService;
import com.r2d.player.Player;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 보스 처치·단계 돌파 보상.
 *
 * <p>보스가 쓰러진 순간 접속해 있는 사람은 마지막 기여자 한 명뿐이므로, 나머지 참여자의 몫은
 * 수령 대기 상태로 남습니다. 앱은 켜질 때 {@code /pending}을 조회해 알림을 띄우면 됩니다.
 */
@RestController
@RequestMapping("/api/v1/boss-rewards")
public class BossRewardController {

    private final BossRewardService bossRewardService;

    public BossRewardController(BossRewardService bossRewardService) {
        this.bossRewardService = bossRewardService;
    }

    /** 수령 대기 중인 보상. */
    @GetMapping("/pending")
    public List<BossRewardResponse> pending(@CurrentPlayer Player player) {
        return bossRewardService.pendingFor(player).stream().map(BossRewardResponse::of).toList();
    }

    /** 수령 이력 포함 전체. */
    @GetMapping
    public List<BossRewardResponse> history(@CurrentPlayer Player player) {
        return bossRewardService.historyFor(player).stream().map(BossRewardResponse::of).toList();
    }

    @PostMapping("/{rewardId}/claim")
    public BossRewardResponse claim(@CurrentPlayer Player player, @PathVariable Long rewardId) {
        return BossRewardResponse.of(bossRewardService.claim(player, rewardId));
    }

    /** 밀린 보상을 한 번에 수령합니다. */
    @PostMapping("/claim-all")
    public ClaimAllResponse claimAll(@CurrentPlayer Player player) {
        List<BossReward> claimed = bossRewardService.claimAll(player);
        return new ClaimAllResponse(
                claimed.size(),
                claimed.stream().mapToInt(BossReward::getXp).sum(),
                claimed.stream().mapToInt(BossReward::getCoins).sum(),
                claimed.stream().mapToInt(BossReward::getRegionContributionPoints).sum(),
                claimed.stream().map(BossRewardResponse::of).toList());
    }

    public record BossRewardResponse(Long id, Long bossId, String regionCode, String kind, int phase,
                                     double contributionRatio, int contributedCells,
                                     int xp, int coins, int regionContributionPoints,
                                     boolean claimed, String reason,
                                     Instant createdAt, Instant claimedAt, String note) {
        static BossRewardResponse of(BossReward r) {
            return new BossRewardResponse(r.getId(), r.getBossId(), r.getRegionCode(),
                    r.getKind().getLabel(), r.getPhase(), r.getContributionRatio(),
                    r.getContributedCells(), r.getXp(), r.getCoins(), r.getRegionContributionPoints(),
                    r.isClaimed(), r.getReason(), r.getCreatedAt(), r.getClaimedAt(),
                    "보상은 막타가 아니라 누적 기여 비율로 배분됩니다.");
        }
    }

    public record ClaimAllResponse(int count, int totalXp, int totalCoins,
                                   int totalRegionContributionPoints,
                                   List<BossRewardResponse> rewards) {
    }
}
