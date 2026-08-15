package com.r2d.domain;

/**
 * 주행 수단 판별 결과.
 *
 * <p>자전거 주행만 데이터 기여로 인정합니다. 차량 탑승이나 모의 위치가 의심돼도
 * 계정에 불이익을 주지 않고, 해당 구간의 기여만 보류·제외합니다.
 */
public enum TransportMode {
    BICYCLE("자전거"),
    VEHICLE("차량"),
    WALK("도보"),
    UNKNOWN("판별 불가");

    private final String label;

    TransportMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
