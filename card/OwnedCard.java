package com.r2d.card;

import java.time.Instant;

import com.r2d.player.Player;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 플레이어가 보유한 카드.
 *
 * <p>기념 카드는 이상 후보를 처음 발견한 시점에 "잠금 상태"로 먼저 지급되고, 교차검증으로
 * 확정 전이가 일어나면 해금됩니다. 발견 즉시 확정 보상을 주지 않는 이유는 단발 관측만으로
 * 결함을 단정하지 않기 위해서입니다.
 */
@Entity
// 기념 카드는 발견한 셀마다 따로 남아야 하므로 유일 조건에 셀을 포함합니다.
// 팩에서 나온 일반 카드는 sourceCellId가 비어 있어 플레이어당 한 장으로 묶입니다.
@Table(name = "owned_cards",
        uniqueConstraints = @UniqueConstraint(columnNames = {"player_id", "card_code", "sourceCellId"}))
public class OwnedCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id")
    private Player player;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_code")
    private Card card;

    @Column(nullable = false)
    private boolean locked;

    /** 이 카드를 얻게 한 셀. 기념 카드의 "어디서 발견했는지" 기록입니다. */
    @Column(length = 32)
    private String sourceCellId;

    @Column(nullable = false)
    private Instant acquiredAt = Instant.now();

    private Instant unlockedAt;

    protected OwnedCard() {
    }

    public OwnedCard(Player player, Card card, boolean locked, String sourceCellId) {
        this.player = player;
        this.card = card;
        this.locked = locked;
        this.sourceCellId = sourceCellId;
        if (!locked) {
            this.unlockedAt = Instant.now();
        }
    }

    public void unlock() {
        if (locked) {
            this.locked = false;
            this.unlockedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Player getPlayer() {
        return player;
    }

    public Card getCard() {
        return card;
    }

    public boolean isLocked() {
        return locked;
    }

    public String getSourceCellId() {
        return sourceCellId;
    }

    public Instant getAcquiredAt() {
        return acquiredAt;
    }

    public Instant getUnlockedAt() {
        return unlockedAt;
    }
}
