package com.r2d.analysis;

import com.r2d.domain.AnomalyClass;

/**
 * IMU 윈도우 하나에 대한 후보 판정 결과.
 *
 * <p>출력은 확정 결함명이 아니라 후보 클래스와 신뢰도입니다. "포트홀", "균열", "단차" 같은
 * 확정 명칭은 카메라 교차검증과 전문가 라벨이 확보되기 전까지 어디에도 쓰지 않습니다.
 */
public record AnomalyDetection(
        String cellId,
        double lat,
        double lon,
        long epochMs,
        AnomalyClass anomalyClass,
        /** 0~1. 이 값은 보상 크기에 곱해지지 않습니다. 상태 전이 판정에만 씁니다. */
        double confidence,
        String reason
) {

    /** 서브몹으로 추적할 후보인지. 정상 시설물과 판정 보류는 제외됩니다. */
    public boolean isTrackable() {
        return anomalyClass.isTrackable();
    }
}
