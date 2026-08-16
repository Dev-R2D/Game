package com.r2d.boss;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.r2d.cell.RoadCell;
import com.r2d.cell.RoadCellRepository;
import com.r2d.common.R2dException;
import com.r2d.config.GameRules;
import com.r2d.domain.CellState;
import com.r2d.region.Region;
import com.r2d.region.RegionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지역 공동 보스 운영.
 *
 * <p>보스는 임의로 만들지 않고 그 지역에 실제로 남아 있는 데이터 수요에서 만들어집니다.
 * 지역이 정화될수록 다음 보스의 HP가 작아지고, 결국 새 보스가 생기지 않는 것이 정상입니다.
 */
@Service
public class BossService {

    private final BossRepository bossRepository;
    private final BossContributionRepository contributionRepository;
    private final RoadCellRepository cellRepository;
    private final RegionRepository regionRepository;
    private final GameRules rules;

    public BossService(BossRepository bossRepository,
                       BossContributionRepository contributionRepository,
                       RoadCellRepository cellRepository,
                       RegionRepository regionRepository,
                       GameRules rules) {
        this.bossRepository = bossRepository;
        this.contributionRepository = contributionRepository;
        this.cellRepository = cellRepository;
        this.regionRepository = regionRepository;
        this.rules = rules;
    }

    /** 지역의 진행 중 보스를 찾고, 없으면 현재 데이터 수요로 새로 만듭니다. */
    @Transactional
    public Boss activeBossOrCreate(String regionCode) {
        return bossRepository.findFirstByRegionCodeAndStatusOrderByCreatedAtDesc(regionCode, BossStatus.ACTIVE)
                .orElseGet(() -> createBoss(regionCode));
    }

    public Optional<Boss> findActiveBoss(String regionCode) {
        return bossRepository.findFirstByRegionCodeAndStatusOrderByCreatedAtDesc(regionCode, BossStatus.ACTIVE);
    }

    public Boss get(Long bossId) {
        return bossRepository.findById(bossId)
                .orElseThrow(() -> R2dException.notFound("BOSS_NOT_FOUND", "보스를 찾을 수 없습니다: " + bossId));
    }

    /**
     * 지역 데이터 필요량으로 보스를 생성합니다.
     *
     * <p>보스 최대 HP = ceil(지역 데이터 필요량 × 필요량당 HP × 등급 배율)
     */
    @Transactional
    public Boss createBoss(String regionCode) {
        Region region = regionRepository.findById(regionCode)
                .orElseThrow(() -> R2dException.notFound("REGION_NOT_FOUND", "지역을 찾을 수 없습니다: " + regionCode));

        double dataNeed = calculateDataNeed(regionCode);
        BossTier tier = BossTier.forActiveRiders(region.getActiveRiders());

        double rawHp = dataNeed * rules.boss().hpPerNeedUnit() * tier.getHpScale();
        double maxHp = Math.max(rules.boss().minHp(), Math.ceil(rawHp));

        String name = region.getName() + " 오염원";
        return bossRepository.save(new Boss(regionCode, name, maxHp, tier.getPhaseCount(), tier, dataNeed));
    }

    /**
     * 지역 데이터 필요량.
     *
     * <p>미조사·재확인·갱신·낮은 신뢰도 셀에 서로 다른 가중치를 적용한 합계입니다.
     * 자전거로 갈 수 없는 셀은 아무리 데이터가 없어도 필요량에 넣지 않습니다. 갈 수 없는 곳을
     * 목표로 만들면 안전 규칙과 충돌하기 때문입니다.
     */
    public double calculateDataNeed(String regionCode) {
        double need = 0;
        for (RoadCell cell : cellRepository.findByRegionCode(regionCode)) {
            if (!cell.isBikeAccessible()) {
                continue;
            }
            need += cell.getState().getDataNeedWeight();
        }
        return need;
    }

    /** 지역 정화 진행도: 상태별 셀 분포. 지도 색과 시즌 진행도의 근거입니다. */
    public Map<CellState, Long> regionCellBreakdown(String regionCode) {
        Map<CellState, Long> breakdown = new EnumMap<>(CellState.class);
        for (CellState state : CellState.values()) {
            breakdown.put(state, cellRepository.countByRegionCodeAndState(regionCode, state));
        }
        return breakdown;
    }

    /**
     * 확정 피해를 보스에 반영합니다.
     *
     * <p>같은 rideId로 두 번 호출되면 두 번째는 아무것도 하지 않고 기존 기여를 그대로 돌려줍니다.
     * 네트워크 재전송과 동시 요청에서 중복 피해가 생기지 않아야 한다는 요구를 여기서 지킵니다.
     *
     * @return 실제로 반영된 기여 원장
     */
    @Transactional
    public BossContribution applyDamage(Long bossId, String rideId, Long playerId, double damage,
                                        int contributedCells) {
        Optional<BossContribution> existing = contributionRepository.findByRideId(rideId);
        if (existing.isPresent()) {
            return existing.get();
        }

        Boss boss = get(bossId);
        double applied = boss.applyDamage(damage);
        return contributionRepository.save(
                new BossContribution(bossId, rideId, playerId, applied, contributedCells));
    }

    /** 이 보스에 대한 개인 기여 비율(0~1). 결과 화면의 "내 기여도" 표시에 씁니다. */
    public double personalContributionRatio(Long bossId, Long playerId) {
        double total = contributionRepository.sumDamageByBoss(bossId);
        if (total <= 0) {
            return 0;
        }
        return contributionRepository.sumDamageByBossAndPlayer(bossId, playerId) / total;
    }

    public long participantCount(Long bossId) {
        return contributionRepository.countParticipants(bossId);
    }

    /** 참여 구간 기준 기여 순위. 속도나 막타가 아니라 누적 피해와 기여 셀 수로만 정렬합니다. */
    public List<BossContribution> contributions(Long bossId) {
        return contributionRepository.findByBossId(bossId);
    }
}
