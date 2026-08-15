package com.r2d.judge;

import java.util.List;

import com.r2d.analysis.AnomalyDetection;
import com.r2d.analysis.TransportAssessment;
import com.r2d.domain.CellState;
import com.r2d.domain.Validity;

/**
 * 심사용 분석 오버레이.
 *
 * <p>실제 주행에서는 이 정보를 절대 화면에 띄우지 않습니다. 주행 중 확인할 것이 있으면
 * 화면을 보게 되기 때문입니다. 심사 모드에서만 "심사용 분석 화면" 배너 아래 공개합니다.
 */
public record JudgeOverlay(
        String rideId,
        String logId,
        /** 로그 출처 한 줄. 파생 로그를 실측으로 오인하지 않도록 항상 함께 내려보냅니다. */
        String provenance,
        boolean derived,
        /** 클라이언트가 몇 배속으로 재생할지. 서버 계산은 배속과 무관합니다. */
        int playbackSpeed,
        TransportAssessment transport,
        List<Frame> frames,
        List<AnomalyDetection> detections
) {

    /**
     * 재생 타임라인의 한 칸.
     *
     * @param cumulativeEstimatedDamage 이 시점까지의 예상 피해. 로컬 예상치이며 서버 확정치와 다를 수 있습니다.
     */
    public record Frame(
            long epochMs,
            double lat,
            double lon,
            String cellId,
            CellState cellState,
            double gpsAccuracyM,
            double speedMps,
            Validity validity,
            String validityReason,
            double cumulativeDistanceM,
            double cumulativeEstimatedDamage
    ) {
    }
}
