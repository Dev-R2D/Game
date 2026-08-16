package com.r2d.api;

import java.util.List;
import java.util.Map;

import com.r2d.card.Card;
import com.r2d.card.CardRepository;
import com.r2d.card.DeckEffect;
import com.r2d.card.DeckService;
import com.r2d.domain.ContributionType;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CardController {

    private final CardRepository cardRepository;
    private final DeckService deckService;

    public CardController(CardRepository cardRepository, DeckService deckService) {
        this.cardRepository = cardRepository;
        this.deckService = deckService;
    }

    @GetMapping("/cards")
    public List<CardResponse> cards() {
        return cardRepository.findAll().stream().map(CardResponse::of).toList();
    }

    /**
     * 덱 편성 미리보기.
     *
     * <p>출발 전 30초 안에 "이 덱이 오늘 주행에 왜 유리한지"를 보여 주기 위한 API입니다.
     * 실제 배율은 어떤 상태의 셀을 지나는지에 따라 달라지므로, 여기서는 계열별 보정만 알려 줍니다.
     */
    @PostMapping("/decks/preview")
    public DeckPreviewResponse preview(@RequestBody DeckRequest request) {
        DeckEffect effect = deckService.resolve(request.cardCodes());
        return DeckPreviewResponse.of(effect);
    }

    public record DeckRequest(List<String> cardCodes) {
    }

    public record CardResponse(String code, String name, String line, int tier,
                               String description, boolean commemorative) {
        static CardResponse of(Card card) {
            return new CardResponse(card.getCode(), card.getName(), card.getLine().getLabel(),
                    card.getTier(), card.getDescription(), card.isCommemorative());
        }
    }

    public record DeckPreviewResponse(List<String> cardCodes, double synergy, String synergyLabel,
                                      Map<String, Double> contributionBonus, double enduranceBonus,
                                      String note) {
        static DeckPreviewResponse of(DeckEffect effect) {
            Map<String, Double> bonus = effect.contributionBonus().entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            e -> e.getKey().getLabel(), Map.Entry::getValue));
            return new DeckPreviewResponse(effect.cardCodes(), effect.synergy(), effect.synergyLabel(),
                    bonus, effect.enduranceBonus(),
                    describe(effect));
        }

        private static String describe(DeckEffect effect) {
            if (effect.contributionBonus().isEmpty()) {
                return "지구 계열 위주 덱입니다. 경로와 무관하게 유효 거리 기반 피해가 올라갑니다.";
            }
            if (effect.contributionBonus().size() >= 3) {
                return "범용 덱입니다. 최대 배율은 낮지만 어떤 상태의 구간을 만나도 손해가 적습니다.";
            }
            String types = effect.contributionBonus().keySet().stream()
                    .map(ContributionType::getLabel)
                    .reduce((a, b) -> a + "·" + b).orElse("");
            return types + " 구간이 많은 경로에서 유리한 덱입니다.";
        }
    }
}
