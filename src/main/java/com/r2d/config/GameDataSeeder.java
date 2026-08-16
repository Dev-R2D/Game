package com.r2d.config;

import java.util.ArrayList;
import java.util.List;

import com.r2d.anomaly.AnomalyLifecycleService;
import com.r2d.card.Card;
import com.r2d.card.CardRepository;
import com.r2d.cell.RoadCell;
import com.r2d.cell.RoadCellRepository;
import com.r2d.common.CellGrid;
import com.r2d.domain.CardLine;
import com.r2d.domain.CellState;
import com.r2d.region.Region;
import com.r2d.region.RegionRepository;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기준 데이터 적재.
 *
 * <p>카드 도감과 지역은 게임이 성립하기 위한 기준 데이터이므로 항상 넣습니다.
 * 도로 셀은 실제 주행으로 생기는 것이 원칙이지만, 심사 모드가 첫 재생부터 "갱신 필요"와
 * "보수 후 재확인" 상태를 보여줄 수 있어야 하므로 소수의 예시 셀만 미리 만들어 둡니다.
 *
 * <p>{@link #seed()}는 여러 번 불러도 안전합니다. 이미 데이터가 있으면 아무것도 하지 않습니다.
 */
@Component
public class GameDataSeeder {

    /** 심사 로그가 지나가는 지역. */
    public static final String DEFAULT_REGION_CODE = "DONGTAN2";

    private final RegionRepository regionRepository;
    private final CardRepository cardRepository;
    private final RoadCellRepository cellRepository;

    public GameDataSeeder(RegionRepository regionRepository, CardRepository cardRepository,
                          RoadCellRepository cellRepository) {
        this.regionRepository = regionRepository;
        this.cardRepository = cardRepository;
        this.cellRepository = cellRepository;
    }

    @Transactional
    public void seed() {
        seedRegions();
        seedCards();
        seedSampleCells();
    }

    private void seedRegions() {
        if (regionRepository.count() > 0) {
            return;
        }
        regionRepository.saveAll(List.of(
                new Region(DEFAULT_REGION_CODE, "동탄2동", 128),
                new Region("YEONGTONG", "영통동", 42),
                new Region("GWANGGYO", "광교동", 7)
        ));
    }

    private void seedCards() {
        if (cardRepository.count() > 0) {
            return;
        }
        cardRepository.saveAll(List.of(
                new Card("TB_SCOUT_1", "첫 발자국", CardLine.TRAILBLAZE, 1,
                        "미탐사 구간에서 소폭의 기여 배율 보정을 받습니다."),
                new Card("TB_PATH_3", "미개척 경로", CardLine.TRAILBLAZE, 3,
                        "처음 가는 길과 원정 라이딩에서 신규 기여 배율이 크게 올라갑니다."),
                new Card("TB_FRONTIER_5", "개척자의 지도", CardLine.TRAILBLAZE, 5,
                        "신규 기여 배율과 탐사 팩 등급 하한을 함께 끌어올립니다."),

                new Card("SC_ROUTINE_1", "익숙한 길", CardLine.SCOUT, 1,
                        "오래 방치된 구간에서 소폭의 갱신 배율 보정을 받습니다."),
                new Card("SC_REVISIT_3", "재탐사 기록", CardLine.SCOUT, 3,
                        "노후·갱신 필요 구간의 기여 배율이 올라갑니다."),
                new Card("SC_ARCHIVE_5", "낡은 노선도", CardLine.SCOUT, 5,
                        "갱신 필요 구간에서 가장 큰 배율을 냅니다."),

                new Card("EN_STEADY_1", "꾸준한 페달", CardLine.ENDURANCE, 1,
                        "경로와 무관하게 유효 거리 기반 피해가 조금 올라갑니다."),
                new Card("EN_LONGHAUL_3", "장거리 주행", CardLine.ENDURANCE, 3,
                        "긴 주행에서 기본 피해와 완주 보상이 올라갑니다."),
                new Card("EN_IRONLEG_5", "무쇠 다리", CardLine.ENDURANCE, 5,
                        "어떤 경로에서도 유효 거리 기반 피해를 크게 올립니다."),

                new Card("VF_CHECK_1", "다시 보기", CardLine.VERIFICATION, 1,
                        "낮은 신뢰도 구간에서 소폭의 검증 배율 보정을 받습니다."),
                new Card("VF_CHAIN_3", "검증 체인", CardLine.VERIFICATION, 3,
                        "의심·재확인 셀을 지날 때 검증 기여가 크게 올라갑니다."),
                new Card("VF_WITNESS_5", "교차 관측자", CardLine.VERIFICATION, 5,
                        "고정 출퇴근 코스 반복 주행에서 가장 큰 검증 배율을 냅니다."),

                Card.commemorative(AnomalyLifecycleService.DISCOVERY_CARD_CODE, "최초 발견 기록",
                        CardLine.VERIFICATION,
                        "이상 후보를 처음 발견하면 잠금 상태로 지급되고, 교차검증으로 확정되면 해금됩니다.")
        ));
    }

    /** 셀 격자 한 칸에 해당하는 위도 간격. 셀이 겹치지 않도록 이 간격으로 띄웁니다. */
    private static final double CELL_LAT_STEP = 0.0006;

    /**
     * 예시 도로 셀.
     *
     * <p>두 묶음으로 나뉩니다.
     *
     * <p><b>경로 위 셀</b>은 심사 로그 B/D가 지나가는 자리에 둡니다. 첫 재생부터 신규·갱신·검증이
     * 한 결과 화면에 함께 나타나야 "어떤 길을 달렸는지에 따라 결과가 달라진다"는 규칙이 보입니다.
     *
     * <p><b>경로 밖 셀</b>은 미션 후보용입니다. 미션은 종류별로 최소 후보 수를 넘겨야 생성되므로,
     * 첫 실행에서 "오늘의 미션" 화면이 비어 있지 않도록 종류마다 충분한 수를 심어 둡니다.
     * 이 셀들은 어떤 심사 로그도 지나지 않으므로 재생을 반복해도 미탐사 상태로 남습니다.
     */
    private void seedSampleCells() {
        if (cellRepository.count() > 0) {
            return;
        }
        List<RoadCell> cells = new ArrayList<>();

        // 경로 위: 로그 B/D가 실제로 통과하는 자리
        cells.add(cellWithState(37.1975, 127.0935, CellState.STALE));
        cells.add(cellWithState(37.1985, 127.0938, CellState.STALE));
        cells.add(cellWithState(37.2000, 127.0955, CellState.LOW_CONFIDENCE));
        cells.add(cellWithState(37.2010, 127.0970, CellState.RECHECK_REQUIRED));

        // 경로 밖: 미션 후보 (남쪽 생활권)
        cells.addAll(column(37.1900, 127.1000, 4, CellState.UNSURVEYED));
        cells.addAll(column(37.1900, 127.1010, 4, CellState.STALE));
        cells.addAll(column(37.1900, 127.1020, 3, CellState.LOW_CONFIDENCE));
        cells.addAll(column(37.1900, 127.1030, 2, CellState.RECHECK_REQUIRED));

        // 안전 필터가 실제로 제외하는지 확인용
        cells.add(blockedCell(37.2100, 127.1100, "자동차전용도로 구간으로 미션 후보에서 제외"));

        cellRepository.saveAll(cells);
    }

    /** 같은 경도에서 위도를 한 칸씩 올려 가며 같은 상태의 셀을 여러 개 만듭니다. */
    private List<RoadCell> column(double startLat, double lon, int count, CellState state) {
        List<RoadCell> cells = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            cells.add(cellWithState(startLat + i * CELL_LAT_STEP, lon, state));
        }
        return cells;
    }

    private RoadCell cellWithState(double lat, double lon, CellState state) {
        RoadCell cell = new RoadCell(CellGrid.cellIdOf(lat, lon), DEFAULT_REGION_CODE);
        cell.setState(state);
        return cell;
    }

    private RoadCell blockedCell(double lat, double lon, String reason) {
        RoadCell cell = new RoadCell(CellGrid.cellIdOf(lat, lon), DEFAULT_REGION_CODE);
        cell.excludeFromMissions(reason);
        return cell;
    }
}
