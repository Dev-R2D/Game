package com.r2d.analysis;

import com.r2d.domain.TransportMode;

/** 주행 수단 판별 결과와 그 근거. */
public record TransportAssessment(
        TransportMode mode,
        /** 0~1. 이 값이 기준 미만이면 자전거로 확정하지 않고 기여를 보류합니다. */
        double confidence,
        String reason,
        double meanSpeedMps,
        double p95SpeedMps,
        /** 정지에 가까운 표본 비율. 신호 대기가 섞인 도심 주행을 구분하는 특징입니다. */
        double stopRatio,
        /** 노면 진동 세기(RMS g). 완충된 차량은 같은 속도에서도 진동이 훨씬 작습니다. */
        double vibrationG
) {

    /** 데이터 기여를 인정할 수 있는 주행인지. */
    public boolean qualifiesForContribution(double requiredConfidence) {
        return mode == TransportMode.BICYCLE && confidence >= requiredConfidence;
    }

    public static TransportAssessment unknown(String reason) {
        return new TransportAssessment(TransportMode.UNKNOWN, 0.0, reason, 0, 0, 0, 0);
    }
}
