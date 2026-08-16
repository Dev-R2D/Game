package com.r2d.settlement;

import com.r2d.domain.CellState;
import com.r2d.domain.ContributionType;
import com.r2d.domain.Validity;

/** 셀 하나에 대한 정산 내역. 결과 화면의 데미지 내역 한 줄에 대응합니다. */
public record CellContribution(
        String cellId,
        double distanceM,
        Validity validity,
        CellState state,
        ContributionType type,
        /** 유효 거리에서 나온 기본 피해(배율 적용 전). */
        double baseDamage,
        /** 셀 상태 · 덱 계열 보정 · 반복 감쇠까지 반영한 최종 기여 배율. */
        double multiplier,
        /** 시너지와 신뢰도 계수까지 곱한 최종 피해. */
        double damage,
        /** 최근 기간에 같은 이용자가 이 셀을 지난 횟수. */
        int recentRepeatCount,
        String note
) {

    public boolean contributesData() {
        return validity == Validity.VALID && type != ContributionType.NONE;
    }
}
