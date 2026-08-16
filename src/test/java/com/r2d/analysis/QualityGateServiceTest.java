package com.r2d.analysis;

import java.util.ArrayList;
import java.util.List;

import com.r2d.config.TestRules;
import com.r2d.domain.Validity;
import com.r2d.ride.TrackPoint;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QualityGateServiceTest {

    private static final long START_MS = 1_700_000_000_000L;

    private final QualityGateService service = new QualityGateService(TestRules.defaults());

    /** 북쪽으로 일정 속도로 달리는 정상 표본. */
    private static List<TrackPoint> straightRide(int count, double accuracyM) {
        List<TrackPoint> points = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            // 위도 0.000045도 ≈ 5m. 1초 간격이므로 약 5m/s.
            points.add(new TrackPoint("ride", START_MS + i * 1000L,
                    37.2000 + i * 0.000045, 127.1000, accuracyM, 5.0));
        }
        return points;
    }

    @Test
    @DisplayName("정상 신호는 전 구간이 유효로 판정된다")
    void cleanRideIsAllValid() {
        QualityReport report = service.evaluate(straightRide(20, 8.0));

        assertThat(report.segments()).allMatch(RideSegment::isValid);
        assertThat(report.invalidDistanceM()).isZero();
        assertThat(report.validDistanceM()).isGreaterThan(0);
        assertThat(report.gpsQualityScore()).isGreaterThan(0.8);
    }

    @Test
    @DisplayName("GPS 정확도가 임계값을 넘으면 무효가 아니라 보류로 떨어진다")
    void lowAccuracyBecomesPending() {
        List<TrackPoint> points = straightRide(10, 8.0);
        points.set(5, new TrackPoint("ride", points.get(5).getEpochMs(),
                points.get(5).getLat(), points.get(5).getLon(), 55.0, 5.0));

        QualityReport report = service.evaluate(points);

        assertThat(report.segments()).anyMatch(s -> s.validity() == Validity.PENDING);
        assertThat(report.pendingDistanceM()).isGreaterThan(0);
        assertThat(report.segments()).noneMatch(s -> s.validity() == Validity.INVALID);
    }

    @Test
    @DisplayName("좌표가 순간적으로 튀면 해당 구간을 무효로 제외한다")
    void coordinateJumpIsInvalid() {
        List<TrackPoint> points = straightRide(10, 8.0);
        // 한 표본만 400m 북쪽으로 옮겨 환산 속도를 90km/h 이상으로 만듭니다.
        points.set(5, new TrackPoint("ride", points.get(5).getEpochMs(),
                points.get(5).getLat() + 0.0036, points.get(5).getLon(), 8.0, 5.0));

        QualityReport report = service.evaluate(points);

        assertThat(report.segments()).anyMatch(s -> s.validity() == Validity.INVALID
                && s.reason().contains("좌표 점프"));
        assertThat(report.invalidDistanceM()).isGreaterThan(0);
    }

    @Test
    @DisplayName("신호 공백 구간은 직선으로 이어 붙이지 않고 무효로 처리한다")
    void signalGapIsInvalid() {
        List<TrackPoint> points = new ArrayList<>(straightRide(6, 8.0));
        // 45초 공백 후 이어지는 표본.
        points.add(new TrackPoint("ride", START_MS + 6 * 1000L + 45_000L,
                37.2000 + 6 * 0.000045, 127.1000, 8.0, 5.0));

        QualityReport report = service.evaluate(points);

        assertThat(report.segments()).anyMatch(s -> s.validity() == Validity.INVALID
                && s.reason().contains("신호 공백"));
    }

    @Test
    @DisplayName("시각이 역순이거나 중복인 표본은 구간을 만들지 않고 버린다")
    void outOfOrderSamplesAreDropped() {
        List<TrackPoint> points = new ArrayList<>(straightRide(5, 8.0));
        points.add(3, new TrackPoint("ride", START_MS + 1000L, 37.2001, 127.1000, 8.0, 5.0));

        QualityReport report = service.evaluate(points);

        assertThat(report.droppedPoints()).isPositive();
    }

    @Test
    @DisplayName("표본이 부족하면 빈 결과를 돌려준다")
    void tooFewSamples() {
        assertThat(service.evaluate(straightRide(1, 8.0)).segments()).isEmpty();
    }
}
