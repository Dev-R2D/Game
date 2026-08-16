package com.r2d.boss;

/**
 * 보스 등급.
 *
 * <p>활성 라이더 수로 HP를 나누는 대신 등급을 정합니다. 라이더가 적은 지역에서 혼자
 * 감당 못 할 HP가 나오거나, 라이더가 많은 지역에서 하루 만에 끝나 버리는 것을 막기 위해서입니다.
 */
public enum BossTier {

    /** 개인형: 활성 라이더가 적은 지역. 혼자서도 며칠 안에 완주 가능한 규모. */
    SOLO("개인형", 1, 0.35, 2),

    /** 소규모 협동형: 몇 명이 나눠 달리면 한 주 안에 끝나는 규모. */
    SMALL_CO_OP("소규모 협동형", 5, 0.7, 3),

    /** 지역 공동형: 행정동 단위 참여를 전제로 한 규모. */
    REGIONAL("지역 공동형", 20, 1.0, 4);

    private final String label;
    private final int minActiveRiders;
    private final double hpScale;
    private final int phaseCount;

    BossTier(String label, int minActiveRiders, double hpScale, int phaseCount) {
        this.label = label;
        this.minActiveRiders = minActiveRiders;
        this.hpScale = hpScale;
        this.phaseCount = phaseCount;
    }

    /** 활성 라이더 수에 맞는 등급. */
    public static BossTier forActiveRiders(int activeRiders) {
        if (activeRiders >= REGIONAL.minActiveRiders) {
            return REGIONAL;
        }
        if (activeRiders >= SMALL_CO_OP.minActiveRiders) {
            return SMALL_CO_OP;
        }
        return SOLO;
    }

    public String getLabel() {
        return label;
    }

    public double getHpScale() {
        return hpScale;
    }

    public int getPhaseCount() {
        return phaseCount;
    }
}
