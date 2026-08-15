package com.r2d.boss;

import java.time.Instant;

import com.r2d.common.R2dException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 보스 보상 한 건.
 *
 * <p><b>왜 즉시 지급하지 않고 수령 대기로 두는가.</b> 보스를 쓰러뜨린 순간 요청을 보내고 있는
 * 사람은 마지막 기여자 한 명뿐입니다. 나머지 참여자는 접속해 있지도 않습니다. 그래서 처치
 * 시점에는 각자의 몫을 <b>계산해서 남겨 두고</b>, 이용자가 앱을 열었을 때 수령합니다.
 * 문서의 "확정 알림 → 보상 수령" 흐름과도 같습니다.
 *
 * <p>(bossId, playerId, kind, phase)에 유일 제약이 걸려 있어 같은 보상이 두 번 만들어지지 않습니다.
 */
@Entity
@Table(name = "boss_rewards",
        uniqueConstraints = @UniqueConstraint(name = "uk_boss_reward",
                columnNames = {"bossId", "playerId", "kind", "phase"}),
        indexes = {
                @Index(name = "idx_boss_reward_player", columnList = "playerId, claimed"),
                @Index(name = "idx_boss_reward_boss", columnList = "bossId")
        })
public class BossReward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long bossId;

    @Column(nullable = false)
    private Long playerId;

    @Column(nullable = false, length = 20)
    private String regionCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BossRewardKind kind;

    /** 중간 보상의 단계 번호. 처치 보상은 0입니다. */
    @Column(nullable = false)
    private int phase;

    /**
     * 이 보스에 대한 내 기여 비율(0~1).
     *
     * <p>막타 여부가 아니라 이 값이 보상 크기를 정합니다. 마지막 일격을 넣은 사람이라도
     * 참여가 적었다면 적게 받습니다.
     */
    @Column(nullable = false)
    private double contributionRatio;

    @Column(nullable = false)
    private double damageContributed;

    @Column(nullable = false)
    private int contributedCells;

    @Column(nullable = false)
    private int xp;

    @Column(nullable = false)
    private int coins;

    @Column(nullable = false)
    private int regionContributionPoints;

    @Column(nullable = false)
    private boolean claimed = false;

    @Column(nullable = false, length = 160)
    private String reason;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant claimedAt;

    protected BossReward() {
    }

    public BossReward(Long bossId, Long playerId, String regionCode, BossRewardKind kind, int phase,
                      double contributionRatio, double damageContributed, int contributedCells,
                      int xp, int coins, int regionContributionPoints, String reason) {
        this.bossId = bossId;
        this.playerId = playerId;
        this.regionCode = regionCode;
        this.kind = kind;
        this.phase = phase;
        this.contributionRatio = contributionRatio;
        this.damageContributed = damageContributed;
        this.contributedCells = contributedCells;
        this.xp = xp;
        this.coins = coins;
        this.regionContributionPoints = regionContributionPoints;
        this.reason = reason;
    }

    /** 수령 처리. 두 번 수령하면 오류입니다. */
    public void claim(Instant now) {
        if (claimed) {
            throw R2dException.conflict("BOSS_REWARD_ALREADY_CLAIMED", "이미 수령한 보상입니다.");
        }
        this.claimed = true;
        this.claimedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getBossId() {
        return bossId;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public BossRewardKind getKind() {
        return kind;
    }

    public int getPhase() {
        return phase;
    }

    public double getContributionRatio() {
        return contributionRatio;
    }

    public double getDamageContributed() {
        return damageContributed;
    }

    public int getContributedCells() {
        return contributedCells;
    }

    public int getXp() {
        return xp;
    }

    public int getCoins() {
        return coins;
    }

    public int getRegionContributionPoints() {
        return regionContributionPoints;
    }

    public boolean isClaimed() {
        return claimed;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }
}
