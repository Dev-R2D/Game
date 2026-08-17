package com.r2d.api;

import java.util.List;

import com.r2d.anomaly.AnomalyBattleService;
import com.r2d.auth.CurrentPlayer;
import com.r2d.player.Player;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게임 화면에서 지도의 레드포인트(결함 후보) 하나를 탭해서 몬스터를 처치했을 때 호출하는 API.
 *
 * <p>덱 편성(3장)을 같이 보내면 시너지 배율이 보상에 반영됩니다. 안 보내거나 비워서 보내면
 * 중립 덱(배율 1.0)으로 처리합니다. 주행거리 기반 데미지는 아직 여기 안 붙어 있습니다 —
 * 그건 네비게이터가 들고 있는 실주행 데이터라 별도 연동이 필요합니다.
 */
@RestController
@RequestMapping("/api/v1/anomalies")
public class AnomalyBattleController {

    private final AnomalyBattleService battleService;

    public AnomalyBattleController(AnomalyBattleService battleService) {
        this.battleService = battleService;
    }

    @PostMapping("/{cellId}/battle")
    public BattleResponse battle(@CurrentPlayer Player player, @PathVariable String cellId,
                                 @RequestBody(required = false) BattleRequest request) {
        List<String> deckCardCodes = request == null ? List.of() : request.deckCardCodes();
        AnomalyBattleService.Result result = battleService.battle(player.getId(), cellId, deckCardCodes);
        return new BattleResponse(cellId, result.alreadyBattled(), result.xp(), result.coins(),
                result.regionContributionPoints(), result.deckSynergy(), result.deckSynergyLabel());
    }

    public record BattleRequest(List<String> deckCardCodes) {
    }

    public record BattleResponse(String cellId, boolean alreadyBattled, int xp, int coins,
                                 int regionContributionPoints, double deckSynergy, String deckSynergyLabel) {
    }
}
