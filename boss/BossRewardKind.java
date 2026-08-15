package com.r2d.boss;

/** 보스 보상의 종류. */
public enum BossRewardKind {

    /**
     * 페이즈 중간 보상.
     *
     * <p>혼자 완주하지 못해도 참여한 만큼은 회수할 수 있어야 하므로, 보스가 다음 단계로
     * 넘어갈 때마다 그때까지 기여한 사람들에게 지급합니다.
     */
    PHASE_CLEAR("단계 돌파"),

    /** 처치 보상. 보스가 쓰러졌을 때 참여자 전원에게 기여 비례로 지급합니다. */
    BOSS_DEFEAT("보스 처치");

    private final String label;

    BossRewardKind(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
