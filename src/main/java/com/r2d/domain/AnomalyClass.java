package com.r2d.domain;

/**
 * 노면 이상 "후보" 클래스.
 *
 * <p>초기 빌드는 센서만으로 포트홀·균열·단차 같은 확정 결함명을 붙이지 않습니다.
 * 여기 있는 값은 전부 신뢰도 중심의 후보 상태이며, 세부 결함명 부여는 카메라
 * 교차검증과 전문가 라벨이 확보된 이후(P1) 범위입니다.
 */
public enum AnomalyClass {

    /** 정상 시설물: 과속방지턱·교량 이음부·연석처럼 파손이 아닌 규칙적 충격. */
    NORMAL_FEATURE("정상 시설물"),

    /** 충격성 이상 후보: 짧고 날카로운 단일 충격 파형. */
    IMPACT_ANOMALY_CANDIDATE("충격성 이상 후보"),

    /** 반복 진동성 이상 후보: 일정 거리에 걸쳐 지속되는 고주파 진동. */
    VIBRATION_ANOMALY_CANDIDATE("반복 진동성 이상 후보"),

    /** 판정 보류: 거치 변화·저품질 신호 등으로 분류를 확정할 수 없음. */
    JUDGEMENT_PENDING("판정 보류");

    private final String label;

    AnomalyClass(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 서브몹으로 만들어 추적할 가치가 있는 후보인지. */
    public boolean isTrackable() {
        return this == IMPACT_ANOMALY_CANDIDATE || this == VIBRATION_ANOMALY_CANDIDATE;
    }
}
