package com.r2d.api;

import java.time.Instant;

import com.r2d.auth.AuthService;
import com.r2d.auth.AuthTokens;
import com.r2d.auth.CurrentPlayer;
import com.r2d.player.Player;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 이메일 로그인 API.
 *
 * <p>가입하면 계정과 게임 플레이어가 함께 만들어집니다. 이후 게임 API는
 * {@code Authorization: Bearer <accessToken>} 헤더로 호출합니다.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public AuthTokens signUp(@Valid @RequestBody SignUpRequest request) {
        return authService.signUp(request.email(), request.password(), request.nickname());
    }

    @PostMapping("/login")
    public AuthTokens logIn(@Valid @RequestBody LogInRequest request) {
        return authService.logIn(request.email(), request.password());
    }

    /** 액세스 토큰이 만료됐을 때 리프레시 토큰으로 재발급받습니다. 리프레시 토큰도 함께 회전합니다. */
    @PostMapping("/refresh")
    public AuthTokens refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    /** 로그아웃. 이 기기의 리프레시 토큰만 폐기하고 다른 기기 세션은 유지합니다. */
    @PostMapping("/logout")
    public LogoutResponse logOut(@RequestBody(required = false) RefreshRequest request) {
        authService.logOut(request == null ? null : request.refreshToken());
        return new LogoutResponse(true, "로그아웃되었습니다. 액세스 토큰은 만료될 때까지 유효하니 앱에서도 지워 주세요.");
    }

    @PostMapping("/password")
    public LogoutResponse changePassword(@CurrentPlayer Player player,
                                         @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(player, request.currentPassword(), request.newPassword());
        return new LogoutResponse(true, "비밀번호를 변경했습니다. 보안을 위해 모든 기기에서 로그아웃되었습니다.");
    }

    /** 현재 로그인 상태 확인. 토큰이 살아 있는지 앱이 확인할 때 씁니다. */
    @GetMapping("/me")
    public SessionResponse me(@CurrentPlayer Player player) {
        return authService.accountOf(player)
                .map(account -> new SessionResponse(player.getPublicId(), player.getNickname(),
                        account.getEmail(), account.isEmailVerified(), account.getLastLoginAt()))
                // 계정 없이 만들어진 게스트 플레이어(레거시 POST /players)도 조회는 됩니다.
                .orElseGet(() -> new SessionResponse(player.getPublicId(), player.getNickname(),
                        null, false, null));
    }

    public record SignUpRequest(
            @NotBlank @Size(max = 254) String email,
            @NotBlank String password,
            @NotBlank @Size(max = 20) String nickname) {
    }

    public record LogInRequest(@NotBlank String email, @NotBlank String password) {
    }


    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
    }

    public record LogoutResponse(boolean ok, String message) {
    }

    /** 이메일은 본인에게만 돌려줍니다. 다른 이용자 조회 API에는 절대 포함되지 않습니다. */
    public record SessionResponse(String playerPublicId, String nickname, String email,
                                  boolean emailVerified, Instant lastLoginAt) {
    }
}
