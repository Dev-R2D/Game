package com.r2d.boss;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * 지역 공동 보스.
 *
 * <p>실시간 멀티플레이가 아니라 비동기 누적입니다. 여러 이용자의 기여가 서버에 쌓이고,
 * 앱은 주기적 조회와 주행 종료 정산으로 상태를 갱신합니다.
 *
 * <p>HP는 활성 라이더 수로 나누지 않습니다. 라이더 수는 보스 등급과 목표 처치 기간을
 * 정하는 데만 쓰고, HP 자체는 그 지역에 실제로 필요한 데이터 양에서 나옵니다.
 */
@Entity
@Table(name = "bosses", indexes = @Index(name = "idx_boss_region", columnList = "regionCode"))
public class Boss {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String regionCode;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false)
    private double maxHp;

    @Column(nullable = false)
    private double currentHp;

    @Column(nullable = false)
    private int phaseCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BossStatus status = BossStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BossTier tier;

    /** 이 보스를 만들 때 계산한 지역 데이터 필요량. 왜 이 HP인지 설명하기 위해 남깁니다. */
    @Column(nullable = false)
    private double dataNeed;

    /**
     * 중간 보상이 이미 지급된 마지막 단계.
     *
     * <p>보스 HP는 여러 사람이 비동기로 깎으므로, "몇 단계까지 보상했는지"를 보스가 직접
     * 들고 있어야 같은 단계 보상이 두 번 나가지 않습니다.
     */
    @Column(nullable = false)
    private int rewardedPhase = 1;

    /** 처치 보상을 이미 만들었는지. 동시 정산에서 중복 지급을 막는 표시입니다. */
    @Column(nullable = false)
    private boolean defeatRewardsIssued = false;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant clearedAt;

    protected Boss() {
    }

    public Boss(String regionCode, String name, double maxHp, int phaseCount, BossTier tier, double dataNeed) {
        this.regionCode = regionCode;
        this.name = name;
        this.maxHp = maxHp;
        this.currentHp = maxHp;
        this.phaseCount = phaseCount;
        this.tier = tier;
        this.dataNeed = dataNeed;
    }

    /**
     * 확정 피해를 적용합니다. 호출 측에서 주행당 한 번만 부르도록 보장해야 합니다
     * ({@link BossContribution}의 유일 제약이 이중 안전장치입니다).
     *
     * @return 실제로 깎인 HP. 남은 HP보다 큰 피해는 잘립니다.
     */
    public double applyDamage(double damage) {
        if (status != BossStatus.ACTIVE || damage <= 0) {
            return 0;
        }
        double applied = Math.min(currentHp, damage);
        currentHp -= applied;
        if (currentHp <= 0) {
            currentHp = 0;
            status = BossStatus.CLEARED;
            clearedAt = Instant.now();
        }
        return applied;
    }

    /** 현재 페이즈(1부터 시작). 체력바를 여러 줄로 나눠 중간 성취를 보여주기 위한 값입니다. */
    public int currentPhase() {
        if (maxHp <= 0) {
            return 1;
        }
        double remainingRatio = currentHp / maxHp;
        int phase = (int) Math.ceil((1.0 - remainingRatio) * phaseCount);
        return Math.max(1, Math.min(phaseCount, phase == 0 ? 1 : phase));
    }

    public double progressRatio() {
        return maxHp <= 0 ? 0 : (maxHp - currentHp) / maxHp;
    }

    public boolean isCleared() {
        return status == BossStatus.CLEARED;
    }

    /**
     * 아직 보상하지 않은 새 단계로 넘어갔으면 그 단계 번호를, 아니면 0을 돌려주고 표시를 올립니다.
     *
     * <p>한 번의 정산이 두 단계를 한꺼번에 넘길 수도 있으므로 호출한 쪽에서 0이 나올 때까지
     * 반복해야 모든 단계의 중간 보상이 나갑니다.
     */
    public int claimNextRewardablePhase() {
        int phase = currentPhase();
        if (!isCleared() && phase > rewardedPhase) {
            rewardedPhase++;
            return rewardedPhase;
        }
        return 0;
    }

    /**
     * 처치 보상을 만들 권한을 한 번만 내줍니다.
     *
     * @return 이번 호출이 처치 보상을 만들어야 하면 true
     */
    public boolean claimDefeatRewardIssuance() {
        if (!isCleared() || defeatRewardsIssued) {
            return false;
        }
        defeatRewardsIssued = true;
        // 처치했으면 남은 단계 보상은 처치 보상이 대신합니다. 같은 성취로 두 번 주지 않습니다.
        rewardedPhase = phaseCount;
        return true;
    }

    public int getRewardedPhase() {
        return rewardedPhase;
    }

    public boolean isDefeatRewardsIssued() {
        return defeatRewardsIssued;
    }

    public Long getId() {
        return id;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public String getName() {
        return name;
    }

    public double getMaxHp() {
        return maxHp;
    }

    public double getCurrentHp() {
        return currentHp;
    }

    public int getPhaseCount() {
        return phaseCount;
    }

    public BossStatus getStatus() {
        return status;
    }

    public BossTier getTier() {
        return tier;
    }

    public double getDataNeed() {
        return dataNeed;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getClearedAt() {
        return clearedAt;
    }
}
