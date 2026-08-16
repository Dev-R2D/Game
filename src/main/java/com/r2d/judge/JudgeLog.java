package com.r2d.judge;

import java.util.List;

/**
 * 심사 재현용 주행 로그 정의.
 *
 * <p>로그는 표본 수만 개를 그대로 담지 않고 경로·속도·이벤트 명세로 저장한 뒤 결정론적으로
 * 펼칩니다. 같은 로그는 몇 번을 재생해도 같은 표본을 만듭니다.
 *
 * <p><b>출처 표기가 이 파일의 핵심입니다.</b> 파생 로그를 "실제 서로 다른 라이더의 원본
 * 데이터"로 표기하지 않기 위해 {@link SourceType}과 {@code derivedFrom}을 필수로 둡니다.
 */
public record JudgeLog(
        String logId,
        String title,
        SourceType sourceType,
        /** 심사자에게 그대로 보여줄 출처 문장. */
        String sourceLabel,
        /** 파생 로그일 때 원본 로그 ID. 원본이 아니면 null입니다. */
        String derivedFrom,
        String purpose,
        String regionCode,
        /** 경로 꼭짓점. 인접 꼭짓점 사이는 직선 보간합니다. */
        List<Waypoint> waypoints,
        double speedMps,
        int sampleIntervalMs,
        /** GPS 오차 반경의 기준값(m). */
        double baseAccuracyM,
        List<EventSpec> events,
        /** 의도적으로 심어 둔 신호 품질 결함. 보류·무효 판정이 실제로 동작하는지 보이기 위한 것입니다. */
        List<QualityFault> qualityFaults,
        /** 파생 로그에 적용할 통제된 편차. 원본 로그에서는 null입니다. */
        Deviation deviation
) {

    /** 로그의 출처 종류. */
    public enum SourceType {
        /** 실제 자전거 주행에서 수집한 기준 로그. */
        REAL_RIDE("실측 주행 로그"),
        /** 판정 규칙 확인을 위해 명시적으로 만든 테스트 로그. */
        TEST("테스트 로그"),
        /** 기준 로그에 통제된 편차를 적용해 만든 파생 로그. */
        DERIVED("파생 로그");

        private final String label;

        SourceType(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public record Waypoint(double lat, double lon) {
    }

    /**
     * 경로 위 특정 지점에서 발생하는 IMU 이벤트.
     *
     * @param atProgress 경로 진행률(0~1)
     */
    public record EventSpec(
            double atProgress,
            int durationMs,
            double peakG,
            double rmsG,
            double dominantHz,
            double gyroRmsDps,
            boolean mountStable,
            /** 이 이벤트가 무엇을 재현하려는 것인지. 오탐 반례를 명시적으로 포함하기 위한 설명입니다. */
            String intent
    ) {
    }

    /**
     * 신호 품질 결함 주입.
     *
     * <p>실제 주행에는 터널·건물 그늘·기기 이상이 늘 섞입니다. 결함이 하나도 없는 로그만
     * 재생하면 보류·무효 판정이 동작하는지 확인할 수 없으므로 의도적으로 심어 둡니다.
     *
     * @param kind 결함 종류
     * @param magnitude ACCURACY_SPIKE는 오차 반경(m), COORD_JUMP는 튀는 거리(m), TIME_GAP은 공백 시간(초)
     */
    public record QualityFault(double atProgress, FaultKind kind, double magnitude) {
    }

    /** 주입 가능한 결함 종류. */
    public enum FaultKind {
        /** GPS 오차 반경이 급격히 커지는 구간 → 보류. */
        ACCURACY_SPIKE,
        /** 좌표가 순간적으로 튀는 지점 → 무효. */
        COORD_JUMP,
        /** 신호가 끊긴 구간 → 무효. */
        TIME_GAP
    }

    /**
     * 파생 로그의 통제된 편차.
     *
     * @param positionJitterM 위치에 더할 오차 크기(m)
     * @param speedScale 속도 배율
     * @param sensorScale IMU 특징값 배율
     * @param seed 난수 시드. 같은 시드는 항상 같은 편차를 만듭니다.
     */
    public record Deviation(double positionJitterM, double speedScale, double sensorScale, long seed) {
    }

    /** 심사자에게 표시할 출처 한 줄. */
    public String provenance() {
        if (sourceType == SourceType.DERIVED) {
            return sourceType.getLabel() + " · 원본 " + derivedFrom + "에서 통제된 편차로 생성 · " + sourceLabel;
        }
        return sourceType.getLabel() + " · " + sourceLabel;
    }
}
