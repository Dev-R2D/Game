package com.r2d.reward;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 지역기여 점수 원장.
 *
 * <p>이 점수는 결제수단도, 쿠폰 구매대금도 아닙니다. 공개된 캠페인 목표에 대한 진행도일 뿐이며
 * 차감·양도·현금화가 없습니다. 그래서 이 엔티티에는 점수를 빼는 연산 자체를 두지 않았습니다.
 * 목표 도달 여부만 조회하고, 쿠폰 발급은 별도 원장에서 1회성으로 처리합니다(P1 범위).
 *
 * <p>유료 상품이 이 점수의 적립률을 올릴 수 없어야 하므로, 적립 계산에는 결제 관련 입력이
 * 전혀 들어가지 않습니다.
 */
@Entity
@Table(name = "region_contribution_ledgers",
        uniqueConstraints = @UniqueConstraint(name = "uk_region_contribution",
                columnNames = {"playerId", "regionCode"}))
public class RegionContributionLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long playerId;

    @Column(nullable = false, length = 20)
    private String regionCode;

    /** 누적 진행도. 단조 증가만 합니다. */
    @Column(nullable = false)
    private long points;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected RegionContributionLedger() {
    }

    public RegionContributionLedger(Long playerId, String regionCode) {
        this.playerId = playerId;
        this.regionCode = regionCode;
    }

    /** 검증된 안전 주행과 데이터 기여만 적립합니다. 음수 적립은 받지 않습니다. */
    public void accrue(long gained) {
        if (gained <= 0) {
            return;
        }
        this.points += gained;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public long getPoints() {
        return points;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
