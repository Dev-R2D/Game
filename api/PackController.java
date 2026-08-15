package com.r2d.api;

import java.util.List;
import java.util.Map;

import com.r2d.auth.CurrentPlayer;
import com.r2d.player.Player;
import com.r2d.reward.ExplorationPack;
import com.r2d.reward.ExplorationPackRepository;
import com.r2d.reward.PackService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/packs")
public class PackController {

    private final PackService packService;
    private final ExplorationPackRepository packRepository;

    public PackController(PackService packService, ExplorationPackRepository packRepository) {
        this.packService = packService;
        this.packRepository = packRepository;
    }

    /** 개봉 대기 중인 팩. 주행이 끝난 뒤에만 존재합니다. */
    @GetMapping("/pending")
    public List<PackResponse> pending(@CurrentPlayer Player player) {
        return packRepository.findByPlayerIdAndOpenedFalse(player.getId()).stream()
                .map(PackResponse::of).toList();
    }

    @PostMapping("/{packId}/open")
    public PackResponse open(@CurrentPlayer Player player, @PathVariable Long packId) {
        return PackResponse.of(packService.open(player, packId));
    }

    /** 등급별 구성 확률 공개. 확률형 요소가 있는 이상 앱에서 항상 확인할 수 있어야 합니다. */
    @GetMapping("/odds")
    public Map<String, Object> odds() {
        return packService.publishedOdds();
    }

    public record PackResponse(Long id, String rideId, String grade, String gradeReason,
                               List<String> cardCodes, int coins, boolean opened) {
        static PackResponse of(ExplorationPack pack) {
            return new PackResponse(pack.getId(), pack.getRideId(), pack.getGrade().getLabel(),
                    pack.getGradeReason(),
                    // 개봉 전에는 내용물을 내려보내지 않습니다. 결과가 미리 노출되면 연출이 무의미해집니다.
                    pack.isOpened() ? pack.getCardCodes() : List.of(),
                    pack.isOpened() ? pack.getCoins() : 0,
                    pack.isOpened());
        }
    }
}
