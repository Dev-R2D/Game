package com.r2d.api;

import java.util.List;

import com.r2d.support.DatabaseCleaner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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

/**
 * HTTP 계약 통합 테스트.
 *
 * <p>앱이 실제로 밟는 순서를 그대로 따라갑니다:
 * 등록 → 출발 → 배치 업로드(재전송 포함) → 종료 정산 → 팩 개봉.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RideApiIntegrationTest {

    private static final String REGION = "DONGTAN2";
    private static final long START_MS = 1_800_000_000_000L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    private String playerId;

    @BeforeEach
    void setUp() throws Exception {
        databaseCleaner.resetToSeedState();
        playerId = registerPlayer("테스트라이더");
    }

    @Test
    @DisplayName("등록 → 출발 → 업로드 → 정산까지 핵심 루프가 끝까지 동작한다")
    void coreLoopWorksEndToEnd() throws Exception {
        String rideId = startRide(List.of("TB_SCOUT_1", "TB_PATH_3", "TB_FRONTIER_5"));

        uploadBatch(rideId, 0, "batch-0", 0);
        uploadBatch(rideId, 1, "batch-1", 60);

        MvcResult finish = mockMvc.perform(post("/api/v1/rides/{id}/finish", rideId)
                        .header("X-Player-Id", playerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientEstimatedDamage\": 1234.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transport.mode").value("자전거"))
                .andExpect(jsonPath("$.damage.formula").exists())
                .andReturn();

        JsonNode settlement = read(finish);
        assertThat(settlement.get("damage").get("finalDamage").asDouble()).isPositive();
        assertThat(settlement.get("damage").get("clientEstimate").asDouble()).isEqualTo(1234.0);
        assertThat(settlement.get("contribution").get("newCells").asInt()).isPositive();
        assertThat(settlement.get("cellLines")).isNotEmpty();
    }

    @Test
    @DisplayName("같은 배치를 다시 보내도 오류가 아니라 '이미 반영됨'으로 응답한다")
    void duplicateBatchIsAcceptedAsAlreadyApplied() throws Exception {
        String rideId = startRide(List.of());

        assertThat(uploadBatch(rideId, 0, "same-key", 0).get("accepted").asBoolean()).isTrue();

        JsonNode retry = uploadBatch(rideId, 0, "same-key", 0);
        assertThat(retry.get("accepted").asBoolean()).isFalse();
        assertThat(retry.get("message").asString()).contains("이미 반영된 배치");

        // batchSeq만 같고 키가 다른 경우도 중복으로 걸러야 합니다.
        JsonNode sameSeq = uploadBatch(rideId, 0, "other-key", 0);
        assertThat(sameSeq.get("accepted").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("정산을 두 번 요청해도 같은 결과가 돌아오고 보상이 두 배가 되지 않는다")
    void finishingTwiceReturnsSameSettlement() throws Exception {
        String rideId = startRide(List.of());
        uploadBatch(rideId, 0, "batch-0", 0);

        JsonNode first = read(mockMvc.perform(post("/api/v1/rides/{id}/finish", rideId)
                .header("X-Player-Id", playerId)).andExpect(status().isOk()).andReturn());
        JsonNode second = read(mockMvc.perform(post("/api/v1/rides/{id}/finish", rideId)
                .header("X-Player-Id", playerId)).andExpect(status().isOk()).andReturn());

        assertThat(second.get("damage").get("finalDamage").asDouble())
                .isEqualTo(first.get("damage").get("finalDamage").asDouble());
        assertThat(second.get("reward").get("xp").asInt())
                .isEqualTo(first.get("reward").get("xp").asInt());
    }

    @Test
    @DisplayName("정산이 끝나야 팩이 생기고, 개봉 전에는 내용물을 내려보내지 않는다")
    void packAppearsOnlyAfterSettlement() throws Exception {
        String rideId = startRide(List.of());
        uploadBatch(rideId, 0, "batch-0", 0);

        mockMvc.perform(get("/api/v1/packs/pending").header("X-Player-Id", playerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(post("/api/v1/rides/{id}/finish", rideId).header("X-Player-Id", playerId))
                .andExpect(status().isOk());

        MvcResult pending = mockMvc.perform(get("/api/v1/packs/pending").header("X-Player-Id", playerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].opened").value(false))
                .andExpect(jsonPath("$[0].cardCodes").isEmpty())
                .andReturn();

        long packId = read(pending).get(0).get("id").asLong();

        mockMvc.perform(post("/api/v1/packs/{id}/open", packId).header("X-Player-Id", playerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.opened").value(true))
                .andExpect(jsonPath("$.cardCodes").isNotEmpty())
                .andExpect(jsonPath("$.gradeReason").isNotEmpty());

        // 같은 팩을 두 번 개봉할 수 없습니다.
        mockMvc.perform(post("/api/v1/packs/{id}/open", packId).header("X-Player-Id", playerId))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("정산이 끝난 주행에는 배치를 더 올릴 수 없다")
    void settledRideRejectsMoreData() throws Exception {
        String rideId = startRide(List.of());
        uploadBatch(rideId, 0, "batch-0", 0);
        mockMvc.perform(post("/api/v1/rides/{id}/finish", rideId).header("X-Player-Id", playerId))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/rides/{id}/batches", rideId)
                        .header("X-Player-Id", playerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchJson(2, "batch-2", 0)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("다른 사람의 주행은 조회할 수 없다")
    void cannotAccessAnotherPlayersRide() throws Exception {
        String rideId = startRide(List.of());
        String otherPlayer = registerPlayer("남의라이더");

        mockMvc.perform(get("/api/v1/rides/{id}", rideId).header("X-Player-Id", otherPlayer))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("지도 조회는 범위 제한을 강제해 전량 조회를 막는다")
    void mapQueryRejectsOversizedBounds() throws Exception {
        mockMvc.perform(get("/api/v1/map/cells")
                        .param("minLat", "30.0").param("maxLat", "40.0")
                        .param("minLon", "120.0").param("maxLon", "130.0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BOUNDS_TOO_LARGE"));
    }

    @Test
    @DisplayName("팩을 개봉한 뒤 보유 카드 목록을 조회할 수 있다")
    void ownedCardsAreReadableAfterOpeningPack() throws Exception {
        String rideId = startRide(List.of());
        uploadBatch(rideId, 0, "batch-0", 0);
        mockMvc.perform(post("/api/v1/rides/{id}/finish", rideId).header("X-Player-Id", playerId))
                .andExpect(status().isOk());

        MvcResult pending = mockMvc.perform(get("/api/v1/packs/pending").header("X-Player-Id", playerId))
                .andExpect(status().isOk()).andReturn();
        long packId = read(pending).get(0).get("id").asLong();
        mockMvc.perform(post("/api/v1/packs/{id}/open", packId).header("X-Player-Id", playerId))
                .andExpect(status().isOk());

        // 카드 정보는 지연 로딩 대상이므로 조회 시점에 함께 읽어 와야 합니다.
        mockMvc.perform(get("/api/v1/players/me/cards").header("X-Player-Id", playerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cardCode").isNotEmpty())
                .andExpect(jsonPath("$[0].name").isNotEmpty())
                .andExpect(jsonPath("$[0].line").isNotEmpty());
    }

    @Test
    @DisplayName("첫 실행에도 오늘의 미션 화면이 비어 있지 않다")
    void missionsAreNeverEmptyOnFirstRun() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/regions/{code}/missions", REGION))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode missions = read(result);
        assertThat(missions).isNotEmpty();
        // 공동 보스전은 상시 카드여야 합니다.
        assertThat(missions).anySatisfy(m -> assertThat(m.get("type").asString()).isEqualTo("BOSS"));
        // 데이터 수요가 남아 있으면 탐사·갱신 미션도 함께 떠야 합니다.
        assertThat(missions.size()).isGreaterThan(1);
        assertThat(missions).allSatisfy(m -> assertThat(m.get("safetyNote").asString()).isNotBlank());
    }

    @Test
    @DisplayName("인증 정보가 없으면 401로 응답한다")
    void missingCredentialsIsUnauthorized() throws Exception {
        // 인증 도입 전에는 헤더 누락이 400이었지만, 이제는 401이 올바른 의미입니다.
        mockMvc.perform(post("/api/v1/rides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"regionCode\": \"" + REGION + "\", \"deckCardCodes\": []}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("팩 확률 구성표는 로그인 없이도 확인할 수 있다")
    void packOddsArePublished() throws Exception {
        mockMvc.perform(get("/api/v1/packs/odds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.등급별구성").exists())
                .andExpect(jsonPath("$.등급결정방식").exists());
    }

    private String registerPlayer(String nickname) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\": \"" + nickname + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return read(result).get("publicId").asString();
    }

    private String startRide(List<String> deck) throws Exception {
        String deckJson = deck.stream()
                .map(code -> "\"" + code + "\"")
                .reduce((a, b) -> a + "," + b)
                .map(joined -> "[" + joined + "]")
                .orElse("[]");

        MvcResult result = mockMvc.perform(post("/api/v1/rides")
                        .header("X-Player-Id", playerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"regionCode\": \"" + REGION + "\", \"deckCardCodes\": " + deckJson + "}"))
                .andExpect(status().isOk())
                .andReturn();
        return read(result).get("rideId").asString();
    }

    private JsonNode uploadBatch(String rideId, int seq, String key, int startIndex) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/rides/{id}/batches", rideId)
                        .header("X-Player-Id", playerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchJson(seq, key, startIndex)))
                .andExpect(status().isOk())
                .andReturn();
        return read(result);
    }

    /** 북쪽으로 약 5m/s로 이동하는 60초짜리 표본 묶음. */
    private String batchJson(int seq, String key, int startIndex) {
        StringBuilder points = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            int index = startIndex + i;
            if (i > 0) {
                points.append(',');
            }
            points.append(String.format(
                    "{\"epochMs\":%d,\"lat\":%.6f,\"lon\":127.100000,\"accuracyM\":8.0,\"speedMps\":5.0}",
                    START_MS + index * 1000L, 37.200000 + index * 0.000045));
        }
        return "{\"batchSeq\":" + seq + ",\"idempotencyKey\":\"" + key + "\",\"points\":["
                + points + "],\"imuWindows\":[]}";
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
