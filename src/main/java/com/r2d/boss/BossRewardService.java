package com.r2d.boss;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.r2d.common.R2dException;
import com.r2d.player.Player;
import com.r2d.reward.RewardService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보스 처치·단계 돌파 보상.
 *
 * <p><b>막타 보너스가 아닙니다.</b> 마지막 일격을 넣은 사람에게 몰아주면 "누가 끝내느냐" 경쟁이
 * 생기고, 그건 속도 경쟁과 무리한 주행으로 이어집니다. 그래서 보상은 참여자 <b>전원</b>에게
 * 각자의 누적 기여 비율대로 나눕니다. 마지막 일격을 넣었더라도 참여가 적었다면 적게 받습니다.
 *
 * <p>혼자 완주하지 못하는 지역 공동 보스라도 손해가 없도록, 단계를 넘길 때마다 중간 보상을 줍니다.
 */
@Service
public class BossRewardService {

    /** 처치 보상 풀: 보스 최대 HP에 비례. 큰 보스일수록 큰 보상. */
    private static final double DEFEAT_XP_PER_HP = 0.02;
    private static final double DEFEAT_COIN_PER_HP = 0.01;
    private static final double DEFEAT_POINT_PER_NEED = 3.0;

    /** 단계 중간 보상은 처치 보상 풀의 이 비율을 단계 수로 나눠 씁니다. */
    private static final double PHASE_POOL_RATIO = 0.25;

    /**
     * 참여자 최소 보장 XP.
     *
     * <p>기여가 아주 작아도 "참여했는데 0을 받는" 경험은 만들지 않습니다. 참여 자체를
     * 인정하는 것이 협동형 보스의 취지입니다.
     */
    private static final int MIN_XP = 10;

    private final BossRewardRepository rewardRepository;
    private final BossContributionRepository contributionRepository;
    private final RewardService rewardService;

    public BossRewardService(BossRewardRepository rewardRepository,
                             BossContributionRepository contributionRepository,
                             RewardService rewardService) {
        this.rewardRepository = rewardRepository;
        this.contributionRepository = contributionRepository;
        this.rewardService = rewardService;
    }

    /**
     * 정산 직후 보스 상태를 보고 필요한 보상을 만듭니다.
     *
     * <p>보상은 여기서 <b>지급되지 않고 생성만</b> 됩니다. 참여자 대부분은 이 순간 접속해 있지
     * 않기 때문입니다. 실제 지급은 각자 {@link #claim}을 호출할 때 일어납니다.
     *
     * @return 이번 호출로 새로 만들어진 보상 목록
     */
    @Transactional
    public List<BossReward> issueAfterSettlement(Boss boss) {
        List<BossReward> created = new ArrayList<>();

        if (boss.claimDefeatRewardIssuance()) {
            created.addAll(issue(boss, BossRewardKind.BOSS_DEFEAT, 0, 1.0));
            return created;
        }

        // 한 번의 정산이 여러 단계를 넘길 수 있으므로 더 넘길 단계가 없을 때까지 반복합니다.
        int phase;
        while ((phase = boss.claimNextRewardablePhase()) > 0) {
            double share = PHASE_POOL_RATIO / Math.max(1, boss.getPhaseCount());
            created.addAll(issue(boss, BossRewardKind.PHASE_CLEAR, phase, share));
        }
        return created;
    }

