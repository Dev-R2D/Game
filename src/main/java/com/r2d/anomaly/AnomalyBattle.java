package com.r2d.anomaly;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 결함 후보(레드포인트) 하나를 게임 화면에서 "몬스터"로 배틀한 기록.
 *
 * <p>같은 플레이어가 같은 후보를 여러 번 배틀해도 보상은 한 번만 나가야 하므로,
 * (cellId, playerId) 조합에 유니크 제약을 둡니다. 재전송돼도 {@link AnomalyBattleService}가
 * 기존 기록을 그대로 돌려주므로(다른 정산 API들과 같은 방식) 중복 지급은 없습니다.
 */
@Entity
@Table(name = "anomaly_battles",
        uniqueConstraints = @UniqueConstraint(name = "uk_battle_cell_player", columnNames = {"cellId", "playerId"}),
        indexes = @Index(name = "idx_battle_player", columnList = "playerId"))
public class AnomalyBattle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String cellId;

    @Column(nullable = false)
    private Long playerId;

    @Column(nullable = false)
    private int xpGranted;

    @Column(nullable = false)
    private int coinsGranted;

    @Column(nullable = false)
    private int regionContributionPointsGranted;

    @Column(nullable = false)
    private Instant defeatedAt = Instant.now();

    protected AnomalyBattle() {
    }

    public AnomalyBattle(String cellId, Long playerId, int xpGranted, int coinsGranted,
                         int regionContributionPointsGranted) {
        this.cellId = cellId;
        this.playerId = playerId;
        this.xpGranted = xpGranted;
        this.coinsGranted = coinsGranted;
        this.regionContributionPointsGranted = regionContributionPointsGranted;
    }

    public Long getId() {
        return id;
    }

    public String getCellId() {
        return cellId;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public int getXpGranted() {
        return xpGranted;
    }

    public int getCoinsGranted() {
        return coinsGranted;
    }

    public int getRegionContributionPointsGranted() {
        return regionContributionPointsGranted;
    }

    public Instant getDefeatedAt() {
        return defeatedAt;
    }
}
