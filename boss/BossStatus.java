package com.r2d.boss;

/** 보스 진행 상태. */
public enum BossStatus {
    ACTIVE("진행 중"),
    CLEARED("처치 완료");

    private final String label;

    BossStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
