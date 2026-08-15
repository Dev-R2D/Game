package com.r2d.domain;

/**
 * 카드 계열.
 *
 * <p>출발 전 30초의 전략 레이어입니다. 각 계열은 특정 기여 종류의 배율을 올리고,
 * 지구 계열만 예외적으로 기여 종류가 아니라 유효 거리 기반 기본 피해를 올립니다.
 */
public enum CardLine {

    /** 개척: 미탐사 구간 기여 배율과 탐사 팩 등급 보정. */
    TRAILBLAZE("개척", ContributionType.NEW),

    /** 정찰: 노후·갱신 필요 구간 기여 배율과 갱신 미션 보정. */
    SCOUT("정찰", ContributionType.UPDATE),

    /** 지구: 유효 주행거리 기반 기본 피해와 완주 보상 보정. */
    ENDURANCE("지구", null),

    /** 검증: 의심·낮은 신뢰도·보수 후 재확인 셀 통과 시 검증 기여 보정. */
    VERIFICATION("검증", ContributionType.VERIFY);

    private final String label;
    private final ContributionType boostedType;

    CardLine(String label, ContributionType boostedType) {
        this.label = label;
        this.boostedType = boostedType;
    }

    public String getLabel() {
        return label;
    }

    /** 이 계열이 배율을 올려주는 기여 종류. 지구 계열은 null(기본 피해를 올림). */
    public ContributionType getBoostedType() {
        return boostedType;
    }
}
