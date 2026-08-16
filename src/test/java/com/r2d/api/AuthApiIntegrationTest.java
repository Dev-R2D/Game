package com.r2d.api;

import com.r2d.support.DatabaseCleaner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 이메일 로그인 통합 테스트. */
@SpringBootTest
@AutoConfigureMockMvc
class AuthApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @BeforeEach
    void setUp() {
        databaseCleaner.resetToSeedState();
    }

    @Test
    @DisplayName("가입하면 계정과 플레이어가 함께 만들어지고 바로 토큰이 발급된다")
    void signUpIssuesTokens() throws Exception {
        JsonNode tokens = signUp("rider@example.com", "verysecret123", "가입라이더");

        assertThat(tokens.get("accessToken").asString()).isNotBlank();
        assertThat(tokens.get("refreshToken").asString()).isNotBlank();
        assertThat(tokens.get("tokenType").asString()).isEqualTo("Bearer");
        assertThat(tokens.get("playerPublicId").asString()).isNotBlank();
        assertThat(tokens.get("nickname").asString()).isEqualTo("가입라이더");
    }

    @Test
    @DisplayName("발급받은 액세스 토큰으로 게임 API를 호출할 수 있다")
    void accessTokenWorksOnGameApi() throws Exception {
        String access = signUp("play@example.com", "verysecret123", "토큰라이더")
                .get("accessToken").asString();

        mockMvc.perform(get("/api/v1/players/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.player.nickname").value("토큰라이더"));

        mockMvc.perform(post("/api/v1/rides")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"regionCode\":\"DONGTAN2\",\"deckCardCodes\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rideId").isNotEmpty());
    }

    @Test
    @DisplayName("이메일이 없을 때와 비밀번호가 틀렸을 때 응답이 구분되지 않는다")
    void loginDoesNotLeakWhichEmailsExist() throws Exception {
        signUp("real@example.com", "verysecret123", "실제라이더");

        MvcResult wrongPassword = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"real@example.com\",\"password\":\"wrongpassword\"}"))
                .andExpect(status().isUnauthorized()).andReturn();

        MvcResult unknownEmail = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"wrongpassword\"}"))
                .andExpect(status().isUnauthorized()).andReturn();

        assertThat(read(unknownEmail).get("code").asString())
                .isEqualTo(read(wrongPassword).get("code").asString());
        assertThat(read(unknownEmail).get("message").asString())
                .isEqualTo(read(wrongPassword).get("message").asString());
    }

    @Test
    @DisplayName("이메일 대소문자가 달라도 같은 계정으로 취급한다")
    void emailIsCaseInsensitive() throws Exception {
        signUp("Mixed@Example.COM", "verysecret123", "대소문자");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"mixed@example.com\",\"password\":\"verysecret123\"}"))
                .andExpect(status().isOk());

        // 대소문자만 바꾼 중복 가입도 막혀야 합니다.
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"MIXED@example.com\",\"password\":\"verysecret123\",\"nickname\":\"다른닉\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    @DisplayName("리프레시 토큰은 한 번 쓰면 회전되고, 재사용하면 전체 세션이 끊긴다")
    void refreshTokenRotatesAndDetectsReuse() throws Exception {
        JsonNode first = signUp("rotate@example.com", "verysecret123", "회전라이더");
        String oldRefresh = first.get("refreshToken").asString();

        JsonNode second = read(mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
                .andExpect(status().isOk()).andReturn());

        String newRefresh = second.get("refreshToken").asString();
        assertThat(newRefresh).isNotEqualTo(oldRefresh);

        // 이미 쓴 토큰을 다시 내밀면 탈취 신호로 보고 전부 폐기합니다.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"));

        // 방금 회전된 토큰도 함께 죽습니다.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + newRefresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그아웃하면 그 리프레시 토큰만 폐기된다")
    void logoutRevokesOnlyThatToken() throws Exception {
        String refresh = signUp("out@example.com", "verysecret123", "로그아웃").get("refreshToken").asString();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("비밀번호를 바꾸면 모든 기기의 세션이 끊긴다")
    void changingPasswordRevokesAllSessions() throws Exception {
        JsonNode tokens = signUp("pw@example.com", "verysecret123", "비번변경");
        String access = tokens.get("accessToken").asString();
        String refresh = tokens.get("refreshToken").asString();

        mockMvc.perform(post("/api/v1/auth/password")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"verysecret123\",\"newPassword\":\"brandnewsecret\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"pw@example.com\",\"password\":\"brandnewsecret\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("비밀번호를 반복해서 틀리면 실패 횟수가 누적되어 계정이 잠긴다")
    void repeatedFailuresLockTheAccount() throws Exception {
        signUp("brute@example.com", "verysecret123", "무차별대입");

        // 실패 기록이 롤백되면 잠금이 영원히 걸리지 않습니다. 10회 후 잠겨야 합니다.
        String wrong = "{\"email\":\"brute@example.com\",\"password\":\"wrongwrongwrong\"}";
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON).content(wrong))
                    .andExpect(status().isUnauthorized());
        }

        // 잠긴 뒤에는 올바른 비밀번호로도 들어갈 수 없습니다.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"brute@example.com\",\"password\":\"verysecret123\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    @DisplayName("짧은 비밀번호와 잘못된 이메일 형식은 가입 단계에서 막힌다")
    void signUpValidatesInput() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ok@example.com\",\"password\":\"short\",\"nickname\":\"짧은비번\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"verysecret123\",\"nickname\":\"이메일오류\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EMAIL"));
    }

    @Test
    @DisplayName("위조되거나 없는 토큰으로는 게임 API를 호출할 수 없다")
    void invalidTokenIsRejected() throws Exception {
        String access = signUp("tamper@example.com", "verysecret123", "위조검증")
                .get("accessToken").asString();

        // 서명 부분만 바꾼 토큰
        String tampered = access.substring(0, access.lastIndexOf('.') + 1) + "AAAAAAAAAAAAAAAAAAAAAA";
        mockMvc.perform(get("/api/v1/players/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/players/me").header("Authorization", "Bearer not.a.token"))
                .andExpect(status().isUnauthorized());

        // 헤더가 아예 없으면 401
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("이메일은 본인 조회에만 나오고 타인 공개 프로필에는 포함되지 않는다")
    void emailNeverLeaksToOtherPlayers() throws Exception {
        JsonNode tokens = signUp("private@example.com", "verysecret123", "비공개라이더");
        String access = tokens.get("accessToken").asString();
        String publicId = tokens.get("playerPublicId").asString();

        MvcResult mine = mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andReturn();
        assertThat(read(mine).get("email").asString()).isEqualTo("private@example.com");

        MvcResult theirs = mockMvc.perform(get("/api/v1/players/{id}", publicId))
                .andExpect(status().isOk()).andReturn();
        assertThat(read(theirs).has("email")).isFalse();
        assertThat(theirs.getResponse().getContentAsString()).doesNotContain("private@example.com");
    }

    @Test
    @DisplayName("이메일 로그인만 제공한다 — 소셜 로그인 경로는 존재하지 않는다")
    void onlyEmailLoginIsExposed() throws Exception {
        // 없는 경로는 500이 아니라 404여야 합니다. 500이면 앱이 서버 장애로 오인해 재시도합니다.
        mockMvc.perform(post("/api/v1/auth/apple")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityToken\":\"whatever\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENDPOINT_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/definitely-not-a-route"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENDPOINT_NOT_FOUND"));
    }

    private JsonNode signUp(String email, String password, String nickname) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password
                                + "\",\"nickname\":\"" + nickname + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return read(result);
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
