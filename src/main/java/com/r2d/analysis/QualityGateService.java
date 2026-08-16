package com.r2d.analysis;

import java.util.ArrayList;
import java.util.List;

import com.r2d.common.CellGrid;
import com.r2d.common.GeoUtils;
import com.r2d.config.GameRules;
import com.r2d.domain.Validity;
import com.r2d.ride.TrackPoint;

import org.springframework.stereotype.Service;

/**
 * 위치 신뢰도 검증.
 *
 * <p>GPS 정확도, 좌표 점프, 시각 순서, 신호 공백을 보고 구간별로 유효/보류/무효를 매깁니다.
 * 여기서 무효로 떨어진 거리는 기본 피해에도 들어가지 않고, 보류로 떨어진 거리는 기본 피해만
 * 인정하고 데이터 기여에서는 빠집니다.
 */
@Service
public class QualityGateService {

    private final GameRules rules;

    public QualityGateService(GameRules rules) {
        this.rules = rules;
    }

    /**
     * 시간순으로 정렬된 위치 표본에서 구간별 유효성을 판정합니다.
     *
     * @param points {@code epochMs} 오름차순으로 정렬된 표본
     */
    public QualityReport evaluate(List<TrackPoint> points) {
        if (points == null || points.size() < 2) {
            return QualityReport.empty();
        }

        GameRules.Quality q = rules.quality();
        List<RideSegment> segments = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        int dropped = 0;
        double accuracySum = 0;
        int accuracyCount = 0;

        TrackPoint previous = points.get(0);
        for (int i = 1; i < points.size(); i++) {
            TrackPoint current = points.get(i);

            // 시각이 뒤로 가거나 같은 표본은 중복 재전송 또는 기기 시계 이상입니다. 구간을 만들지 않고 버립니다.
            if (current.getEpochMs() <= previous.getEpochMs()) {
                dropped++;
                continue;
            }

            double seconds = (current.getEpochMs() - previous.getEpochMs()) / 1000.0;
            double distance = GeoUtils.distanceMeters(previous.getLat(), previous.getLon(),
                    current.getLat(), current.getLon());
            double impliedSpeed = distance / seconds;
            double worstAccuracy = Math.max(previous.getAccuracyM(), current.getAccuracyM());

            Validity validity;
            String reason;
            if (seconds > q.maxGapSeconds()) {
                // 터널·건물 그늘 등으로 신호가 끊긴 구간. 직선으로 이어 붙이면 없는 거리가 생깁니다.
                validity = Validity.INVALID;
                reason = "신호 공백 " + Math.round(seconds) + "초";
            } else if (impliedSpeed > q.maxImpliedSpeedMps()) {
                validity = Validity.INVALID;
                reason = "좌표 점프 " + String.format("%.1f", impliedSpeed) + "m/s";
            } else if (worstAccuracy > q.maxAccuracyM()) {
                validity = Validity.PENDING;
                reason = "GPS 정확도 저하 " + String.format("%.0f", worstAccuracy) + "m";
            } else {
                validity = Validity.VALID;
                reason = null;
            }

            double midLat = (previous.getLat() + current.getLat()) / 2;
            double midLon = (previous.getLon() + current.getLon()) / 2;

            segments.add(new RideSegment(previous.getEpochMs(), current.getEpochMs(), distance,
                    impliedSpeed, worstAccuracy, midLat, midLon,
                    CellGrid.cellIdOf(midLat, midLon), validity, reason));

            accuracySum += worstAccuracy;
            accuracyCount++;
            previous = current;
        }

        double total = 0;
        double valid = 0;
        double pending = 0;
        double invalid = 0;
        for (RideSegment segment : segments) {
            total += segment.distanceM();
            switch (segment.validity()) {
                case VALID -> valid += segment.distanceM();
                case PENDING -> pending += segment.distanceM();
                case INVALID -> invalid += segment.distanceM();
            }
        }

        if (dropped > 0) {
            notes.add("시각 역순·중복 표본 " + dropped + "개를 제외했습니다.");
        }
        if (invalid > 0) {
            notes.add("무효 구간 " + Math.round(invalid) + "m는 피해와 기여에서 모두 제외했습니다.");
        }
        if (pending > 0) {
            notes.add("보류 구간 " + Math.round(pending) + "m는 기본 피해만 인정했습니다.");
        }

        double meanAccuracy = accuracyCount == 0 ? q.maxAccuracyM() : accuracySum / accuracyCount;
        // 정확도가 좋을수록 1에 가깝고, 임계값을 넘어가면 0으로 수렴합니다.
        double gpsQualityScore = Math.max(0.0, Math.min(1.0, 1.0 - (meanAccuracy / (q.maxAccuracyM() * 2))));

        return new QualityReport(List.copyOf(segments), total, valid, pending, invalid,
                gpsQualityScore, dropped, List.copyOf(notes));
    }
}
