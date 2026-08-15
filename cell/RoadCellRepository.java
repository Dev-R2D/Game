package com.r2d.cell;

import java.util.Collection;
import java.util.List;

import com.r2d.domain.CellState;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoadCellRepository extends JpaRepository<RoadCell, String> {

    List<RoadCell> findByCellIdIn(Collection<String> cellIds);

    List<RoadCell> findByRegionCode(String regionCode);

    long countByRegionCodeAndState(String regionCode, CellState state);

    /** 지도 화면용 사각 영역 조회. */
    @Query("""
            select c from RoadCell c
            where c.centerLat between :minLat and :maxLat
              and c.centerLon between :minLon and :maxLon
            """)
    List<RoadCell> findInBoundingBox(@Param("minLat") double minLat,
                                     @Param("maxLat") double maxLat,
                                     @Param("minLon") double minLon,
                                     @Param("maxLon") double maxLon);

    /** 미션 후보 조회: 안전 필터를 통과하고 데이터 수요가 있는 셀만. */
    @Query("""
            select c from RoadCell c
            where c.regionCode = :regionCode
              and c.bikeAccessible = true
              and c.state <> com.r2d.domain.CellState.FRESH
            """)
    List<RoadCell> findMissionCandidates(@Param("regionCode") String regionCode);
}
