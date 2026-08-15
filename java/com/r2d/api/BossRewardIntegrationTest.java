package com.r2d.api;

import java.util.List;

import com.r2d.boss.Boss;
import com.r2d.boss.BossReward;
import com.r2d.boss.BossRewardKind;
import com.r2d.boss.BossRewardRepository;
import com.r2d.boss.BossService;
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

/**
 * 보스 처치 보상 통합 테스트.
 *
 * <p>보스 HP를 작게 낮춰(테스트 프로퍼티) 한 세션 안에서 실제로 처치까지 도달시킵니다.
 * 가장 중요한 검증은 <b>막타를 넣은 사람이 더 받지 않는다</b>는 것입니다.
 */
@SpringBootTest(properties = {
        "r2d.boss.min-hp=5000",
        "r2d.boss.hp-per-need-unit=1"
})
@AutoConfigureMockMvc
class BossRewardIntegrationTest {

    private static final String REGION = "DONGTAN2";
    private static final long START_MS = 1_900_000_000_000L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private BossRewardRepository rewardRepository;

    @Autowired
    private BossService bossService;

    @Autowired
    private com.r2d.player.PlayerRepository playerRepository;

    @BeforeEach
    void setUp() {
        databaseCleaner.resetToSeedState();
    }

    @Test
    @DisplayName("보스를 쓰러뜨리면 막타가 아니라 참여자 전원이 기여도만큼 받는다")
    void defeatRewardsGoToEveryParticipantByContribution() throws Exception {
        // 큰 기여자: 8배치(약 2.4km)
        String heavy = register("큰기여자");
        rideAndSettle(heavy, 37.2400, 127.1400, 8);

        Boss boss = bossService.activeBossOrCreate(REGION);
        assertThat(boss.getCurrentHp()).isGreaterThan(0);   // 아직 살아 있어야 합니다

        // 작은 기여자가 마지막 일격을 넣습니다: 3배치(약 0.9km)
        String finisher = register("막타라이더");
        rideAndSettle(finisher, 37.2500, 127.1500, 3);

        assertThat(bossService.get(boss.getId()).isCleared()).isTrue();

        List<BossReward> defeat = rewardRepository.findByBossId(boss.getId()).stream()
                .filter(r -> r.getKind() == BossRewardKind.BOSS_DEFEAT)
                .toList();

        // 참여자 두 명 모두 보상을 받아야 합니다. 막타 한 명만 받으면 안 됩니다.
        assertThat(defeat).hasSize(2);

        BossReward heavyReward = rewardOf(defeat, heavy);
        BossReward finisherReward = rewardOf(defeat, finisher);

        // 핵심: 막타를 넣은 쪽이 아니라 많이 기여한 쪽이 더 받습니다.
        assertThat(heavyReward.getXp()).isGreaterThan(finisherReward.getXp());
        assertThat(heavyReward.getContributionRatio()).isGreaterThan(finisherReward.getContributionRatio());
        assertThat(heavyReward.getContributionRatio() + finisherReward.getContributionRatio())
                .isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.001));

        // 기여가 적어도 0을 받지는 않습니다.
        assertThat(finisherReward.getXp()).isGreaterThanOrEqualTo(10);
    }

    @Test
    @DisplayName("보상은 수령해야 원장에 반영되고, 두 번 수령할 수 없다")
    void rewardMustBeClaimedAndOnlyOnce() throws Exception {
        String player = register("수령라이더");
        rideAndSettle(player, 37.2400, 127.1400, 8);
        rideAndSettle(player, 37.2600, 127.1600, 8);   // 혼자 처치

        MvcResult pendingResult = mockMvc.perform(get("/api/v1/boss-rewards/pending")
                        .header("X-Player-Id", player))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode pending = read(pendingResult);
        assertThat(pending).isNotEmpty();

        long xpBefore = profileXp(player);
        JsonNode reward = pending.get(0);
        long rewardId = reward.get("id").asLong();
        int rewardXp = reward.get("xp").asInt();

        mockMvc.perform(post("/api/v1/boss-rewards/{id}/claim", rewardId).header("X-Player-Id", player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimed").value(true));

        assertThat(profileXp(player)).isEqualTo(xpBefore + rewardXp);

        // 두 번째 수령은 거부
        mockMvc.perform(post("/api/v1/boss-rewards/{id}/claim", rewardId).header("X-Player-Id", player))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOSS_REWARD_ALREADY_CLAIMED"));
    }

    @Test
    @DisplayName("남의 보상은 수령할 수 없다")
    void cannotClaimAnotherPlayersReward() throws Exception {
        String owner = register("보상주인");
        rideAndSettle(owner, 37.2400, 127.1400, 8);
        rideAndSettle(owner, 37.2600, 127.1600, 8);

        JsonNode pending = read(mockMvc.perform(get("/api/v1/boss-rewards/pending")
                .header("X-Player-Id", owner)).andReturn());
        long rewardId = pending.get(0).get("id").asLong();

        String stranger = register("남의라이더");
        mockMvc.perform(post("/api/v1/boss-rewards/{id}/claim", rewardId).header("X-Player-Id", stranger))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOSS_REWARD_NOT_FOUND"));
    }

    @Test
    @DisplayName("같은 주행을 다시 정산해도 보상이 늘어나지 않는다")
    void resettlingDoesNotDuplicateRewards() throws Exception {
        String player = register("멱등라이더");
        rideAndSettle(player, 37.2400, 127.1400, 8);
        String rideId = rideAndSettle(player, 37.2600, 127.1600, 8);

        int countAfterFirst = rewardRepository.findByPlayerIdOrderByCreatedAtDesc(playerIdOf(player)).size();

        mockMvc.perform(post("/api/v1/rides/{id}/finish", rideId).header("X-Player-Id", player))
                .andExpect(status().isOk());

        assertThat(rewardRepository.findByPlayerIdOrderByCreatedAtDesc(playerIdOf(player)))
                .hasSize(countAfterFirst);
    }

    @Test
    @DisplayName("보스를 쓰러뜨리기 전에도 단계를 넘기면 중간 보상이 나온다")
    void phaseRewardsArriveBeforeDefeat() throws Exception {
        String player = register("중간보상");
        rideAndSettle(player, 37.2400, 127.1400, 6);

        Boss boss = bossService.activeBossOrCreate(REGION);
        assertThat(boss.isCleared()).as("아직 처치 전이어야 합니다").isFalse();

        List<BossReward> phase = rewardRepository.findByBossId(boss.getId()).stream()
                .filter(r -> r.getKind() == BossRewardKind.PHASE_CLEAR)
                .toList();

        assertThat(phase).isNotEmpty();
        assertThat(phase).allSatisfy(r -> {
            assertThat(r.getPhase()).isGreaterThan(1);
            assertThat(r.getXp()).isPositive();
            assertThat(r.getReason()).contains("단계 돌파");
        });
    }

    @Test
    @DisplayName("처치된 보스는 bossId로 다시 조회해 결과 화면을 그릴 수 있다")
    void defeatedBossRemainsQueryableById() throws Exception {
        String player = register("처치화면");
        rideAndSettle(player, 37.2400, 127.1400, 8);
        String rideId = rideAndSettle(player, 37.2600, 127.1600, 8);

        JsonNode settlement = read(mockMvc.perform(
                get("/api/v1/rides/{id}/settlement", rideId).header("X-Player-Id", player))
                .andExpect(status().isOk()).andReturn());
        long bossId = settlement.get("bossId").asLong();

        // 지역 조회는 이미 다음 보스를 돌려주므로, 방금 쓰러뜨린 보스는 id로 찾아야 합니다.
        mockMvc.perform(get("/api/v1/regions/{code}/boss", REGION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("진행 중"));

        mockMvc.perform(get("/api/v1/bosses/{id}", bossId).header("X-Player-Id", player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cleared").value(true))
                .andExpect(jsonPath("$.status").value("처치 완료"))
                .andExpect(jsonPath("$.myContributionRatio").value(1.0))
                .andExpect(jsonPath("$.myRewards").isNotEmpty());
    }

    @Test
    @DisplayName("한 번에 모두 수령할 수 있다")
    void claimAllWorks() throws Exception {
        String player = register("일괄수령");
        rideAndSettle(player, 37.2400, 127.1400, 8);
        rideAndSettle(player, 37.2600, 127.1600, 8);

        long xpBefore = profileXp(player);

        MvcResult result = mockMvc.perform(post("/api/v1/boss-rewards/claim-all")
                        .header("X-Player-Id", player))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = read(result);

        assertThat(body.get("count").asInt()).isPositive();
        assertThat(profileXp(player)).isEqualTo(xpBefore + body.get("totalXp").asInt());

        // 이제 남은 대기 보상이 없어야 합니다.
        mockMvc.perform(get("/api/v1/boss-rewards/pending").header("X-Player-Id", player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── 도우미 ────────────────────────────────────────────────

    private String register(String nickname) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + nickname + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return read(result).get("publicId").asString();
    }

    /** 주행을 시작해 배치를 올리고 정산까지 마칩니다. */
    private String rideAndSettle(String playerId, double startLat, double lon, int batches) throws Exception {
        MvcResult start = mockMvc.perform(post("/api/v1/rides")
                        .header("X-Player-Id", playerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"regionCode\":\"" + REGION + "\",\"deckCardCodes\":[]}"))
                .andExpect(status().isOk()).andReturn();
        String rideId = read(start).get("rideId").asString();

        for (int b = 0; b < batches; b++) {
            mockMvc.perform(post("/api/v1/rides/{id}/batches", rideId)
                            .header("X-Player-Id", playerId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(batchJson(b, rideId + "-" + b, b * 60, startLat, lon)))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/v1/rides/{id}/finish", rideId).header("X-Player-Id", playerId))
                .andExpect(status().isOk());
        return rideId;
    }

    /** 북쪽으로 5m/s 이동하는 60초짜리 표본 묶음. */
    private String batchJson(int seq, String key, int startIndex, double startLat, double lon) {
        StringBuilder points = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            int idx = startIndex + i;
            if (i > 0) {
                points.append(',');
            }
            points.append(String.format(
                    "{\"epochMs\":%d,\"lat\":%.6f,\"lon\":%.6f,\"accuracyM\":8.0,\"speedMps\":5.0}",
                    START_MS + idx * 1000L, startLat + idx * 0.000045, lon));
        }
        return "{\"batchSeq\":" + seq + ",\"idempotencyKey\":\"" + key + "\",\"points\":["
                + points + "],\"imuWindows\":[]}";
    }

    private BossReward rewardOf(List<BossReward> rewards, String playerPublicId) {
        Long id = playerIdOf(playerPublicId);
        return rewards.stream().filter(r -> r.getPlayerId().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("보상이 없습니다: " + playerPublicId));
    }

    private Long playerIdOf(String publicId) {
        return playerRepository.findByPublicId(publicId)
                .orElseThrow(() -> new AssertionError("플레이어를 찾을 수 없습니다: " + publicId))
                .getId();
    }

    private long profileXp(String playerId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/players/me").header("X-Player-Id", playerId))
                .andExpect(status().isOk()).andReturn();
        return read(result).get("xp").asLong();
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
