package com.r2d.mission;

import java.util.List;

import com.r2d.domain.CardLine;

/**
 * 오늘의 미션 카드.
 *
 * <p>저장하지 않고 현재 셀 상태에서 매번 생성합니다. 지역이 정화되면 미션이 자연히 줄어드는
 * 것이 정상 동작이며, 미리 만들어 둔 미션이 남아 이미 조사된 길로 유도하는 일을 막습니다.
 */
public record Mission(
        String code,
        String title,
        String summary,
        MissionType type,
        /** 이 미션이 겨냥하는 셀 수. */
        int targetCellCount,
        /** 예상 소요 시간(분) 범위. */
        int estimatedMinutesMin,
        int estimatedMinutesMax,
        /** 이 미션에 유리한 카드 계열. */
        CardLine recommendedLine,
        String rewardSummary,
        /** 안전 필터를 통과한 대표 셀 목록. 지도 표시에 씁니다. */
        List<String> sampleCellIds,
        /** 안전 안내. 미션마다 반드시 함께 표시합니다. */
        String safetyNote
) {
}
