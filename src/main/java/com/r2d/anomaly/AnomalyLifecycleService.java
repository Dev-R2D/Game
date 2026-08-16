package com.r2d.anomaly;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.r2d.analysis.AnomalyDetection;
import com.r2d.card.Card;
import com.r2d.card.CardRepository;
import com.r2d.card.OwnedCard;
import com.r2d.card.OwnedCardRepository;
import com.r2d.cell.RoadCell;
import com.r2d.cell.RoadCellRepository;
import com.r2d.config.GameRules;
import com.r2d.domain.AnomalyState;
import com.r2d.player.Player;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 서브몹(노면 이상 후보)의 상태 전이.
 *
 * <p>교차검증이 곧 성장 연출이라는 규칙이 여기서 실행됩니다. 발견은 잠금 상태의 기념 카드까지만
 * 주고, 실제 확정 보상은 서로 다른 이용자의 독립 관측이 쌓였을 때만 나갑니다. 라이더가 적은
 * 지역에서는 같은 이용자라도 날짜가 분리된 반복 관측을 대체 경로로 인정합니다.
 */
@Service
public class AnomalyLifecycleService {

    /** 최초 발견 시 잠금 상태로 지급되는 기념 카드 코드. */
    public static final String DISCOVERY_CARD_CODE = "MEMO_DISCOVERY";

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AnomalyCandidateRepository candidateRepository;
    private final AnomalyObservationRepository observationRepository;
    private final RoadCellRepository cellRepository;
    private final CardRepository cardRepository;
    private final OwnedCardRepository ownedCardRepository;
    private final GameRules rules;

    public AnomalyLifecycleService(AnomalyCandidateRepository candidateRepository,
                                   AnomalyObservationRepository observationRepository,
                                   RoadCellRepository cellRepository,
                                   CardRepository cardRepository,
                                   OwnedCardRepository ownedCardRepository,
                                   GameRules rules) {
        this.candidateRepository = candidateRepository;
        this.observationRepository = observationRepository;
        this.cellRepository = cellRepository;
        this.cardRepository = cardRepository;
        this.ownedCardRepository = ownedCardRepository;
        this.rules = rules;
    }

