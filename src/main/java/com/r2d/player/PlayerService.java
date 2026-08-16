package com.r2d.player;

import com.r2d.common.R2dException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 플레이어 등록과 조회. */
@Service
public class PlayerService {

    private final PlayerRepository playerRepository;

    public PlayerService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    @Transactional
    public Player register(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            throw R2dException.badRequest("NICKNAME_REQUIRED", "닉네임을 입력해 주세요.");
        }
        String trimmed = nickname.trim();
        if (playerRepository.existsByNickname(trimmed)) {
            throw R2dException.conflict("NICKNAME_TAKEN", "이미 사용 중인 닉네임입니다: " + trimmed);
        }
        return playerRepository.save(new Player(trimmed));
    }

    public Player require(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            throw R2dException.badRequest("PLAYER_ID_REQUIRED",
                    "X-Player-Id 헤더에 플레이어 식별자가 필요합니다.");
        }
        return playerRepository.findByPublicId(publicId)
                .orElseThrow(() -> R2dException.notFound("PLAYER_NOT_FOUND",
                        "플레이어를 찾을 수 없습니다: " + publicId));
    }

    /**
     * 닉네임 공개 여부를 바꿉니다.
     *
     * <p>최초 발견 기록 같은 공개 화면에 닉네임을 쓸지 이용자가 직접 정하고, 언제든 비공개로
     * 되돌릴 수 있어야 하므로 별도 API로 둡니다.
     */
    @Transactional
    public Player setNicknameVisibility(String publicId, boolean visible) {
        Player player = require(publicId);
        player.setNicknamePublic(visible);
        return player;
    }
}
