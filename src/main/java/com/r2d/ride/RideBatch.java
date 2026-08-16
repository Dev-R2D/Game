package com.r2d.ride;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 업로드된 센서 배치 기록.
 *
 * <p>오프라인 큐가 연결 복구 후 같은 배치를 다시 보내는 것은 정상 동작입니다. 그래서 중복은
 * 오류가 아니라 "이미 반영됨"으로 처리해야 하며, 그 판단 근거를 여기 남깁니다.
 * (rideId, batchSeq)와 idempotencyKey 두 축으로 중복을 막습니다.
 */
@Entity
@Table(name = "ride_batches", uniqueConstraints = {
        @UniqueConstraint(name = "uk_batch_ride_seq", columnNames = {"rideId", "batchSeq"}),
        @UniqueConstraint(name = "uk_batch_idempotency", columnNames = {"idempotencyKey"})
})
public class RideBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String rideId;

    @Column(nullable = false)
    private int batchSeq;

    @Column(nullable = false, length = 80)
    private String idempotencyKey;

    @Column(nullable = false)
    private int pointCount;

    @Column(nullable = false)
    private int imuWindowCount;

    @Column(nullable = false)
    private Instant receivedAt = Instant.now();

    protected RideBatch() {
    }

    public RideBatch(String rideId, int batchSeq, String idempotencyKey, int pointCount, int imuWindowCount) {
        this.rideId = rideId;
        this.batchSeq = batchSeq;
        this.idempotencyKey = idempotencyKey;
        this.pointCount = pointCount;
        this.imuWindowCount = imuWindowCount;
    }

    public Long getId() {
        return id;
    }

    public String getRideId() {
        return rideId;
    }

    public int getBatchSeq() {
        return batchSeq;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public int getPointCount() {
        return pointCount;
    }

    public int getImuWindowCount() {
        return imuWindowCount;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
