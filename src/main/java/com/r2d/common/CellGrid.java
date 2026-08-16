package com.r2d.common;

/**
 * 도로 셀 격자.
 *
 * <p>보스·미션·기여도의 최소 단위인 "도로 셀"을 위경도 고정 격자로 정의합니다.
 * 한 변이 약 50m가 되도록 위도/경도 스텝을 나누어 두었고, 경도 스텝은 북위 37.5도
 * (수도권) 기준으로 보정했습니다. 셀 ID는 서버·클라이언트·로그가 모두 동일하게
 * 재계산할 수 있어야 하므로 격자 인덱스를 그대로 문자열로 씁니다.
 */
public final class CellGrid {

    /** 셀 한 변의 목표 길이(m). */
    public static final double CELL_SIZE_M = 50.0;

    /** 위도 1도 ≈ 111,320m. */
    private static final double LAT_STEP = CELL_SIZE_M / 111_320.0;

    /** 북위 37.5도에서 경도 1도 ≈ 88,300m. */
    private static final double LON_STEP = CELL_SIZE_M / 88_300.0;

    private CellGrid() {
    }

    /** 좌표가 속한 셀 ID. 같은 좌표는 항상 같은 ID를 돌려줍니다. */
    public static String cellIdOf(double lat, double lon) {
        long latIdx = (long) Math.floor(lat / LAT_STEP);
        long lonIdx = (long) Math.floor(lon / LON_STEP);
        return "C" + latIdx + "_" + lonIdx;
    }

    /** 셀 중심의 위도. 지도 렌더링과 미션 배치에 사용합니다. */
    public static double centerLat(String cellId) {
        return (indexOf(cellId, 0) + 0.5) * LAT_STEP;
    }

    /** 셀 중심의 경도. */
    public static double centerLon(String cellId) {
        return (indexOf(cellId, 1) + 0.5) * LON_STEP;
    }

    private static long indexOf(String cellId, int part) {
        String[] tokens = cellId.substring(1).split("_");
        if (tokens.length != 2) {
            throw new IllegalArgumentException("잘못된 셀 ID: " + cellId);
        }
        return Long.parseLong(tokens[part]);
    }
}
