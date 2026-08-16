package com.r2d.anomaly;

import com.r2d.cell.RoadCellRepository;
import com.r2d.common.R2dException;
import com.r2d.domain.AnomalyState;
import com.r2d.reward.RewardService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게임 화면에서 "레드포인트 하나 = 몬스터 하나"를 배틀했을 때의 정산.
 *
 * <p>지금은 주행 전체 단위 정산({@code SettlementService})과 별개로, 결함 후보 하나 단위로
 * 고정 보상을 지급하는 가장 단순한 형태입니다. 확정된 결함(state=CONFIRMED)일수록 더 큰 보상을
 * 주고, 아직 의심 단계(SUSPECT)인 후보는 절반만 줍니다 — 의심 단계는 검증되지 않았으니
 * 확정을 유도하는 방향으로도 자연스럽게 맞습니다.
 *
 * <p>보스 보상과 같은 방식으로 일일 상한을 적용하지 않습니다({@link RewardService#grantBossReward}).
 * (cellId, playerId) 유니크 제약으로 같은 후보를 반복 배틀해도 보상은 한 번만 나갑니다.
 */
@Service
public class AnomalyBattleService {

    private static final int CONFIRMED_XP = 40;
    private static final int CONFIRMED_COINS = 20;
    private static final int CONFIRMED_REGION_POINTS = 15;

    private final AnomalyCandidateRepository candidateRepository;
    private final AnomalyBattleRepository battleRepository;
    private final RoadCellRepository roadCellRepository;
    private final RewardService rewardService;

    public AnomalyBattleService(AnomalyCandidateRepository candidateRepository,
                                AnomalyBattleRepository battleRepository,
                                RoadCellRepository roadCellRepository,
                                RewardService rewardService) {
        this.candidateRepository = candidateRepository;
        this.battleRepository = battleRepository;
        this.roadCellRepository = roadCellRepository;
        this.rewardService = rewardService;
    }

    @Transactional
    public Result battle(Long playerId, String cellId) {
        AnomalyCandidate candidate = candidateRepository.findByCellId(cellId)
                .orElseThrow(() -> R2dException.notFound("ANOMALY_NOT_FOUND", "해당 위치에 결함 후보가 없습니다."));

        // 이미 배틀했으면 재지급하지 않고 기존 결과를 그대로 돌려줍니다(재전송에도 안전).
        var existing = battleRepository.findByCellIdAndPlayerId(cellId, playerId);
        if (existing.isPresent()) {
            AnomalyBattle b = existing.get();
            return new Result(true, b.getXpGranted(), b.getCoinsGranted(), b.getRegionContributionPointsGranted());
        }

        boolean confirmed = candidate.getState() == AnomalyState.CONFIRMED;
        int xp = confirmed ? CONFIRMED_XP : CONFIRMED_XP / 2;
        int coins = confirmed ? CONFIRMED_COINS : CONFIRMED_COINS / 2;
        int points = confirmed ? CONFIRMED_REGION_POINTS : CONFIRMED_REGION_POINTS / 2;

        String regionCode = roadCellRepository.findById(cellId).map(c -> c.getRegionCode()).orElse(null);
        rewardService.grantBossReward(playerId, regionCode, xp, coins, points);
        battleRepository.save(new AnomalyBattle(cellId, playerId, xp, coins, points));

        return new Result(false, xp, coins, points);
    }

    public record Result(boolean alreadyBattled, int xp, int coins, int regionContributionPoints) {
    }
}
