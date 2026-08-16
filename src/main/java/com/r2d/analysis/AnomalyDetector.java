package com.r2d.analysis;

import java.util.ArrayList;
import java.util.List;

import com.r2d.common.CellGrid;
import com.r2d.config.GameRules;
import com.r2d.domain.AnomalyClass;
import com.r2d.ride.ImuWindow;

import org.springframework.stereotype.Service;

/**
 * 노면 이상 후보 분류.
 *
 * <p>먼저 "무엇이 아닌지"를 가립니다. 과속방지턱, 교량 이음부, 연석, 거치대 흔들림은 노면
 * 파손과 비슷한 크기의 충격을 만들기 때문에, 피크값만 보면 오탐이 그대로 데이터가 됩니다.
 * 그래서 지속 시간, 우세 주파수, 진입 속도, 자이로 응답, 거치 안정성을 함께 봅니다.
 *
 * <p>규칙 기반인 이유: 제출 빌드 시점에 기기별·장착별 라벨 데이터셋이 없으므로, 학습 모델의
 * 정확도를 주장하는 대신 판정 규칙과 오탐 반례를 공개할 수 있는 형태를 택했습니다.
 * 임계값은 {@link GameRules}에 모아 두어 실측 로그로 검증한 뒤 조정할 수 있습니다.
 */
@Service
public class AnomalyDetector {

    private final GameRules rules;

    public AnomalyDetector(GameRules rules) {
        this.rules = rules;
    }

    public List<AnomalyDetection> detect(List<ImuWindow> windows) {
        List<AnomalyDetection> detections = new ArrayList<>();
        for (ImuWindow window : windows) {
            detections.add(classify(window));
        }
        return List.copyOf(detections);
    }

    private AnomalyDetection classify(ImuWindow w) {
        GameRules.Anomaly a = rules.anomaly();
        String cellId = CellGrid.cellIdOf(w.getLat(), w.getLon());

        // 거치 방향이 바뀌는 중이면 어떤 파형도 신뢰할 수 없습니다. 값을 해석하지 않고 보류합니다.
        if (!w.isMountStable()) {
            return detection(w, cellId, AnomalyClass.JUDGEMENT_PENDING, 0.15,
                    "거치 변화가 감지되어 파형을 해석하지 않았습니다.");
        }

        // 같은 요철도 빠르게 지나면 충격이 커집니다. 속도로 정규화해야 저속 주행의 진짜 이상이 묻히지 않습니다.
        double normalizedPeak = w.getPeakG() / (1.0 + w.getEntrySpeedMps() / 10.0);

        if (normalizedPeak < a.minPeakG() && w.getRmsG() < a.vibrationMinRmsG()) {
            return detection(w, cellId, AnomalyClass.NORMAL_FEATURE, 0.8,
                    "정규화 피크 " + fmt(normalizedPeak) + "g로 이상 후보 기준 미만입니다.");
        }

        // 길고 완만한 충격은 과속방지턱·교량 이음부처럼 의도된 시설물의 전형적인 파형입니다.
        if (w.getDurationMs() >= a.normalFeatureMinDurationMs() && w.getDominantHz() < a.impactMinDominantHz()) {
            return detection(w, cellId, AnomalyClass.NORMAL_FEATURE, 0.75,
                    "지속 " + w.getDurationMs() + "ms · 우세 " + fmt(w.getDominantHz())
                            + "Hz로 완만한 시설물 파형에 가깝습니다.");
        }

        // 짧고 날카로우며 고주파인 단일 충격.
        if (w.getDurationMs() <= a.impactMaxDurationMs()
                && normalizedPeak >= a.minPeakG()
                && w.getDominantHz() >= a.impactMinDominantHz()) {
            double confidence = confidenceOf(
                    normalizedPeak / (a.minPeakG() * 2.0),
                    (double) (a.impactMaxDurationMs() - w.getDurationMs()) / a.impactMaxDurationMs(),
                    w.getDominantHz() / (a.impactMinDominantHz() * 2.5));
            return detection(w, cellId, AnomalyClass.IMPACT_ANOMALY_CANDIDATE, confidence,
                    "지속 " + w.getDurationMs() + "ms · 정규화 피크 " + fmt(normalizedPeak)
                            + "g · 우세 " + fmt(w.getDominantHz()) + "Hz의 충격성 파형입니다.");
        }

        // 일정 거리에 걸쳐 이어지는 고주파 진동.
        if (w.getDurationMs() >= a.vibrationMinDurationMs() && w.getRmsG() >= a.vibrationMinRmsG()) {
            double confidence = confidenceOf(
                    w.getRmsG() / (a.vibrationMinRmsG() * 2.0),
                    (double) w.getDurationMs() / (a.vibrationMinDurationMs() * 2.0),
                    w.getDominantHz() / (a.impactMinDominantHz() * 2.0));
            return detection(w, cellId, AnomalyClass.VIBRATION_ANOMALY_CANDIDATE, confidence,
                    "지속 " + w.getDurationMs() + "ms 동안 RMS " + fmt(w.getRmsG())
                            + "g의 진동이 유지되었습니다.");
        }

        return detection(w, cellId, AnomalyClass.JUDGEMENT_PENDING, 0.3,
                "어느 후보 클래스의 파형 조건도 충족하지 않아 보류합니다.");
    }

    /**
     * 세 특징의 적합도를 평균해 신뢰도를 만듭니다.
     *
     * <p>단일 관측의 신뢰도는 0.85를 넘지 못하게 막습니다. 한 번의 파형만으로 이상을 확신하지
     * 않는다는 규칙을 숫자 자체로 강제하기 위해서입니다. 확정은 교차검증에서만 일어납니다.
     */
    private static double confidenceOf(double... fits) {
        double sum = 0;
        for (double fit : fits) {
            sum += Math.max(0.0, Math.min(1.0, fit));
        }
        return Math.min(0.85, 0.35 + 0.5 * (sum / fits.length));
    }

    private static AnomalyDetection detection(ImuWindow w, String cellId, AnomalyClass anomalyClass,
                                              double confidence, String reason) {
        return new AnomalyDetection(cellId, w.getLat(), w.getLon(), w.getEpochMs(),
                anomalyClass, confidence, reason);
    }

    private static String fmt(double value) {
        return String.format("%.2f", value);
    }
}
