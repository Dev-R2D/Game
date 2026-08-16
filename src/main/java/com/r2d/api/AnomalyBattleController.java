package com.r2d.api;

import com.r2d.anomaly.AnomalyBattleService;
import com.r2d.auth.CurrentPlayer;
import com.r2d.player.Player;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게임 화면에서 지도의 레드포인트(결함 후보) 하나를 탭해서 몬스터를 처치했을 때 호출하는 API.
 *
 * <p>실제 데미지 계산(주행거리·덱시너지 기반)은 아직 여기 안 붙어 있습니다. 지금은 확정 여부에
 * 따른 고정 보상만 지급합니다 — 프론트의 몬스터 배틀 연출은 그대로 두고, "처치" 버튼을 눌렀을 때
 * 이 API 하나만 호출하면 됩니다.
 */
@RestController
@RequestMapping("/api/v1/anomalies")
public class AnomalyBattleController {

    private final AnomalyBattleService battleService;

    public AnomalyBattleController(AnomalyBattleService battleService) {
        this.battleService = battleService;
    }

    @PostMapping("/{cellId}/battle")
    public BattleResponse battle(@CurrentPlayer Player player, @PathVariable String cellId) {
        AnomalyBattleService.Result result = battleService.battle(player.getId(), cellId);
        return new BattleResponse(cellId, result.alreadyBattled(), result.xp(), result.coins(),
                result.regionContributionPoints());
    }

    public record BattleResponse(String cellId, boolean alreadyBattled, int xp, int coins,
                                 int regionContributionPoints) {
    }
}
