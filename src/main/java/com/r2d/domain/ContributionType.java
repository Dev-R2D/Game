package com.r2d.domain;

/** 한 셀을 지나며 만든 데이터 기여의 종류. 카드 계열 보너스가 붙는 기준이기도 합니다. */
public enum ContributionType {

    /** 신규: 아직 조사되지 않은 셀을 처음 확보. */
    NEW("신규"),

    /** 갱신: 오래되어 다시 봐야 하는 셀을 최신화. */
    UPDATE("갱신"),

    /** 검증: 신뢰도가 낮거나 보수 후 재확인이 필요한 셀을 다시 관측. */
    VERIFY("검증"),

    /** 기여 없음: 최근에 충분히 조사된 셀. 기본 피해만 발생합니다. */
    NONE("기여없음");

    private final String label;

    ContributionType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
