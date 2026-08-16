package com.r2d.player;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 플레이어.
 *
 * <p>개인정보 최소화 원칙에 따라 정산·기여 계산은 내부 가명 식별자({@code publicId})로만 하고,
 * 공개 화면에는 이용자가 동의한 게임 닉네임만 노출합니다. 닉네임 공개 여부는 언제든 되돌릴 수 있습니다.
 */
@Entity
@Table(name = "players")
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 외부에 노출되는 가명 식별자. 기기 식별자나 계정 정보와 연결하지 않습니다. */
    @Column(nullable = false, unique = true, length = 40)
    private String publicId;

    @Column(nullable = false, length = 20)
    private String nickname;

    /** 최초 발견 기록 등에 닉네임을 표시해도 되는지에 대한 동의. 기본값은 비공개입니다. */
    @Column(nullable = false)
    private boolean nicknamePublic = false;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Player() {
    }

    public Player(String nickname) {
        this.publicId = UUID.randomUUID().toString();
        this.nickname = nickname;
    }

    /** 공개 화면에 쓸 표시 이름. 동의하지 않았으면 익명 라이더로 내려갑니다. */
    public String displayName() {
        return nicknamePublic ? nickname : "익명 라이더";
    }

    public Long getId() {
        return id;
    }

    public String getPublicId() {
        return publicId;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public boolean isNicknamePublic() {
        return nicknamePublic;
    }

    public void setNicknamePublic(boolean nicknamePublic) {
        this.nicknamePublic = nicknamePublic;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
