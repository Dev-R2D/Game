package com.r2d.card;

import java.util.List;
import java.util.Map;

import com.r2d.domain.CardLine;
import com.r2d.domain.ContributionType;

/**
 * 편성된 덱 3장이 만들어내는 효과.
 *
 * <p>덱은 "오늘 어디를 어떻게 달릴 것인가"의 선언입니다. 같은 계열을 몰아주면 특정 상태의
 * 셀에서 배율이 크지만, 예상과 다른 길을 달리면 보정을 거의 못 받습니다. 계열을 섞으면
 * 최대 배율은 낮아지는 대신 어떤 상태의 셀을 만나도 손해가 적습니다.
 */
public record DeckEffect(
        List<String> cardCodes,
        /** 덱 전체에 곱해지는 시너지 배율. */
        double synergy,
        /** 시너지 설명(상위 시너지 / 계열 시너지 / 범용 덱). */
        String synergyLabel,
        /** 기여 종류별 추가 배율 보정. 해당 종류의 셀을 지날 때만 붙습니다. */
        Map<ContributionType, Double> contributionBonus,
        /** 지구 계열이 올려주는 기본 피해 보정. */
        double enduranceBonus,
        /** 개척 계열 카드 수. 탐사 팩 등급 하한 보정에 씁니다. */
        int trailblazeCount
) {

    /** 특정 기여 종류에 적용할 최종 계열 보정(1.0 = 보정 없음). */
    public double bonusFor(ContributionType type) {
        return 1.0 + contributionBonus.getOrDefault(type, 0.0);
    }

    /** 덱을 고르지 않은 주행(심사 재현 기본값 등)에 쓰는 중립 덱. */
    public static DeckEffect neutral() {
        return new DeckEffect(List.of(), 1.0, "덱 없음", Map.of(), 0.0, 0);
    }

    /** 계열 구성 요약. 결과 화면에서 "왜 이 배율이 나왔는지" 설명하는 데 씁니다. */
    public static String describe(List<CardLine> lines) {
        return lines.stream().map(CardLine::getLabel).reduce((a, b) -> a + "·" + b).orElse("없음");
    }
}
