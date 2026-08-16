package com.r2d.common;

/**
 * 위경도 좌표 계산 유틸리티.
 *
 * <p>PostGIS 같은 공간 확장 없이 동작해야 하므로(제출 빌드는 H2 기반) 하버사인 거리와
 * 고정 격자 셀만 사용합니다. 대한민국 위도대(33~39N)에서의 오차는 게임 정산 목적에는 충분합니다.
 */
public final class GeoUtils {

    /** 지구 평균 반지름(m). */
    private static final double EARTH_RADIUS_M = 6_371_008.8;

    private GeoUtils() {
    }

    /** 두 좌표 사이의 하버사인 거리(m). */
    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }
}
