package com.r2d.analysis;

import java.util.List;

/** 위치 신뢰도 검증 결과 전체. */
public record QualityReport(
        List<RideSegment> segments,
        double totalDistanceM,
        double validDistanceM,
        double pendingDistanceM,
        double invalidDistanceM,
        /** 0~1. 표본의 GPS 정확도 분포를 요약한 점수입니다. */
        double gpsQualityScore,
        /** 시각 역순·중복으로 버린 표본 수. */
        int droppedPoints,
        List<String> notes
) {

    public static QualityReport empty() {
        return new QualityReport(List.of(), 0, 0, 0, 0, 0, 0, List.of("위치 표본이 없습니다."));
    }
}
