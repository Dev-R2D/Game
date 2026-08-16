package com.r2d.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 게임 밸런스와 판정 임계값 전체.
 *
 * <p>실제 자전거·차량·도보 로그로 임계값을 검증한 뒤 원격 설정으로 조정할 수 있어야 하므로,
 * 판정에 쓰이는 숫자는 코드에 흩어 두지 않고 전부 여기로 모읍니다.
 * {@code application.yml}의 {@code r2d.*}로 덮어쓸 수 있고, 값이 없으면 아래 기본값을 씁니다.
 */
@ConfigurationProperties(prefix = "r2d")
public record GameRules(
        @DefaultValue Quality quality,
        @DefaultValue Transport transport,
        @DefaultValue Anomaly anomaly,
        @DefaultValue Cell cell,
        @DefaultValue Damage damage,
        @DefaultValue Boss boss,
        @DefaultValue Pack pack,
        @DefaultValue DailyCap dailyCap
) {

    /** 위치 신뢰도 검증 임계값. */
    public record Quality(
            /** 이 값을 넘는 GPS 오차 반경(m)은 저품질로 봅니다. */
            @DefaultValue("30.0") double maxAccuracyM,
            /** 좌표 점프 판정: 두 점 사이 환산 속도가 이 값(m/s)을 넘으면 무효. 25m/s = 90km/h. */
            @DefaultValue("25.0") double maxImpliedSpeedMps,
            /** 두 점 사이 간격이 이 값(초)을 넘으면 신호 공백으로 보고 거리를 잇지 않습니다. */
            @DefaultValue("30.0") double maxGapSeconds,
            /** 셀 관측을 VALID로 인정하기 위한 최소 고품질 표본 비율. */
            @DefaultValue("0.6") double minValidSampleRatio,
            /** 신뢰도 계수의 하한. 아무리 품질이 나빠도 기본 성장은 남깁니다. */
            @DefaultValue("0.5") double minConfidenceCoefficient
    ) {
    }

    /** 주행 수단 판별 임계값. */
    public record Transport(
            /** 도보 판정: p95 속도가 이 값(m/s) 미만. */
            @DefaultValue("2.5") double walkP95Mps,
            /** 자전거 상한: p95 속도가 이 값(m/s)을 넘으면 자전거로 보지 않습니다. */
            @DefaultValue("12.0") double bicycleMaxP95Mps,
            /** 차량 판정: p95 속도가 이 값(m/s) 이상. 14m/s ≈ 50km/h. */
            @DefaultValue("14.0") double vehicleP95Mps,
            /** 차량 판정 보조: 평균 속도가 높은데 노면 진동이 이 값 미만이면 완충된 차량으로 봅니다. */
            @DefaultValue("0.12") double vehicleMaxVibrationG,
            /** 이 신뢰도 미만이면 자전거로 확정하지 않고 기여를 보류합니다. */
            @DefaultValue("0.7") double bicycleConfirmConfidence
    ) {
    }

    /** 노면 이상 후보 분류·검증 임계값. */
    public record Anomaly(
            /** 충격성 후보의 최대 지속 시간(ms). 짧고 날카로울수록 파손 후보에 가깝습니다. */
            @DefaultValue("150") int impactMaxDurationMs,
            /** 정상 시설물(과속방지턱 등)로 보는 최소 지속 시간(ms). */
            @DefaultValue("300") int normalFeatureMinDurationMs,
            /** 후보로 올리기 위한 최소 피크 가속도(g). */
            @DefaultValue("1.8") double minPeakG,
            /** 충격성 후보로 보는 최소 주파수 성분(Hz). 낮은 주파수는 완만한 굴곡입니다. */
            @DefaultValue("12.0") double impactMinDominantHz,
            /** 반복 진동성 후보로 보는 최소 RMS 가속도(g)와 최소 지속 시간(ms). */
            @DefaultValue("0.55") double vibrationMinRmsG,
            @DefaultValue("800") int vibrationMinDurationMs,
            /** 확정 전이: 서로 다른 이용자 수 기준. */
            @DefaultValue("2") int confirmDistinctUsers,
            /** 확정 전이(저밀도 지역 대체 경로): 같은 이용자라도 날짜가 분리된 관측 수 기준. */
            @DefaultValue("3") int confirmDistinctDays,
            /** 확정 전이에 필요한 누적 신뢰도. */
            @DefaultValue("0.7") double confirmConfidence,
            /** 소멸 전이: 이상 패턴 없이 통과한 서로 다른 주행 수. */
            @DefaultValue("3") int resolveCleanRides
    ) {
    }

    /** 셀 상태 전이 규칙. */
    public record Cell(
            /** 마지막 유효 관측이 이 일수를 넘기면 갱신 필요(STALE)로 내려갑니다. */
            @DefaultValue("30") int freshnessDays,
            /** 이 값 미만의 누적 신뢰도는 낮은 신뢰도(LOW_CONFIDENCE) 상태로 둡니다. */
            @DefaultValue("0.6") double lowConfidenceThreshold,
            /** 같은 이용자의 반복 방문 보너스 감쇠를 계산하는 기간(일). */
            @DefaultValue("7") int repeatWindowDays,
            /** 반복 방문 1회마다 적용되는 기여 보너스 감쇠 계수. */
            @DefaultValue("0.5") double repeatDecayFactor
    ) {
    }

    /** 확정 피해 계산 계수. */
    public record Damage(
            /** 유효 거리 1m당 기본 피해. */
            @DefaultValue("1.0") double perMeter,
            /** 지구 계열 카드 1장당 기본 피해 보정. */
            @DefaultValue("0.15") double enduranceBonusPerCard,
            /** 계열 카드 1장당 해당 기여 종류의 배율 보정. */
            @DefaultValue("0.20") double lineBonusPerCard,
            /** 같은 계열 3장(상위 시너지). */
            @DefaultValue("1.30") double synergyTriple,
            /** 같은 계열 2장(계열 시너지). */
            @DefaultValue("1.15") double synergyPair,
            /** 계열 3종을 섞은 범용 덱. 최대 배율은 낮지만 어떤 셀을 만나도 손해가 적습니다. */
            @DefaultValue("1.05") double synergyMixed
    ) {
    }

    /** 지역 공동 보스 규칙. */
    public record Boss(
            /** 데이터 필요량 1단위당 보스 HP. */
            @DefaultValue("1200.0") double hpPerNeedUnit,
            /** 체력바를 나누는 단계 수. 혼자 완주하지 못해도 중간 보상을 회수할 수 있게 합니다. */
            @DefaultValue("3") int phaseCount,
            /** 보스 최소 HP. 데이터 수요가 적은 지역에서도 콘텐츠가 성립하도록 하한을 둡니다. */
            @DefaultValue("20000.0") double minHp
    ) {
    }

    /** 탐사 팩 등급 하한 규칙. */
    public record Pack(
            /** 골드 하한: 이번 주행에서 확보한 미탐사 셀 수. */
            @DefaultValue("5") int goldNewCells,
            /** 실버 하한: 이번 주행에서 갱신·검증한 셀 수. */
            @DefaultValue("4") int silverUpdatedCells,
            /** 하한 위에서 한 등급 올라갈 확률. 등급을 내리는 굴림은 없습니다. */
            @DefaultValue("0.15") double upgradeChance
    ) {
    }

    /** 일일 상한. 무제한 장거리·야간 원정 경쟁을 막기 위한 안전 장치입니다. */
    public record DailyCap(
            @DefaultValue("5") int packs,
            @DefaultValue("3000") int xp,
            @DefaultValue("500") int regionContributionPoints
    ) {
    }
}
