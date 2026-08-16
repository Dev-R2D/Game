package com.r2d.domain;

/** 주행 세션 상태. 정산은 한 번만 일어나야 하므로 상태로 강제합니다. */
public enum RideStatus {

    /** 주행 중: 센서 배치를 계속 받을 수 있는 상태. */
    ACTIVE("주행 중"),

    /** 정산 완료: 피해·보상·보스 반영이 끝난 상태. 추가 배치를 거부합니다. */
    SETTLED("정산 완료"),

    /** 폐기: 품질 미달이나 사용자 취소로 정산 없이 닫힌 상태. */
    DISCARDED("폐기");

    private final String label;

    RideStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
