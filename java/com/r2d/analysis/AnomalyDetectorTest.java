package com.r2d.analysis;

import java.util.List;

import com.r2d.config.TestRules;
import com.r2d.domain.AnomalyClass;
import com.r2d.ride.ImuWindow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 후보 분류기 테스트.
 *
 * <p>가장 중요한 것은 오탐 반례입니다. 과속방지턱처럼 파손이 아닌 충격이 이상 후보로 올라가면
 * 데이터가 통째로 오염되므로, 그 경우를 먼저 검증합니다.
 */
class AnomalyDetectorTest {

    private final AnomalyDetector detector = new AnomalyDetector(TestRules.defaults());

    private static ImuWindow window(int durationMs, double peakG, double rmsG, double dominantHz,
                                    double gyroRmsDps, double entrySpeedMps, boolean mountStable) {
        return new ImuWindow("ride", 1_000L, 37.2, 127.1, durationMs, peakG, rmsG, dominantHz,
                gyroRmsDps, entrySpeedMps, mountStable);
    }

    @Test
    @DisplayName("과속방지턱처럼 길고 완만한 저주파 충격은 정상 시설물로 배제한다")
    void speedBumpIsNormalFeature() {
        AnomalyDetection detection = detector.detect(List.of(
                window(420, 3.0, 0.40, 7.0, 12.0, 4.5, true))).get(0);

        assertThat(detection.anomalyClass()).isEqualTo(AnomalyClass.NORMAL_FEATURE);
        assertThat(detection.isTrackable()).isFalse();
    }

    @Test
    @DisplayName("짧고 날카로운 고주파 단일 충격은 충격성 이상 후보로 분류한다")
    void sharpSpikeIsImpactCandidate() {
        AnomalyDetection detection = detector.detect(List.of(
                window(90, 3.4, 0.50, 26.0, 30.0, 4.5, true))).get(0);

        assertThat(detection.anomalyClass()).isEqualTo(AnomalyClass.IMPACT_ANOMALY_CANDIDATE);
        assertThat(detection.isTrackable()).isTrue();
    }

    @Test
    @DisplayName("일정 거리에 걸친 지속 진동은 반복 진동성 이상 후보로 분류한다")
    void sustainedVibrationIsVibrationCandidate() {
        AnomalyDetection detection = detector.detect(List.of(
                window(1400, 1.9, 0.72, 18.0, 20.0, 4.5, true))).get(0);

        assertThat(detection.anomalyClass()).isEqualTo(AnomalyClass.VIBRATION_ANOMALY_CANDIDATE);
    }

    @Test
    @DisplayName("거치가 흔들린 구간은 피크가 커도 판정 보류로 내린다")
    void unstableMountIsPending() {
        AnomalyDetection detection = detector.detect(List.of(
                window(200, 3.8, 0.30, 22.0, 95.0, 4.5, false))).get(0);

        assertThat(detection.anomalyClass()).isEqualTo(AnomalyClass.JUDGEMENT_PENDING);
        assertThat(detection.isTrackable()).isFalse();
    }

    @Test
    @DisplayName("같은 파형이라도 진입 속도가 빠르면 충격을 정규화해 과대 판정하지 않는다")
    void normalizesByEntrySpeed() {
        AnomalyDetection slow = detector.detect(List.of(
                window(90, 3.0, 0.30, 26.0, 25.0, 2.0, true))).get(0);
        AnomalyDetection fast = detector.detect(List.of(
                window(90, 3.0, 0.30, 26.0, 25.0, 11.0, true))).get(0);

        assertThat(slow.anomalyClass()).isEqualTo(AnomalyClass.IMPACT_ANOMALY_CANDIDATE);
        // 같은 피크라도 빠르게 진입했다면 후보 기준에 못 미칩니다.
        assertThat(fast.anomalyClass()).isEqualTo(AnomalyClass.NORMAL_FEATURE);
    }

    @Test
    @DisplayName("단일 관측만으로는 신뢰도가 0.85를 넘지 못한다")
    void singleObservationConfidenceIsCapped() {
        AnomalyDetection detection = detector.detect(List.of(
                window(20, 9.0, 0.90, 40.0, 60.0, 3.0, true))).get(0);

        assertThat(detection.confidence()).isLessThanOrEqualTo(0.85);
    }
}
