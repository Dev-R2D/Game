package com.r2d.region;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 행정동 단위 지역.
 *
 * <p>지역 공동 보스와 지역 정화 진행도의 단위입니다. 실제 서비스에서는 행정동 폴리곤을
 * 쓰지만, 제출 빌드에서는 셀에 지역 코드를 직접 부여하는 방식으로 단순화했습니다.
 */
@Entity
@Table(name = "regions")
public class Region {

    /** 행정동 코드. */
    @Id
    @Column(length = 20)
    private String code;

    @Column(nullable = false, length = 60)
    private String name;

    /** 이 지역에서 활동 중인 라이더 수. 보스 HP를 나누는 데 쓰지 않고 보스 등급 결정에만 씁니다. */
    @Column(nullable = false)
    private int activeRiders;

    protected Region() {
    }

    public Region(String code, String name, int activeRiders) {
        this.code = code;
        this.name = name;
        this.activeRiders = activeRiders;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getActiveRiders() {
        return activeRiders;
    }

    public void setActiveRiders(int activeRiders) {
        this.activeRiders = activeRiders;
    }
}
