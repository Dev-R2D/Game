package com.r2d.settlement;

import java.util.List;

import com.r2d.domain.ContributionType;

/** 확정 피해 계산 결과 전체. */
public record DamageResult(
        List<CellContribution> cells,
        /** 유효 거리 기반 기본 피해 합계. */
        double baseDamage,
        /** 표시용 가중 평균 기여 배율. 기본 피해로 가중해 계산합니다. */
        double weightedContributionMultiplier,
        double deckSynergy,
        double confidenceCoefficient,
        double finalDamage
) {

    public long countByType(ContributionType type) {
        return cells.stream().filter(c -> c.contributesData() && c.type() == type).count();
    }

    public static DamageResult empty() {
        return new DamageResult(List.of(), 0, 1.0, 1.0, 1.0, 0);
    }
}
