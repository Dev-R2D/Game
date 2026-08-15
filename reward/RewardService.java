package com.r2d.reward;

import com.r2d.anomaly.AnomalyOutcome;
import com.r2d.domain.ContributionType;
import com.r2d.settlement.DamageResult;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주행 보상 지급.
 *
 * <p>게임 재화와 지역기여 점수를 서로 다른 원장에 씁니다. 두 원장 사이에 교환이나 이체 경로는
 * 없으며, 지역기여 점수는 검증된 안전 주행에서만 적립됩니다.
 */
@Service
public class RewardService {

    /** 확정 피해 이만큼마다 XP 1. */
    private static final double DAMAGE_PER_XP = 50.0;

    private final RewardLedgerRepository rewardRepository;
    private final RegionContributionLedgerRepository contributionRepository;
    private final DailyCounterService dailyCounterService;

    public RewardService(RewardLedgerRepository rewardRepository,
                         RegionContributionLedgerRepository contributionRepository,
                         DailyCounterService dailyCounterService) {
        this.rewardRepository = rewardRepository;
        this.contributionRepository = contributionRepository;
        this.dailyCounterService = dailyCounterService;
    }

    /**
     * 한 주행의 보상을 지급합니다.
     *
     * @param dataContributionAllowed 주행 수단 신뢰도가 기준을 넘겼는지. false면 지역기여 점수는 적립하지 않습니다.
     */
    @Transactional
    public Granted grant(Long playerId, String regionCode, DamageResult damage, AnomalyOutcome anomaly,
                         boolean dataContributionAllowed) {
        int requestedXp = (int) Math.round(damage.finalDamage() / DAMAGE_PER_XP)
                + anomaly.discovered() * 20
                + anomaly.confirmed() * 60
                + anomaly.resolved() * 40;
        int grantedXp = dailyCounterService.grantXp(playerId, requestedXp);

        // 코인은 상한 대상이 아닙니다. 게임 내부 재화일 뿐이고 경쟁 지표가 아니기 때문입니다.
        int coins = (int) Math.round(damage.finalDamage() / 200.0);

        RewardLedger ledger = rewardRepository.findByPlayerId(playerId)
                .orElseGet(() -> rewardRepository.save(new RewardLedger(playerId)));
        ledger.add(grantedXp, coins);

        int grantedPoints = 0;
        if (dataContributionAllowed && regionCode != null) {
            int requestedPoints = (int) (damage.countByType(ContributionType.NEW) * 3
                    + damage.countByType(ContributionType.UPDATE) * 2
                    + damage.countByType(ContributionType.VERIFY) * 2)
                    + anomaly.confirmed() * 10;
            grantedPoints = dailyCounterService.grantContributionPoints(playerId, requestedPoints);
            if (grantedPoints > 0) {
                RegionContributionLedger regionLedger = contributionRepository
                        .findByPlayerIdAndRegionCode(playerId, regionCode)
                        .orElseGet(() -> contributionRepository.save(
                                new RegionContributionLedger(playerId, regionCode)));
                regionLedger.accrue(grantedPoints);
            }
        }

        boolean capped = grantedXp < requestedXp;
        return new Granted(grantedXp, coins, grantedPoints, capped);
    }

    /**
     * 보스 보상 지급.
     *
     * <p>일반 주행 보상과 달리 <b>일일 상한을 적용하지 않습니다.</b> 상한은 무제한 반복 주행
     * 경쟁을 막는 장치인데, 보스 보상은 지역당 한 번뿐이라 반복해서 얻을 수 없습니다.
     * 여기에 상한을 걸면 그날 열심히 달린 사람이 오히려 보스 보상을 못 받게 됩니다.
     */
    @Transactional
    public void grantBossReward(Long playerId, String regionCode, int xp, int coins, int points) {
        RewardLedger ledger = rewardRepository.findByPlayerId(playerId)
                .orElseGet(() -> rewardRepository.save(new RewardLedger(playerId)));
        ledger.add(xp, coins);

        if (points > 0 && regionCode != null) {
            RegionContributionLedger regionLedger = contributionRepository
                    .findByPlayerIdAndRegionCode(playerId, regionCode)
                    .orElseGet(() -> contributionRepository.save(
                            new RegionContributionLedger(playerId, regionCode)));
            regionLedger.accrue(points);
        }
    }

    /** 실제로 지급된 보상. 요청량과 다르면 일일 상한에 걸린 것입니다. */
    public record Granted(int xp, int coins, int regionContributionPoints, boolean dailyCapApplied) {
    }
}
