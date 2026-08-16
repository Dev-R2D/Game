package com.r2d.reward;

import java.time.LocalDate;
import java.time.ZoneId;

import com.r2d.config.GameRules;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일일 상한 관리.
 *
 * <p>상한은 보상에만 걸리고 데이터 수집에는 걸리지 않습니다. 상한을 채운 뒤에도 주행은
 * 정상적으로 인정되고 도로 셀도 갱신됩니다. "더 받으려고 밤새 달릴 이유"만 없애는 장치입니다.
 */
@Service
public class DailyCounterService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DailyCounterRepository repository;
    private final GameRules rules;

    public DailyCounterService(DailyCounterRepository repository, GameRules rules) {
        this.repository = repository;
        this.rules = rules;
    }

    @Transactional
    public boolean tryGrantPack(Long playerId) {
        return counterFor(playerId).grantPack(rules.dailyCap().packs());
    }

    /** 요청한 XP 중 오늘 실제로 줄 수 있는 양. */
    @Transactional
    public int grantXp(Long playerId, int requested) {
        return counterFor(playerId).grantXp(requested, rules.dailyCap().xp());
    }

    /** 요청한 지역기여 점수 중 오늘 실제로 적립 가능한 양. */
    @Transactional
    public int grantContributionPoints(Long playerId, int requested) {
        return counterFor(playerId).grantContributionPoints(requested, rules.dailyCap().regionContributionPoints());
    }

    @Transactional(readOnly = true)
    public DailyCounter snapshot(Long playerId) {
        return repository.findByPlayerIdAndDay(playerId, today())
                .orElseGet(() -> new DailyCounter(playerId, today()));
    }

    private DailyCounter counterFor(Long playerId) {
        return repository.findByPlayerIdAndDay(playerId, today())
                .orElseGet(() -> repository.save(new DailyCounter(playerId, today())));
    }

    private LocalDate today() {
        return LocalDate.now(KST);
    }
}
