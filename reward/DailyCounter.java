package com.r2d.reward;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 일일 보상 상한 카운터.
 *
 * <p>무제한 장거리 경쟁과 야간 원정 경쟁이 생기지 않도록 팩·경험치·지역기여 점수에 상한을 둡니다.
 * 상한에 걸려도 주행 자체는 계속 인정되고 도로 데이터도 그대로 쌓입니다. 상한은 보상에만 걸립니다.
 */
@Entity
// day는 H2·MySQL에서 예약어이므로 컬럼명을 counter_date로 둡니다.
@Table(name = "daily_counters",
        uniqueConstraints = @UniqueConstraint(name = "uk_daily_player_date",
                columnNames = {"playerId", "counter_date"}))
public class DailyCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long playerId;

    @Column(name = "counter_date", nullable = false)
    private LocalDate day;

    @Column(nullable = false)
    private int packs;

    @Column(nullable = false)
    private int xp;

    @Column(nullable = false)
    private int regionContributionPoints;

    protected DailyCounter() {
    }

    public DailyCounter(Long playerId, LocalDate day) {
        this.playerId = playerId;
        this.day = day;
    }

    /**
     * 상한을 넘지 않는 선까지만 지급액을 잘라 반환하고, 잘린 만큼 카운터를 올립니다.
     *
     * @return 실제로 지급 가능한 양
     */
    public int grantXp(int requested, int cap) {
        int granted = Math.max(0, Math.min(requested, cap - xp));
        xp += granted;
        return granted;
    }

    public int grantContributionPoints(int requested, int cap) {
        int granted = Math.max(0, Math.min(requested, cap - regionContributionPoints));
        regionContributionPoints += granted;
        return granted;
    }

    /** 오늘 팩을 하나 더 받을 수 있는지. 상한에 걸리면 팩은 지급되지 않습니다. */
    public boolean grantPack(int cap) {
        if (packs >= cap) {
            return false;
        }
        packs++;
        return true;
    }

    public Long getId() {
        return id;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public LocalDate getDay() {
        return day;
    }

    public int getPacks() {
        return packs;
    }

    public int getXp() {
        return xp;
    }

    public int getRegionContributionPoints() {
        return regionContributionPoints;
    }
}
