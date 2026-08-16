package com.r2d.ride;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * IMU 이벤트 윈도우.
 *
 * <p>100Hz 원시 가속도를 그대로 올리면 배터리와 통신량을 감당할 수 없으므로, 앱이 이벤트
 * 구간을 잘라 파형 특징만 추출해 올립니다. 서버는 피크값 하나가 아니라 지속 시간·주파수
 * 성분·진입 속도·거치 안정성을 함께 보고 후보 클래스를 판정합니다.
 */
@Entity
@Table(name = "imu_windows", indexes = @Index(name = "idx_imu_ride", columnList = "rideId, epochMs"))
public class ImuWindow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String rideId;

    @Column(nullable = false)
    private long epochMs;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lon;

    /** 이벤트 지속 시간(ms). 짧고 날카로울수록 충격성 후보, 길고 완만하면 정상 시설물에 가깝습니다. */
    @Column(nullable = false)
    private int durationMs;

    /** 구간 내 최대 가속도(g). 보상 계산에는 절대 쓰이지 않고 분류에만 씁니다. */
    @Column(nullable = false)
    private double peakG;

    /** 구간 RMS 가속도(g). 반복 진동성 후보 판정에 씁니다. */
    @Column(nullable = false)
    private double rmsG;

    /** 우세 주파수(Hz). 고주파일수록 날카로운 충격입니다. */
    @Column(nullable = false)
    private double dominantHz;

    /** 자이로 RMS(dps). 거치 흔들림과 노면 충격을 구분하는 보조 특징입니다. */
    @Column(nullable = false)
    private double gyroRmsDps;

    /** 이벤트 진입 속도(m/s). 같은 요철도 속도에 따라 충격이 달라지므로 정규화에 필요합니다. */
    @Column(nullable = false)
    private double entrySpeedMps;

    /** 거치 방향이 유지되고 있는지. false면 판정 보류로 내립니다. */
    @Column(nullable = false)
    private boolean mountStable = true;

    protected ImuWindow() {
    }

    public ImuWindow(String rideId, long epochMs, double lat, double lon, int durationMs, double peakG,
                     double rmsG, double dominantHz, double gyroRmsDps, double entrySpeedMps,
                     boolean mountStable) {
        this.rideId = rideId;
        this.epochMs = epochMs;
        this.lat = lat;
        this.lon = lon;
        this.durationMs = durationMs;
        this.peakG = peakG;
        this.rmsG = rmsG;
        this.dominantHz = dominantHz;
        this.gyroRmsDps = gyroRmsDps;
        this.entrySpeedMps = entrySpeedMps;
        this.mountStable = mountStable;
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

    public int getDurationMs() {
        return durationMs;
    }

    public double getPeakG() {
        return peakG;
    }

    public double getRmsG() {
        return rmsG;
    }

    public double getDominantHz() {
        return dominantHz;
    }

    public double getGyroRmsDps() {
        return gyroRmsDps;
    }

    public double getEntrySpeedMps() {
        return entrySpeedMps;
    }

    public boolean isMountStable() {
        return mountStable;
    }
}
