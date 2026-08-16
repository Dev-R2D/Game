package com.r2d.api;

import java.util.List;

import com.r2d.card.OwnedCard;
import com.r2d.auth.CurrentPlayer;
import com.r2d.card.OwnedCardRepository;
import com.r2d.player.Player;
import com.r2d.player.PlayerService;
import com.r2d.reward.DailyCounter;
import com.r2d.reward.DailyCounterService;
import com.r2d.reward.RegionContributionLedger;
import com.r2d.reward.RegionContributionLedgerRepository;
import com.r2d.reward.RewardLedger;
import com.r2d.reward.RewardLedgerRepository;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/players")
public class PlayerController {

    private final PlayerService playerService;
    private final RewardLedgerRepository rewardRepository;
    private final RegionContributionLedgerRepository contributionRepository;
    private final DailyCounterService dailyCounterService;
    private final OwnedCardRepository ownedCardRepository;

    public PlayerController(PlayerService playerService,
                            RewardLedgerRepository rewardRepository,
                            RegionContributionLedgerRepository contributionRepository,
                            DailyCounterService dailyCounterService,
                            OwnedCardRepository ownedCardRepository) {
        this.playerService = playerService;
        this.rewardRepository = rewardRepository;
        this.contributionRepository = contributionRepository;
        this.dailyCounterService = dailyCounterService;
        this.ownedCardRepository = ownedCardRepository;
    }

    @PostMapping
    public PlayerResponse register(@Valid @RequestBody RegisterRequest request) {
        return PlayerResponse.of(playerService.register(request.nickname()));
    }

    @GetMapping("/me")
    public ProfileResponse me(@CurrentPlayer Player player) {
        RewardLedger ledger = rewardRepository.findByPlayerId(player.getId())
                .orElseGet(() -> new RewardLedger(player.getId()));
        DailyCounter daily = dailyCounterService.snapshot(player.getId());

        List<RegionContributionResponse> contributions = contributionRepository.findByPlayerId(player.getId())
                .stream()
                .map(RegionContributionResponse::of)
                .toList();

        return new ProfileResponse(PlayerResponse.of(player), ledger.getXp(), ledger.getCoins(), ledger.level(),
                contributions,
                new DailyUsageResponse(daily.getPacks(), daily.getXp(), daily.getRegionContributionPoints()));
    }

    @PatchMapping("/me/nickname-visibility")
    public PlayerResponse setNicknameVisibility(@CurrentPlayer Player player,
                                                @RequestBody VisibilityRequest request) {
        return PlayerResponse.of(playerService.setNicknameVisibility(player.getPublicId(), request.nicknamePublic()));
    }

    @GetMapping("/me/cards")
    public List<OwnedCardResponse> cards(@CurrentPlayer Player player) {
        return ownedCardRepository.findByPlayerWithCard(player).stream().map(OwnedCardResponse::of).toList();
    }

    /** 다른 이용자의 공개 정보. 경로·원시 센서·정확한 위치는 어떤 경우에도 포함하지 않습니다. */
    @GetMapping("/{publicId}")
    public PublicPlayerResponse publicProfile(@PathVariable String publicId) {
        Player player = playerService.require(publicId);
        RewardLedger ledger = rewardRepository.findByPlayerId(player.getId())
                .orElseGet(() -> new RewardLedger(player.getId()));
        return new PublicPlayerResponse(player.getPublicId(), player.displayName(), ledger.level());
    }

    public record RegisterRequest(@NotBlank @Size(max = 20) String nickname) {
    }

    public record VisibilityRequest(boolean nicknamePublic) {
    }

    public record PlayerResponse(String publicId, String nickname, boolean nicknamePublic) {
        static PlayerResponse of(Player player) {
            return new PlayerResponse(player.getPublicId(), player.getNickname(), player.isNicknamePublic());
        }
    }

    public record PublicPlayerResponse(String publicId, String displayName, int level) {
    }

    public record ProfileResponse(PlayerResponse player, long xp, long coins, int level,
                                  List<RegionContributionResponse> regionContributions,
                                  DailyUsageResponse todayUsage) {
    }

    public record RegionContributionResponse(String regionCode, long points, String note) {
        static RegionContributionResponse of(RegionContributionLedger ledger) {
            return new RegionContributionResponse(ledger.getRegionCode(), ledger.getPoints(),
                    "캠페인 진행도입니다. 차감·양도·현금화되지 않습니다.");
        }
    }

    public record DailyUsageResponse(int packs, int xp, int regionContributionPoints) {
    }

    public record OwnedCardResponse(String cardCode, String name, String line, int tier,
                                    boolean locked, String sourceCellId) {
        static OwnedCardResponse of(OwnedCard owned) {
            return new OwnedCardResponse(owned.getCard().getCode(), owned.getCard().getName(),
                    owned.getCard().getLine().getLabel(), owned.getCard().getTier(),
                    owned.isLocked(), owned.getSourceCellId());
        }
    }
}
