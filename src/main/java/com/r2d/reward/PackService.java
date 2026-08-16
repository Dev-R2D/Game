package com.r2d.reward;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import com.r2d.anomaly.AnomalyOutcome;
import com.r2d.card.Card;
import com.r2d.card.CardRepository;
import com.r2d.card.DeckEffect;
import com.r2d.card.OwnedCard;
import com.r2d.card.OwnedCardRepository;
import com.r2d.common.R2dException;
import com.r2d.config.GameRules;
import com.r2d.domain.ContributionType;
import com.r2d.domain.PackGrade;
import com.r2d.player.Player;
import com.r2d.settlement.DamageResult;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탐사 팩 생성과 개봉.
 *
 * <p>핵심 규칙: <b>등급은 확률로 정하지 않습니다.</b> 신규·갱신·검증 기여가 등급의 하한을 정하고,
 * 확률은 그 하한을 한 칸 올릴 수 있을 뿐 절대 내리지 못합니다. 그래서 "오늘 운이 나빠서
 * 미탐사 구간을 달린 보람이 없었다"는 경험이 구조적으로 생기지 않습니다.
 */
@Service
public class PackService {

    /** 등급별 카드 장수와 티어 범위, 코인. 앱에 그대로 공개하는 표입니다. */
    private static final Map<PackGrade, GradeSpec> GRADE_SPECS = Map.of(
            PackGrade.BRONZE, new GradeSpec(2, 1, 2, 100),
            PackGrade.SILVER, new GradeSpec(2, 1, 3, 200),
            PackGrade.GOLD, new GradeSpec(3, 2, 4, 350),
            PackGrade.SIGNATURE, new GradeSpec(3, 3, 5, 600)
    );

    private final ExplorationPackRepository packRepository;
    private final CardRepository cardRepository;
    private final OwnedCardRepository ownedCardRepository;
    private final DailyCounterService dailyCounterService;
    private final GameRules rules;
    private final Random random;

    @Autowired
    public PackService(ExplorationPackRepository packRepository,
                       CardRepository cardRepository,
                       OwnedCardRepository ownedCardRepository,
                       DailyCounterService dailyCounterService,
                       GameRules rules) {
        this(packRepository, cardRepository, ownedCardRepository, dailyCounterService, rules, new Random());
    }

    /** 테스트에서 굴림을 고정하기 위한 생성자. */
    PackService(ExplorationPackRepository packRepository,
                CardRepository cardRepository,
                OwnedCardRepository ownedCardRepository,
                DailyCounterService dailyCounterService,
                GameRules rules,
                Random random) {
        this.packRepository = packRepository;
        this.cardRepository = cardRepository;
        this.ownedCardRepository = ownedCardRepository;
        this.dailyCounterService = dailyCounterService;
        this.rules = rules;
        this.random = random;
    }

    /**
     * 정산 결과로 팩을 만듭니다. 일일 팩 상한에 걸리면 팩을 만들지 않습니다.
     *
     * @return 생성된 팩. 상한에 걸렸으면 비어 있습니다.
     */
    @Transactional
    public Optional<ExplorationPack> createPack(Player player, String rideId, DamageResult damage,
                                                AnomalyOutcome anomaly, DeckEffect deck) {
        Optional<ExplorationPack> existing = packRepository.findByRideId(rideId);
        if (existing.isPresent()) {
            return existing;
        }
        if (!dailyCounterService.tryGrantPack(player.getId())) {
            return Optional.empty();
        }

        GradeFloor floor = determineFloor(damage, anomaly, deck);
        PackGrade grade = rollUpgrade(floor.grade());
        String reason = floor.reason()
                + (grade != floor.grade() ? " · 추가 굴림으로 " + grade.getLabel() + " 승급" : "");

        GradeSpec spec = GRADE_SPECS.get(grade);
        List<String> cardCodes = rollCards(spec);

        return Optional.of(packRepository.save(
                new ExplorationPack(player.getId(), rideId, grade, reason, cardCodes, spec.coins())));
    }

