package com.r2d.ride;

import java.util.List;

/**
 * 앱이 올리는 센서 배치 하나.
 *
 * <p>주행 중에는 네트워크가 끊겨도 로컬 큐에 쌓아 두었다가 복구 후 순서대로 보냅니다.
 * 그래서 같은 배치가 두 번 도착하는 것은 오류가 아니라 정상 동작이며,
 * {@code idempotencyKey}로 한 번만 반영합니다.
 */
public record SensorBatchCommand(
        int batchSeq,
        String idempotencyKey,
        List<PointCommand> points,
        List<ImuCommand> imuWindows
) {

    /** 위치 표본. */
    public record PointCommand(
            long epochMs,
            double lat,
            double lon,
            double accuracyM,
            double speedMps
    ) {
    }

    /** 앱이 추출한 IMU 이벤트 특징. 원시 가속도 대신 파형 특징만 올립니다. */
    public record ImuCommand(
            long epochMs,
            double lat,
            double lon,
            int durationMs,
            double peakG,
            double rmsG,
            double dominantHz,
            double gyroRmsDps,
            double entrySpeedMps,
            boolean mountStable
    ) {
    }
}
