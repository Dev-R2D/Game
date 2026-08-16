package com.r2d.analysis;

import com.r2d.domain.Validity;

/**
 * 연속한 두 위치 표본 사이의 구간.
 *
 * <p>유효성 판정과 셀 매칭은 점이 아니라 구간 단위로 합니다. 한 점의 GPS가 튀었다고
 * 주행 전체를 버리지 않고, 문제가 된 구간만 보류·무효로 떼어내기 위해서입니다.
 */
public record RideSegment(
        long startEpochMs,
        long endEpochMs,
        double distanceM,
        double speedMps,
        /** 구간 양 끝 중 나쁜 쪽의 GPS 오차 반경(m). */
        double worstAccuracyM,
        /** 구간 중점 좌표. 지도 표시와 심사 오버레이가 이 값을 씁니다. */
        double midLat,
        double midLon,
        /** 구간 중점이 속한 도로 셀. */
        String cellId,
        Validity validity,
        /** 보류·무효 사유. 결과 화면에 그대로 표시합니다. */
        String reason
) {

    public boolean isValid() {
        return validity == Validity.VALID;
    }

    /** 기본 피해의 재료가 되는 거리. 무효 구간은 거리 자체가 인정되지 않습니다. */
    public double damageableDistanceM() {
        return validity == Validity.INVALID ? 0.0 : distanceM;
    }
}
