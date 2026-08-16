package com.r2d.domain;

/**
 * 주행 세션의 출처.
 *
 * <p>심사 모드 재생과 실제 주행은 동일한 판정·정산 파이프라인을 타지만, 원장에는
 * 반드시 구분해 남깁니다. 파생 로그를 실제 라이더의 원본 데이터로 표기하지 않기 위해서입니다.
 */
public enum RideMode {

    /** 실제 주행: 기기 GPS·IMU에서 올라온 세션. */
    REAL("실제 주행"),

    /** 심사 재현: 사전 기록 로그 또는 파생 로그를 재생한 세션. */
    JUDGE_SIM("심사 재현");

    private final String label;

    RideMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
