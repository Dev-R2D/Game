package com.r2d.mission;

/** 미션 종류. */
public enum MissionType {
    EXPLORE("미탐사 구간 정찰"),
    UPDATE("노후 데이터 갱신"),
    VERIFY("검증 체인"),
    BOSS("공동 보스전");

    private final String label;

    MissionType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
