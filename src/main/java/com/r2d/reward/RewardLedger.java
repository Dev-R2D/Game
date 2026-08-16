package com.r2d.reward;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 게임 재화 원장.
 *
 * <p>여기 있는 값은 캐릭터·카드·외형 등 게임 내부 성장에만 쓰입니다. 현실 혜택으로 넘어가는
 * 통로는 없으며, 지역기여 점수와는 {@link RegionContributionLedger}로 완전히 분리돼 있습니다.
 */
@Entity
@Table(name = "reward_ledgers",
        uniqueConstraints = @UniqueConstraint(name = "uk_reward_player", columnNames = {"playerId"}))
public class RewardLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long playerId;

    @Column(nullable = false)
    private long xp;

    @Column(nullable = false)
    private long coins;

    protected RewardLedger() {
    }

    public RewardLedger(Long playerId) {
        this.playerId = playerId;
    }

    public void add(long xpGain, long coinGain) {
        this.xp += Math.max(0, xpGain);
        this.coins += Math.max(0, coinGain);
    }

    /** 누적 XP 기반 레벨. 단순 구간 환산이며 서버가 유일한 권위입니다. */
    public int level() {
        return (int) (1 + Math.floor(Math.sqrt(xp / 100.0)));
    }

    public Long getId() {
        return id;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public long getXp() {
        return xp;
    }

    public long getCoins() {
        return coins;
    }
}
