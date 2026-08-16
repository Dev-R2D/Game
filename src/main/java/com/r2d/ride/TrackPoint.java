package com.r2d.ride;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** 주행 중 기록된 위치 표본 하나. */
@Entity
@Table(name = "track_points", indexes = @Index(name = "idx_point_ride", columnList = "rideId, epochMs"))
public class TrackPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String rideId;

    /** 기기 기준 관측 시각(epoch ms). 순서 뒤바뀜 검사에 사용합니다. */
    @Column(nullable = false)
    private long epochMs;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lon;

    /** GPS 오차 반경(m). */
    @Column(nullable = false)
    private double accuracyM;

    /** 기기가 보고한 순간 속도(m/s). 음수면 미보고로 간주합니다. */
    @Column(nullable = false)
    private double speedMps;

    protected TrackPoint() {
    }

    public TrackPoint(String rideId, long epochMs, double lat, double lon, double accuracyM, double speedMps) {
        this.rideId = rideId;
        this.epochMs = epochMs;
        this.lat = lat;
        this.lon = lon;
        this.accuracyM = accuracyM;
        this.speedMps = speedMps;
    }

    public Long getId() {
        return id;
    }

    public String getRideId() {
        return rideId;
    }

    public long getEpochMs() {
        return epochMs;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

    public double getAccuracyM() {
        return accuracyM;
    }

    public double getSpeedMps() {
        return speedMps;
    }
}