    /**
     * 기여도로 등급 하한을 정합니다.
     *
     * <p>개척 계열 카드는 골드 하한에 필요한 미탐사 셀 수를 낮춰 줍니다. 이것이 "개척 덱이
     * 탐사 팩 등급을 보정한다"는 규칙의 실제 구현입니다.
     */
    GradeFloor determineFloor(DamageResult damage, AnomalyOutcome anomaly, DeckEffect deck) {
        long newCells = damage.countByType(ContributionType.NEW);
        long updatedCells = damage.countByType(ContributionType.UPDATE)
                + damage.countByType(ContributionType.VERIFY);

        // 시그니처는 발견만으로는 나오지 않습니다. 교차검증으로 확정까지 끝나야 합니다.
        if (anomaly.confirmed() > 0) {
            return new GradeFloor(PackGrade.SIGNATURE,
                    "신규 이상 후보 " + anomaly.confirmed() + "건의 검증이 완료되었습니다.");
        }

        int goldRequirement = Math.max(1, rules.pack().goldNewCells() - deck.trailblazeCount());
        if (newCells >= goldRequirement) {
            return new GradeFloor(PackGrade.GOLD,
                    "미탐사 셀 " + newCells + "개 확보 (개척 덱 보정으로 기준 " + goldRequirement + "개)");
        }
        if (updatedCells >= rules.pack().silverUpdatedCells()) {
            return new GradeFloor(PackGrade.SILVER, "갱신·검증 셀 " + updatedCells + "개 확보");
        }
        return new GradeFloor(PackGrade.BRONZE, "유효 주행 완료");
    }

    /** 하한 위에서만 굴립니다. 결과가 하한보다 낮아지는 경우는 없습니다. */
    private PackGrade rollUpgrade(PackGrade floor) {
        if (floor == PackGrade.SIGNATURE || random.nextDouble() >= rules.pack().upgradeChance()) {
            return floor;
        }
        return PackGrade.ofRank(Math.min(PackGrade.SIGNATURE.getRank(), floor.getRank() + 1)).atLeast(floor);
    }

    private List<String> rollCards(GradeSpec spec) {
        List<Card> pool = cardRepository.findByCommemorativeFalse().stream()
                .filter(card -> card.getTier() >= spec.minTier() && card.getTier() <= spec.maxTier())
                .toList();
        if (pool.isEmpty()) {
            return List.of();
        }
        List<String> picked = new ArrayList<>();
        for (int i = 0; i < spec.cardCount(); i++) {
            picked.add(pool.get(random.nextInt(pool.size())).getCode());
        }
        return picked;
    }

    /**
     * 팩을 개봉하고 내용물을 보유 카드로 옮깁니다.
     *
     * <p>개봉은 주행이 끝난 뒤에만 가능합니다. 주행 중 팩 개봉이 잠기는 것은 앱의 UI 제약이
     * 아니라 서버 규칙입니다 — 정산이 끝나야 팩 자체가 존재하기 때문입니다.
     */
    @Transactional
    public ExplorationPack open(Player player, Long packId) {
        ExplorationPack pack = packRepository.findById(packId)
                .orElseThrow(() -> R2dException.notFound("PACK_NOT_FOUND", "팩을 찾을 수 없습니다: " + packId));
        if (!pack.getPlayerId().equals(player.getId())) {
            throw R2dException.notFound("PACK_NOT_FOUND", "팩을 찾을 수 없습니다: " + packId);
        }
        pack.open();

        for (String code : pack.getCardCodes()) {
            Card card = cardRepository.findById(code).orElse(null);
            if (card == null) {
                continue;
            }
            // 이미 가진 카드는 중복 행을 만들지 않습니다. 도감 완성 여부만 보면 되기 때문입니다.
            if (ownedCardRepository.findByPlayerAndCard(player, card).isEmpty()) {
                ownedCardRepository.save(new OwnedCard(player, card, false, null));
            }
        }
        return pack;
    }

    /** 앱에 공개하는 등급별 구성표. 확률형 구성 요소를 명확히 표시하기 위한 데이터입니다. */
    public Map<String, Object> publishedOdds() {
        Map<String, Object> table = new LinkedHashMap<>();
        for (PackGrade grade : PackGrade.values()) {
            GradeSpec spec = GRADE_SPECS.get(grade);
            table.put(grade.getLabel(), Map.of(
                    "카드 장수", spec.cardCount(),
                    "카드 티어 범위", spec.minTier() + "~" + spec.maxTier(),
                    "코인", spec.coins()
            ));
        }
        return Map.of(
                "등급별구성", table,
                "등급결정방식", "신규·갱신·검증 기여가 등급 하한을 정하고, 하한 위에서만 "
                        + Math.round(rules.pack().upgradeChance() * 100) + "% 확률로 한 등급 승급합니다.",
                "주의", "카드와 게임 재화는 구매·양도·현금화할 수 없습니다."
        );
    }

    /** 등급 하한과 그 근거. */
    public record GradeFloor(PackGrade grade, String reason) {
    }

    private record GradeSpec(int cardCount, int minTier, int maxTier, int coins) {
    }
}
