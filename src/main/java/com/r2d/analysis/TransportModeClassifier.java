package com.r2d.analysis;

import java.util.List;

import com.r2d.config.GameRules;
import com.r2d.domain.TransportMode;
import com.r2d.ride.ImuWindow;

import org.springframework.stereotype.Service;

/**
 * 주행 수단 판별.
 *
 * <p>목적은 처벌이 아니라 데이터 오염 방지입니다. 차량 탑승이나 모의 위치가 의심돼도 계정에
 * 불이익을 주지 않고, 해당 주행의 기여만 보류·제외합니다.
 *
 * <p>속도만 보면 내리막 자전거와 저속 차량을 구분할 수 없습니다. 그래서 속도 분포에 더해
 * 노면 진동 세기를 함께 봅니다. 서스펜션이 있는 차량은 같은 속도에서도 진동이 뚜렷하게 작습니다.
 */
@Service
public class TransportModeClassifier {

    /** 이 속도(m/s) 미만이면 정지에 가까운 표본으로 셉니다. */
    private static final double STOP_SPEED_MPS = 0.8;

    private final GameRules rules;

    public TransportModeClassifier(GameRules rules) {
        this.rules = rules;
    }

    public TransportAssessment classify(List<RideSegment> segments, List<ImuWindow> imuWindows) {
        List<RideSegment> usable = segments.stream()
                .filter(s -> s.validity() != com.r2d.domain.Validity.INVALID)
                .toList();
        if (usable.size() < 3) {
            return TransportAssessment.unknown("판별에 필요한 표본이 부족합니다.");
        }

        double[] speeds = usable.stream().mapToDouble(RideSegment::speedMps).sorted().toArray();
        double mean = usable.stream().mapToDouble(RideSegment::speedMps).average().orElse(0);
        double p95 = speeds[percentileIndex(speeds.length, 0.95)];
        double stopRatio = (double) usable.stream().filter(s -> s.speedMps() < STOP_SPEED_MPS).count() / usable.size();
        double vibration = imuWindows.isEmpty() ? 0.0
                : imuWindows.stream().mapToDouble(ImuWindow::getRmsG).average().orElse(0);

        GameRules.Transport t = rules.transport();

        TransportMode mode;
        double confidence;
        String reason;

        if (p95 < t.walkP95Mps()) {
            mode = TransportMode.WALK;
            confidence = 0.85;
            reason = "p95 속도 " + fmt(p95) + "m/s로 도보 범위입니다.";
        } else if (p95 >= t.vehicleP95Mps()) {
            mode = TransportMode.VEHICLE;
            confidence = 0.9;
            reason = "p95 속도 " + fmt(p95) + "m/s로 자전거 범위를 넘습니다.";
        } else if (p95 > t.bicycleMaxP95Mps()) {
            // 자전거 상한과 차량 하한 사이. 내리막 자전거일 수도 있어 확정하지 않습니다.
            mode = TransportMode.UNKNOWN;
            confidence = 0.4;
            reason = "p95 속도 " + fmt(p95) + "m/s가 자전거와 차량 경계에 있습니다.";
        } else if (!imuWindows.isEmpty() && mean > t.walkP95Mps() * 2 && vibration < t.vehicleMaxVibrationG()) {
            // 속도는 자전거 범위인데 노면 진동이 거의 없는 경우. 완충된 차량 탑승을 의심합니다.
            mode = TransportMode.VEHICLE;
            confidence = 0.65;
            reason = "속도는 자전거 범위지만 노면 진동 " + fmt(vibration) + "g로 완충 차량이 의심됩니다.";
        } else {
            mode = TransportMode.BICYCLE;
            // 속도 분포가 자전거 대역 중앙에 가까울수록, 노면 진동 근거가 있을수록 신뢰도가 올라갑니다.
            double speedFit = 1.0 - Math.abs(mean - 5.0) / 8.0;
            double vibrationFit = imuWindows.isEmpty() ? 0.0 : Math.min(1.0, vibration / t.vehicleMaxVibrationG());
            confidence = clamp(0.55 + 0.25 * clamp(speedFit) + 0.20 * clamp(vibrationFit));
            reason = "평균 " + fmt(mean) + "m/s, 정지 비율 " + fmt(stopRatio)
                    + ", 노면 진동 " + fmt(vibration) + "g로 자전거 주행에 부합합니다.";
        }

        return new TransportAssessment(mode, confidence, reason, mean, p95, stopRatio, vibration);
    }

    /** 정렬된 배열에서 백분위에 해당하는 인덱스. 표본이 적어도 범위를 벗어나지 않습니다. */
    private static int percentileIndex(int length, double percentile) {
        int index = (int) Math.ceil(length * percentile) - 1;
        return Math.max(0, Math.min(length - 1, index));
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static String fmt(double value) {
        return String.format("%.2f", value);
    }
}
