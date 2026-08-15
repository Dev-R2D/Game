package com.r2d.domain;

/**
 * 구간 유효성 판정.
 *
 * <p>기준 미만 구간을 즉시 버리지 않고 보류로 남기는 이유는, 계정 제재가 아니라
 * 데이터 오염 방지가 목적이기 때문입니다. 결과 화면에서도 세 상태를 구분해 보여줍니다.
 */
public enum Validity {

    /** 유효: 기본 피해 + 데이터 기여 + 셀 상태 갱신 모두 반영. */
    VALID("유효"),

    /** 보류: 기본 피해만 반영하고 데이터 기여와 셀 상태 갱신은 하지 않음. */
    PENDING("보류"),

    /** 무효: 피해·기여 모두 반영하지 않음. */
    INVALID("무효");

    private final String label;

    Validity(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
