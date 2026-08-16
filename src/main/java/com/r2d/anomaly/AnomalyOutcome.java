package com.r2d.anomaly;

import java.util.List;

import com.r2d.domain.AnomalyState;

/** 한 주행이 서브몹 상태에 만든 변화. 결과 화면과 알림의 재료입니다. */
public record AnomalyOutcome(
        /** 이번 주행에서 처음 만들어진 후보(= 새 의심 몹) 수. */
        int discovered,
        /** 이번 주행의 관측으로 의심에서 확정으로 전이한 수. */
        int confirmed,
        /** 이번 주행의 재확인으로 소멸한 수. */
        int resolved,
        List<Transition> transitions
) {

    /** 개별 상태 전이 기록. */
    public record Transition(
            String cellId,
            AnomalyState fromState,
            AnomalyState toState,
            double confidence,
            String reason
    ) {
    }

    public static AnomalyOutcome empty() {
        return new AnomalyOutcome(0, 0, 0, List.of());
    }
}
