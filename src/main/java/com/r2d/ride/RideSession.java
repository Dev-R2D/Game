package com.r2d.ride;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.r2d.common.R2dException;
import com.r2d.domain.RideMode;
import com.r2d.domain.RideStatus;
import com.r2d.player.Player;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 주행 세션.
 *
 * <p>{@code rideId}는 앱 로그, 센서 배치, 셀 관측, 기여, 보스 정산, 보상 원장을 하나로 잇는
 * 관측성의 축입니다. 화면 전환·백그라운드 전환·네트워크 단절이 있어도 같은 rideId로 이어집니다.
 */
@Entity
@Table(name = "ride_sessions", indexes = {
        @Index(name = "idx_ride_player", columnList = "player_id"),
        @Index(name = "idx_ride_status", columnList = "status")
})
public class RideSession {

    @Id
    @Column(length = 40)
    private String rideId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id")
    private Player player;

    /** 이 주행이 공략하기로 선택한 지역 보스. 선택하지 않은 자유 주행도 허용합니다. */
    @Column(length = 20)
    private String regionCode;

    private Long bossId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ride_deck_cards", joinColumns = @JoinColumn(name = "ride_id"))
    @Column(name = "card_code", length = 30)
    private List<String> deckCardCodes = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RideMode mode = RideMode.REAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RideStatus status = RideStatus.ACTIVE;

    /** 심사 재현 세션일 때 재생한 로그 ID. 실측/파생 구분을 원장에 남기기 위해 필수입니다. */
    @Column(length = 40)
    private String sourceLogId;

    @Column(nullable = false)
    private Instant startedAt = Instant.now();

    private Instant endedAt;

    /** 다음에 받아야 할 배치 순번. 중복·역순 배치를 걸러내는 기준입니다. */
    @Column(nullable = false)
    private int nextBatchSeq = 0;

    protected RideSession() {
    }

    public RideSession(Player player, String regionCode, Long bossId, List<String> deckCardCodes, RideMode mode) {
        this.rideId = UUID.randomUUID().toString();
        this.player = player;
        this.regionCode = regionCode;
        this.bossId = bossId;
        this.deckCardCodes = new ArrayList<>(deckCardCodes == null ? List.of() : deckCardCodes);
        this.mode = mode;
    }

    /** 센서 배치를 더 받을 수 있는 상태인지 확인합니다. */
    public void assertIngestible() {
        if (status != RideStatus.ACTIVE) {
            throw R2dException.conflict("RIDE_NOT_ACTIVE",
                    "이미 " + status.getLabel() + " 상태인 주행에는 데이터를 추가할 수 없습니다.");
        }
    }

    public void markBatchAccepted(int batchSeq) {
        this.nextBatchSeq = Math.max(this.nextBatchSeq, batchSeq + 1);
    }

    public void settle(Instant endedAt) {
        assertIngestible();
        this.status = RideStatus.SETTLED;
        this.endedAt = endedAt;
    }

    public void discard() {
        this.status = RideStatus.DISCARDED;
        this.endedAt = Instant.now();
    }

    public String getRideId() {
        return rideId;
    }

    public Player getPlayer() {
        return player;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public Long getBossId() {
        return bossId;
    }

    public List<String> getDeckCardCodes() {
        return deckCardCodes;
    }

    public RideMode getMode() {
        return mode;
    }

    public RideStatus getStatus() {
        return status;
    }

    public String getSourceLogId() {
        return sourceLogId;
    }

    public void setSourceLogId(String sourceLogId) {
        this.sourceLogId = sourceLogId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public int getNextBatchSeq() {
        return nextBatchSeq;
    }
}