    /**
     * 한 주행의 후보 판정을 서브몹 상태에 반영합니다.
     *
     * @param detections 이번 주행에서 나온 후보 판정 전체
     * @param traversedCells 이번 주행이 유효하게 지나간 셀. 재확인(소멸) 판정에 필요합니다.
     * @param simulated 심사 재현 로그 기반 주행인지
     */
    @Transactional
    public AnomalyOutcome apply(String rideId, Player player, List<AnomalyDetection> detections,
                               Collection<String> traversedCells, boolean simulated, Instant now) {
        LocalDate observedDate = now.atZone(KST).toLocalDate();
        List<AnomalyOutcome.Transition> transitions = new ArrayList<>();
        int discovered = 0;
        int confirmed = 0;
        int resolved = 0;

        // 한 셀에서 여러 윈도우가 잡히면 가장 신뢰도 높은 판정 하나만 대표로 씁니다.
        Map<String, AnomalyDetection> strongestPerCell = new HashMap<>();
        for (AnomalyDetection detection : detections) {
            if (!detection.isTrackable()) {
                continue;
            }
            strongestPerCell.merge(detection.cellId(), detection,
                    (left, right) -> left.confidence() >= right.confidence() ? left : right);
        }

        for (AnomalyDetection detection : strongestPerCell.values()) {
            Optional<AnomalyCandidate> existing = candidateRepository.findByCellId(detection.cellId());
            if (existing.isEmpty()) {
                AnomalyCandidate candidate = candidateRepository.save(new AnomalyCandidate(
                        detection.cellId(), detection.lat(), detection.lon(), detection.anomalyClass(),
                        detection.confidence(), player.getPublicId(), now));
                recordObservation(candidate, rideId, player, detection, now, observedDate, simulated);
                grantLockedDiscoveryCard(player, detection.cellId());
                discovered++;
                transitions.add(new AnomalyOutcome.Transition(detection.cellId(), null, AnomalyState.SUSPECT,
                        candidate.getConfidence(), "새 이상 후보를 발견했습니다. 교차검증 전까지는 의심 상태입니다."));
                continue;
            }

            AnomalyCandidate candidate = existing.get();
            if (observationRepository.existsByCandidateIdAndRideId(candidate.getId(), rideId)) {
                continue;
            }

            boolean newObserver = !observationRepository.existsByCandidateIdAndPlayerId(
                    candidate.getId(), player.getId());
            boolean newDay = !observationRepository.existsByCandidateIdAndObservedDate(
                    candidate.getId(), observedDate);

            AnomalyState before = candidate.getState();
            candidate.reinforce(detection.confidence(), newObserver, newDay, now);
            recordObservation(candidate, rideId, player, detection, now, observedDate, simulated);

            GameRules.Anomaly a = rules.anomaly();
            if (candidate.tryConfirm(a.confirmDistinctUsers(), a.confirmDistinctDays(), a.confirmConfidence(), now)) {
                confirmed++;
                unlockDiscoveryCards(candidate.getCellId());
                transitions.add(new AnomalyOutcome.Transition(candidate.getCellId(), before, AnomalyState.CONFIRMED,
                        candidate.getConfidence(),
                        "독립 관측 " + candidate.getDistinctObservers() + "명 · 분리된 날짜 "
                                + candidate.getDistinctDays() + "일로 확정 기준을 충족했습니다."));
            }
        }

        // 후보가 잡히지 않은 채 지나간 셀 중 확정 상태인 곳은 재확인 통과로 셉니다.
        for (String cellId : traversedCells) {
            if (strongestPerCell.containsKey(cellId)) {
                continue;
            }
            Optional<AnomalyCandidate> existing = candidateRepository.findByCellId(cellId);
            if (existing.isEmpty() || existing.get().getState() != AnomalyState.CONFIRMED) {
                continue;
            }
            AnomalyCandidate candidate = existing.get();
            if (observationRepository.existsByCandidateIdAndRideId(candidate.getId(), rideId)) {
                continue;
            }
            candidate.recordCleanPass(now);
            if (candidate.tryResolve(rules.anomaly().resolveCleanRides(), now)) {
                resolved++;
                cellRepository.findById(cellId).ifPresent(RoadCell::clearRecheck);
                transitions.add(new AnomalyOutcome.Transition(cellId, AnomalyState.CONFIRMED, AnomalyState.RESOLVED,
                        candidate.getConfidence(),
                        "재주행 " + candidate.getCleanPasses() + "회에서 기존 패턴이 반복되지 않아 소멸했습니다."));
            }
        }

        return new AnomalyOutcome(discovered, confirmed, resolved, List.copyOf(transitions));
    }

    /** 확정된 후보가 보수 보고를 받으면 셀을 재확인 대상으로 돌립니다. */
    @Transactional
    public void markRepairReported(String cellId) {
        cellRepository.findById(cellId).ifPresent(RoadCell::markRecheckRequired);
    }

    private void recordObservation(AnomalyCandidate candidate, String rideId, Player player,
                                   AnomalyDetection detection, Instant now, LocalDate observedDate,
                                   boolean simulated) {
        observationRepository.save(new AnomalyObservation(candidate.getId(), rideId, player.getId(),
                detection.anomalyClass(), detection.confidence(), now, observedDate, simulated));
    }

    /**
     * 최초 발견자에게 잠금 상태의 기념 카드를 지급합니다.
     *
     * <p>발견 즉시 확정 보상을 주지 않는 이유는, 단발 관측만으로 이상을 단정하지 않는다는
     * 판정 규칙과 보상 규칙을 어긋나지 않게 하기 위해서입니다.
     */
    private void grantLockedDiscoveryCard(Player player, String cellId) {
        Card card = cardRepository.findById(DISCOVERY_CARD_CODE).orElse(null);
        if (card == null) {
            return;
        }
        boolean alreadyGranted = !ownedCardRepository
                .findByPlayerAndSourceCellIdAndLockedTrue(player, cellId).isEmpty();
        if (!alreadyGranted) {
            ownedCardRepository.save(new OwnedCard(player, card, true, cellId));
        }
    }

    /** 확정 전이가 일어나면 해당 셀에 걸린 잠금 기념 카드를 모두 해금합니다. */
    private void unlockDiscoveryCards(String cellId) {
        ownedCardRepository.findBySourceCellIdAndLockedTrue(cellId).forEach(OwnedCard::unlock);
    }
}
