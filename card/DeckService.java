package com.r2d.card;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.r2d.common.R2dException;
import com.r2d.config.GameRules;
import com.r2d.domain.CardLine;
import com.r2d.domain.ContributionType;

import org.springframework.stereotype.Service;

/** 덱 편성 검증과 효과 계산. */
@Service
public class DeckService {

    /** 한 주행에 편성하는 카드 수. */
    public static final int DECK_SIZE = 3;

    private final CardRepository cardRepository;
    private final GameRules rules;

    public DeckService(CardRepository cardRepository, GameRules rules) {
        this.cardRepository = cardRepository;
        this.rules = rules;
    }

    /**
     * 카드 코드 3장으로 덱 효과를 계산합니다.
     *
     * @param cardCodes 중복 없는 카드 코드 3장. 비어 있으면 중립 덱을 돌려줍니다.
     */
    public DeckEffect resolve(List<String> cardCodes) {
        if (cardCodes == null || cardCodes.isEmpty()) {
            return DeckEffect.neutral();
        }
        if (cardCodes.size() != DECK_SIZE) {
            throw R2dException.badRequest("DECK_SIZE", "덱은 카드 " + DECK_SIZE + "장으로 편성해야 합니다.");
        }
        if (cardCodes.stream().distinct().count() != DECK_SIZE) {
            throw R2dException.badRequest("DECK_DUPLICATE", "같은 카드를 중복 편성할 수 없습니다.");
        }

        List<Card> cards = cardRepository.findByCodeIn(cardCodes);
        if (cards.size() != DECK_SIZE) {
            throw R2dException.notFound("CARD_NOT_FOUND", "존재하지 않는 카드가 덱에 포함되어 있습니다.");
        }

        Map<CardLine, Integer> lineCounts = new EnumMap<>(CardLine.class);
        Map<ContributionType, Double> contributionBonus = new HashMap<>();
        double enduranceBonus = 0.0;

        for (Card card : cards) {
            lineCounts.merge(card.getLine(), 1, Integer::sum);
            if (card.getLine() == CardLine.ENDURANCE) {
                enduranceBonus += rules.damage().enduranceBonusPerCard() * (0.6 + 0.2 * card.getTier());
            } else {
                contributionBonus.merge(card.getLine().getBoostedType(),
                        card.lineBonus(rules.damage().lineBonusPerCard()), Double::sum);
            }
        }

        int maxSameLine = lineCounts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        double synergy;
        String synergyLabel;
        if (maxSameLine >= 3) {
            synergy = rules.damage().synergyTriple();
            synergyLabel = "상위 시너지";
        } else if (maxSameLine == 2) {
            synergy = rules.damage().synergyPair();
            synergyLabel = "계열 시너지";
        } else {
            synergy = rules.damage().synergyMixed();
            synergyLabel = "범용 덱";
        }

        return new DeckEffect(
                List.copyOf(cardCodes),
                synergy,
                synergyLabel + " (" + DeckEffect.describe(cards.stream().map(Card::getLine).toList()) + ")",
                Map.copyOf(contributionBonus),
                enduranceBonus,
                lineCounts.getOrDefault(CardLine.TRAILBLAZE, 0)
        );
    }
}
