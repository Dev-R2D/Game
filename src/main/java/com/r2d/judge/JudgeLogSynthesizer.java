package com.r2d.judge;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.r2d.common.GeoUtils;
import com.r2d.ride.SensorBatchCommand;

import org.springframework.stereotype.Component;

/**
 * 로그 정의를 실제 표본 스트림으로 펼칩니다.
 *
 * <p>결정론적입니다. 같은 로그는 몇 번 재생해도 같은 표본을 만들고, 파생 로그의 편차도 시드로
 * 고정됩니다. 심사에서 "다시 돌려도 같은 결과가 나오는가"를 확인할 수 있어야 하기 때문입니다.
 */
@Component
public class JudgeLogSynthesizer {

    private final JudgeLogRepository logRepository;

    public JudgeLogSynthesizer(JudgeLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    /**
     * 로그를 표본으로 펼칩니다.
     *
     * @param startEpochMs 첫 표본의 시각
     */
    public SensorBatchCommand synthesize(JudgeLog log, long startEpochMs, String idempotencyKey) {
        JudgeLog base = log.sourceType() == JudgeLog.SourceType.DERIVED
                ? logRepository.get(log.derivedFrom())
                : log;

        JudgeLog.Deviation deviation = log.deviation();
        Random random = new Random(deviation == null ? log.logId().hashCode() : deviation.seed());

        double speedScale = deviation == null ? 1.0 : deviation.speedScale();
        double sensorScale = deviation == null ? 1.0 : deviation.sensorScale();
        double jitterM = deviation == null ? 0.0 : deviation.positionJitterM();

        double speed = base.speedMps() * speedScale;
        List<JudgeLog.Waypoint> waypoints = base.waypoints();
        if (waypoints.size() < 2 || speed <= 0) {
            return new SensorBatchCommand(0, idempotencyKey, List.of(), List.of());
        }

        double totalLength = routeLength(waypoints);
        double stepM = speed * (base.sampleIntervalMs() / 1000.0);
        int sampleCount = (int) Math.floor(totalLength / stepM);

        List<JudgeLog.QualityFault> faults = base.qualityFaults() == null ? List.of() : base.qualityFaults();
        TrackOffset offset = TrackOffset.of(jitterM, random);

        List<SensorBatchCommand.PointCommand> points = new ArrayList<>();
        long extraDelayMs = 0;
        for (int i = 0; i <= sampleCount; i++) {
            double progress = sampleCount == 0 ? 0 : (double) i / sampleCount;
            double travelled = i * stepM;
            double[] position = interpolate(waypoints, travelled);
            double[] jittered = offset.apply(position, progress);
            // 오차 반경도 표본마다 조금씩 흔들립니다. 완벽한 신호는 실제 주행에 없습니다.
            double accuracy = base.baseAccuracyM() * (0.8 + random.nextDouble() * 0.6);

            for (JudgeLog.QualityFault fault : faults) {
                switch (fault.kind()) {
                    case ACCURACY_SPIKE -> {
                        if (Math.abs(progress - fault.atProgress()) < 0.03) {
                            accuracy = fault.magnitude();
                        }
                    }
                    case COORD_JUMP -> {
                        if (isNearestSample(progress, fault.atProgress(), sampleCount)) {
                            jittered = new double[]{jittered[0] + fault.magnitude() / 111_320.0, jittered[1]};
                        }
                    }
                    case TIME_GAP -> {
                        if (isNearestSample(progress, fault.atProgress(), sampleCount)) {
                            extraDelayMs += (long) (fault.magnitude() * 1000);
                        }
                    }
                }
            }

            points.add(new SensorBatchCommand.PointCommand(
                    startEpochMs + (long) i * base.sampleIntervalMs() + extraDelayMs,
                    jittered[0], jittered[1], accuracy, speed));
        }

        List<SensorBatchCommand.ImuCommand> imuWindows = new ArrayList<>();
        for (JudgeLog.EventSpec event : base.events()) {
            double progress = Math.max(0, Math.min(1, event.atProgress()));
            double travelled = totalLength * progress;
            double[] position = interpolate(waypoints, travelled);
            double[] jittered = offset.apply(position, progress);
            long offsetMs = (long) ((travelled / speed) * 1000);
            imuWindows.add(new SensorBatchCommand.ImuCommand(
                    startEpochMs + offsetMs,
                    jittered[0], jittered[1],
                    event.durationMs(),
                    event.peakG() * sensorScale,
                    event.rmsG() * sensorScale,
                    event.dominantHz(),
                    event.gyroRmsDps() * sensorScale,
                    speed,
                    event.mountStable()));
        }

        return new SensorBatchCommand(0, idempotencyKey, points, imuWindows);
    }

    /** 결함을 정확히 한 표본에만 적용하기 위한 판정. */
    private static boolean isNearestSample(double progress, double target, int sampleCount) {
        if (sampleCount == 0) {
            return false;
        }
        double half = 0.5 / sampleCount;
        return Math.abs(progress - target) <= half;
    }

    /** 경로 전체 길이(m). */
    private double routeLength(List<JudgeLog.Waypoint> waypoints) {
        double length = 0;
        for (int i = 1; i < waypoints.size(); i++) {
            length += segmentLength(waypoints, i);
        }
        return length;
    }

    private double segmentLength(List<JudgeLog.Waypoint> waypoints, int toIndex) {
        JudgeLog.Waypoint a = waypoints.get(toIndex - 1);
        JudgeLog.Waypoint b = waypoints.get(toIndex);
        return GeoUtils.distanceMeters(a.lat(), a.lon(), b.lat(), b.lon());
    }

    /** 경로 시작점에서 {@code travelled}m 떨어진 좌표. */
    private double[] interpolate(List<JudgeLog.Waypoint> waypoints, double travelled) {
        double remaining = travelled;
        for (int i = 1; i < waypoints.size(); i++) {
            double length = segmentLength(waypoints, i);
            if (remaining <= length || i == waypoints.size() - 1) {
                double ratio = length <= 0 ? 0 : Math.min(1.0, remaining / length);
                JudgeLog.Waypoint a = waypoints.get(i - 1);
                JudgeLog.Waypoint b = waypoints.get(i);
                return new double[]{
                        a.lat() + (b.lat() - a.lat()) * ratio,
                        a.lon() + (b.lon() - a.lon()) * ratio
                };
            }
            remaining -= length;
        }
        JudgeLog.Waypoint last = waypoints.get(waypoints.size() - 1);
        return new double[]{last.lat(), last.lon()};
    }

    /**
     * 파생 로그의 위치 편차.
     *
     * <p>표본마다 독립적인 난수를 더하면 안 됩니다. 1초 간격 표본에 ±6m의 백색잡음을 넣으면
     * 인접 표본 사이의 환산 속도가 10m/s 넘게 튀어, 주행 수단 판별이 자전거를 차량으로 잘못
     * 분류합니다. 실제로 같은 길을 달리는 다른 라이더가 만드는 차이는 잡음이 아니라
     * "조금 다른 주행 라인"이므로, 일정한 횡방향 오프셋과 느린 사행으로 표현합니다.
     */
    private record TrackOffset(double constantLatM, double constantLonM, double wanderM, double phase) {

        /** 경로 전체에 걸쳐 사행이 도는 횟수. */
        private static final double WANDER_CYCLES = 2.0;

        static TrackOffset of(double jitterM, Random random) {
            if (jitterM <= 0) {
                return new TrackOffset(0, 0, 0, 0);
            }
            return new TrackOffset(
                    (random.nextDouble() - 0.5) * 2 * jitterM,
                    (random.nextDouble() - 0.5) * 2 * jitterM,
                    jitterM * 0.3,
                    random.nextDouble() * Math.PI * 2);
        }

        double[] apply(double[] position, double progress) {
            if (constantLatM == 0 && constantLonM == 0 && wanderM == 0) {
                return position;
            }
            double wander = Math.sin(progress * WANDER_CYCLES * 2 * Math.PI + phase) * wanderM;
            return new double[]{
                    position[0] + (constantLatM + wander) / 111_320.0,
                    position[1] + constantLonM / 88_300.0
            };
        }
    }
}
