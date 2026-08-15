package com.r2d.domain;

/**
 * 도로 셀의 데이터 상태.
 *
 * <p>보스 HP(지역 데이터 필요량)와 기여 배율이 모두 이 상태에서 나옵니다. 즉 "어떤 길을
 * 달렸는가"가 게임 결과를 바꾼다는 규칙의 실제 구현 지점입니다.
 *
 * <p>주의: 여기 있는 값은 "도로 데이터가 얼마나 필요한가"만 나타냅니다. 노면이 얼마나
 * 심하게 파손됐는지는 들어가지 않습니다({@link com.r2d.domain.AnomalyClass} 참고).
 */
public enum CellState {

    /** 미조사: 관측 이력이 없는 셀. 필요도·기여 배율이 가장 높습니다. */
    UNSURVEYED("미조사", 1.0, 2.0, ContributionType.NEW),

    /** 보수 후 재확인: 이상이 해소됐다고 보고된 뒤 다시 확인이 필요한 셀. */
    RECHECK_REQUIRED("보수 후 재확인", 0.8, 1.8, ContributionType.VERIFY),

    /** 노후/갱신 필요: 마지막 관측이 신선도 기준을 넘긴 셀. */
    STALE("갱신 필요", 0.6, 1.5, ContributionType.UPDATE),

    /** 낮은 신뢰도: 관측은 있으나 품질·독립 관측 수가 부족한 셀. */
    LOW_CONFIDENCE("낮은 신뢰도", 0.5, 1.4, ContributionType.VERIFY),

    /** 최근 조사됨: 추가 데이터 수요가 없는 셀. 기본 피해만 나옵니다. */
    FRESH("최근 조사됨", 0.0, 1.0, ContributionType.NONE);

    private final String label;
    private final double dataNeedWeight;
    private final double contributionMultiplier;
    private final ContributionType contributionType;

    CellState(String label, double dataNeedWeight, double contributionMultiplier,
              ContributionType contributionType) {
        this.label = label;
        this.dataNeedWeight = dataNeedWeight;
        this.contributionMultiplier = contributionMultiplier;
        this.contributionType = contributionType;
    }

    public String getLabel() {
        return label;
    }

    /** 지역 데이터 필요량 합계에 더해지는 가중치. 보스 최대 HP의 재료입니다. */
    public double getDataNeedWeight() {
        return dataNeedWeight;
    }

    /** 이 셀을 유효하게 통과했을 때의 기본 데이터 기여 배율. */
    public double getContributionMultiplier() {
        return contributionMultiplier;
    }

    public ContributionType getContributionType() {
        return contributionType;
    }
}
