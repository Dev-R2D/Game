package com.r2d.reward;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.r2d.common.R2dException;
import com.r2d.domain.PackGrade;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 탐사 팩.
 *
 * <p>주행이 끝나면 등급이 확정된 팩이 생성되고, 개봉은 도착 후 사용자가 직접 탭할 때
 * 일어납니다. 주행 중에는 개봉이 잠깁니다.
 *
 * <p>등급은 이미 정산 시점에 결정돼 있습니다. 개봉은 연출일 뿐 결과를 바꾸지 않습니다.
 */
@Entity
@Table(name = "exploration_packs",
        uniqueConstraints = @UniqueConstraint(name = "uk_pack_ride", columnNames = {"rideId"}))
public class ExplorationPack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long playerId;

    @Column(nullable = false, length = 40)
    private String rideId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PackGrade grade;

    /** 등급 하한을 올린 근거. 팩 등급이 순수 확률이 아님을 사용자에게 그대로 보여줍니다. */
    @Column(nullable = false, length = 200)
    private String gradeReason;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "exploration_pack_items", joinColumns = @JoinColumn(name = "pack_id"))
    @Column(name = "card_code", length = 30)
    private List<String> cardCodes = new ArrayList<>();

    @Column(nullable = false)
    private int coins;

    @Column(nullable = false)
    private boolean opened;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant openedAt;

    protected ExplorationPack() {
    }

    public ExplorationPack(Long playerId, String rideId, PackGrade grade, String gradeReason,
                           List<String> cardCodes, int coins) {
        this.playerId = playerId;
        this.rideId = rideId;
        this.grade = grade;
        this.gradeReason = gradeReason;
        this.cardCodes = new ArrayList<>(cardCodes);
        this.coins = coins;
    }

    /** 팩을 개봉합니다. 두 번 개봉하면 오류입니다(중복 보상 방지). */
    public void open() {
        if (opened) {
            throw R2dException.conflict("PACK_ALREADY_OPENED", "이미 개봉한 팩입니다.");
        }
        this.opened = true;
        this.openedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public String getRideId() {
        return rideId;
    }

    public PackGrade getGrade() {
        return grade;
    }

    public String getGradeReason() {
        return gradeReason;
    }

    public List<String> getCardCodes() {
        return cardCodes;
    }

    public int getCoins() {
        return coins;
    }

    public boolean isOpened() {
        return opened;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }
}
