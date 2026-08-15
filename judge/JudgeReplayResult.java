package com.r2d.judge;

import com.r2d.settlement.RideSettlement;

/** 심사 모드 재생 한 회의 결과. */
public record JudgeReplayResult(
        String rideId,
        String logId,
        String provenance,
        /** 이 재생에 사용한 심사용 가상 라이더의 표시 이름. */
        String simulatedRider,
        JudgeOverlay overlay,
        RideSettlement settlement,
        /** 심사자가 확인해야 할 항목을 문장으로 정리한 것. */
        java.util.List<String> checkpoints
) {
}