    private List<BossReward> issue(Boss boss, BossRewardKind kind, int phase, double poolShare) {
        List<BossContribution> contributions = contributionRepository.findByBossId(boss.getId());
        if (contributions.isEmpty()) {
            return List.of();
        }

        // 같은 사람이 여러 번 주행했을 수 있으므로 플레이어 단위로 합칩니다.
        Map<Long, double[]> perPlayer = new LinkedHashMap<>();
        double totalDamage = 0;
        for (BossContribution c : contributions) {
            double[] acc = perPlayer.computeIfAbsent(c.getPlayerId(), k -> new double[2]);
            acc[0] += c.getDamage();
            acc[1] += c.getContributedCells();
            totalDamage += c.getDamage();
        }
        if (totalDamage <= 0) {
            return List.of();
        }

        long xpPool = Math.round(boss.getMaxHp() * DEFEAT_XP_PER_HP * poolShare);
        long coinPool = Math.round(boss.getMaxHp() * DEFEAT_COIN_PER_HP * poolShare);
        long pointPool = Math.round(boss.getDataNeed() * DEFEAT_POINT_PER_NEED * poolShare);

        List<BossReward> created = new ArrayList<>();
        for (Map.Entry<Long, double[]> entry : perPlayer.entrySet()) {
            Long playerId = entry.getKey();
            double damage = entry.getValue()[0];
            int cells = (int) entry.getValue()[1];
            double ratio = damage / totalDamage;

            // 이미 만들어진 보상이면 건너뜁니다(동시 정산·재시도 대비).
            if (rewardRepository.findByBossIdAndPlayerIdAndKindAndPhase(
                    boss.getId(), playerId, kind, phase).isPresent()) {
                continue;
            }

            int xp = Math.max(MIN_XP, (int) Math.round(xpPool * ratio));
            int coins = (int) Math.round(coinPool * ratio);
            int points = (int) Math.round(pointPool * ratio);

            String reason = kind == BossRewardKind.BOSS_DEFEAT
                    ? boss.getName() + " 처치 · 내 기여도 " + percent(ratio)
                    : boss.getName() + " " + phase + "단계 돌파 · 내 기여도 " + percent(ratio);

            created.add(rewardRepository.save(new BossReward(boss.getId(), playerId, boss.getRegionCode(),
                    kind, phase, ratio, damage, cells, xp, coins, points, reason)));
        }
        return created;
    }

    /** 수령 대기 중인 보상. 앱이 켜질 때 보여 줍니다. */
    public List<BossReward> pendingFor(Player player) {
        return rewardRepository.findByPlayerIdAndClaimedFalseOrderByCreatedAtDesc(player.getId());
    }

    public List<BossReward> historyFor(Player player) {
        return rewardRepository.findByPlayerIdOrderByCreatedAtDesc(player.getId());
    }

    /**
     * 보상 수령.
     *
     * <p><b>일일 상한을 적용하지 않습니다.</b> 상한은 "무제한 반복 주행 경쟁"을 막으려는 장치인데,
     * 보스 보상은 지역당 한 번뿐이라 반복해서 얻을 수 없습니다. 여기에 상한을 걸면 그날 많이 달린
     * 사람이 오히려 보스 보상을 못 받는 이상한 결과가 됩니다.
     */
    @Transactional
    public BossReward claim(Player player, Long rewardId) {
        BossReward reward = rewardRepository.findById(rewardId)
                .orElseThrow(() -> R2dException.notFound("BOSS_REWARD_NOT_FOUND", "보상을 찾을 수 없습니다."));
        if (!reward.getPlayerId().equals(player.getId())) {
            // 남의 보상이 존재한다는 사실 자체를 알리지 않습니다.
            throw R2dException.notFound("BOSS_REWARD_NOT_FOUND", "보상을 찾을 수 없습니다.");
        }

        reward.claim(Instant.now());
        rewardService.grantBossReward(player.getId(), reward.getRegionCode(),
                reward.getXp(), reward.getCoins(), reward.getRegionContributionPoints());
        return reward;
    }

    /** 한 번에 전부 수령. 밀린 보상이 여러 개일 때 앱에서 쓰기 좋게 열어 둡니다. */
    @Transactional
    public List<BossReward> claimAll(Player player) {
        List<BossReward> pending = pendingFor(player);
        List<BossReward> claimed = new ArrayList<>(pending.size());
        for (BossReward reward : pending) {
            reward.claim(Instant.now());
            rewardService.grantBossReward(player.getId(), reward.getRegionCode(),
                    reward.getXp(), reward.getCoins(), reward.getRegionContributionPoints());
            claimed.add(reward);
        }
        return claimed;
    }

    /** 이번 정산으로 이 플레이어가 받은 보상(있으면). 결과 화면 연출용입니다. */
    public List<BossReward> justCreatedFor(List<BossReward> created, Long playerId) {
        return created.stream().filter(r -> r.getPlayerId().equals(playerId)).toList();
    }

    private static String percent(double ratio) {
        return String.format("%.1f%%", ratio * 100);
    }
}
