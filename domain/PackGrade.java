package com.r2d.domain;

/**
 * 탐사 팩 등급.
 *
 * <p>팩 내부 구성에는 확률이 있지만 등급 자체는 순수 확률로 정하지 않습니다.
 * 신규·갱신·검증 기여가 등급 하한을 올리고, 확률은 그 하한 위에서만 굴립니다.
 */
public enum PackGrade {
    BRONZE("브론즈", 0),
    SILVER("실버", 1),
    GOLD("골드", 2),
    SIGNATURE("시그니처", 3);

    private final String label;
    private final int rank;

    PackGrade(String label, int rank) {
        this.label = label;
        this.rank = rank;
    }

    public String getLabel() {
        return label;
    }

    public int getRank() {
        return rank;
    }

    /** 등급 하한과 굴림 결과 중 높은 쪽. 하한 아래로는 절대 내려가지 않습니다. */
    public PackGrade atLeast(PackGrade floor) {
        return this.rank >= floor.rank ? this : floor;
    }

    public static PackGrade ofRank(int rank) {
        for (PackGrade grade : values()) {
            if (grade.rank == rank) {
                return grade;
            }
        }
        throw new IllegalArgumentException("알 수 없는 팩 등급 rank: " + rank);
    }
}
