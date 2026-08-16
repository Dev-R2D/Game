package com.r2d.domain;

/** 노면 이상 서브몹의 생애주기. 관측이 쌓일수록 상태가 올라가는 것이 성장 연출이 됩니다. */
public enum AnomalyState {

    /** 의심: 관측은 있으나 독립 관측·신뢰도가 부족. 회색 몹 + 잠금 상태의 기념 카드. */
    SUSPECT("의심"),

    /** 확정: 서버 신뢰도 기준 충족. 활성 몹 전환, 격파 보상, 잠금 카드 해제. */
    CONFIRMED("확정"),

    /** 소멸: 보수·상태 변화 후 재주행에서 기존 패턴이 반복되지 않음. */
    RESOLVED("소멸");

    private final String label;

    AnomalyState(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
