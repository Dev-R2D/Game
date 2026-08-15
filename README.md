# R2D 백엔드

위치기반(LBS) 수집형 오토배틀 RPG **R2D**의 서버입니다.
게임 소개서 v2.0(NHN NAN 2026 사전과제)의 코어 루프와 안전·개인정보 규칙을 구현했습니다.

> **핵심 문장**: 같은 거리를 달려도 어떤 길을 달렸는지에 따라 데미지와 보상이 달라집니다.

---

## 1. 실행

```bash
cd backend && ./gradlew bootRun
```

| 항목 | 값 |
|---|---|
| Java | 17 |
| 프레임워크 | Spring Boot 4.0.7 (Spring Framework 7, Jackson 3) |
| DB | H2 파일 DB (`./data/r2d`) — 별도 설치 없이 바로 실행 |
| 포트 | 8080 |
| H2 콘솔 | http://localhost:8080/h2-console |

테스트:

```bash
cd backend && ./gradlew test
```

---

## 2. 2분 안에 핵심 루프 확인하기 (심사 모드)

실제 위치 권한과 자전거 이동 없이 `주행 → 판정 → 보스 → 팩 → 지도 갱신`을 끝까지 볼 수 있습니다.

```bash
curl -s localhost:8080/api/v1/judge/logs
```

```bash
curl -s -X POST localhost:8080/api/v1/judge/replay -H 'Content-Type: application/json' -d '{"logId":"A","deckCardCodes":["TB_SCOUT_1","TB_PATH_3","TB_FRONTIER_5"],"playbackSpeed":30}'
```

권장 순서:

| 순서 | 로그 | 확인 포인트 |
|---|---|---|
| 1 | `A` | 미탐사 셀 확보, 개척 덱 배율, 보스 HP 감소, 골드 팩 |
| 2 | `B` | 후보 탐지 + 정상 시설물 오탐 배제, **의심** 몹 생성 |
| 3 | `C1` | 다른 라이더의 독립 관측 → **의심 → 확정** 전이, 시그니처 팩 |
| 4 | `C2` | 관측 추가 시 중복 제거(같은 후보가 두 번 세어지지 않음) |
| 5 | `D` ×3 | 이상 없는 재주행 반복 → **소멸** 전이 |
| 6 | `E` | 좌표 점프·신호 공백·정확도 저하가 **무효/보류**로 구분되어 결과에 표시 |

재생은 실제 주행과 **완전히 같은** 수집·정산 파이프라인을 탑니다. 다른 점은
`RideMode.JUDGE_SIM` 표시와 로그 출처 표기뿐입니다.

### 로그 출처 표기

번들된 로그는 전부 `TEST`(테스트 로그) 또는 `DERIVED`(파생 로그)로 표시됩니다.
**실측 주행 로그로 표기된 로그는 하나도 없습니다.** 실제 주행 로그를 확보하면
`src/main/resources/judge-logs/`의 정의를 교체하고 `sourceType`을 `REAL_RIDE`로 바꾸면 됩니다.
파생 로그(C1/C2)는 응답의 `provenance`에 원본과 적용한 편차가 항상 함께 나갑니다.

---

## 3. 코어 루프와 구현 위치

```
출발 전            주행 중                도착 후
보스·덱 선택   →   센서 배치 업로드   →   품질검사 → 셀매칭 → 유효성판정
                   (서버는 저장만)         → 기여이벤트 → 보스·보상 정산 → 팩
```

| 단계 | 클래스 |
|---|---|
| 덱 편성·시너지 | `card/DeckService`, `card/DeckEffect` |
| 주행 세션·배치 수집 | `ride/RideService` |
| 위치 신뢰도 검증 | `analysis/QualityGateService` |
| 주행 수단 판별 | `analysis/TransportModeClassifier` |
| 노면 이상 후보 분류 | `analysis/AnomalyDetector` |
| 서브몹 상태 전이 | `anomaly/AnomalyLifecycleService` |
| 셀 상태 관리 | `cell/CellService`, `cell/RoadCell` |
| 확정 피해 계산 | `settlement/DamageCalculator` |
| 정산 오케스트레이션 | `settlement/SettlementService` |
| 지역 공동 보스 | `boss/BossService` |
| 탐사 팩 | `reward/PackService` |
| 보상 원장·일일 상한 | `reward/RewardService`, `reward/DailyCounterService` |
| 미션·안전 필터 | `mission/MissionService` |
| 심사 모드 | `judge/JudgeModeService` |

### 확정 피해

```
확정 피해 = 유효 거리 기반 피해 × 데이터 기여 배율 × 덱 시너지 × 신뢰도 계수
```

- **유효 거리 기반 피해**: 자전거 주행으로 인정된 거리. 오래 달리기만 해도 최소 성장은 유지됩니다.
- **데이터 기여 배율**: 셀 상태(미조사 2.0 / 재확인 1.8 / 갱신 1.5 / 낮은 신뢰도 1.4 / 최신 1.0)
  × 덱 계열 보정 × 반복 방문 감쇠.
- **덱 시너지**: 같은 계열 3장 1.30 / 2장 1.15 / 계열 혼합 1.05.
- **신뢰도 계수**: GPS 품질과 주행 수단 신뢰도로 0.5~1.0.

---

## 4. 안전 규칙이 코드로 강제되는 지점

| 규칙 | 구현 |
|---|---|
| 충격 세기가 보상을 올리지 않음 | `DamageCalculator`는 IMU·충격 데이터에 **접근 자체를 못 합니다**. 입력이 거리·셀 상태·덱·신뢰도뿐이라 파손 구간을 세게 밟아 이득 볼 경로가 존재하지 않습니다. |
| 주행 중 화면을 볼 이유 제거 | 주행 중 호출 가능한 API는 배치 업로드 하나뿐입니다. 실시간 전투·보상 조회 API가 없습니다. |
| 팩 개봉은 주행 종료 후에만 | 팩은 정산 시점에 생성됩니다. 주행 중에는 개봉할 팩이 존재하지 않습니다. |
| 속도 경쟁 제거 | 속도 기반 보상·순위 필드가 도메인에 없습니다. |
| 막타 보너스 없음 | 보스 처치 보상은 참여자 전원에게 누적 기여 비율대로 배분됩니다. 마지막 일격을 넣어도 기여가 적으면 적게 받습니다. |
| 일일 상한 | `DailyCounterService`. 상한은 **보상에만** 걸리고 도로 데이터 수집은 계속됩니다. |
| 안전 필터 | `RoadCell.bikeAccessible=false`인 셀은 데이터가 아무리 부족해도 미션·보스 후보에서 제외됩니다. |
| 확정된 이상만 안전 레이어에 반영 | `/map/anomalies`의 `usableForSafetyLayer`는 `CONFIRMED`에서만 true입니다. |

### 과장 방지

- 후보 클래스는 `정상 시설물 / 충격성 이상 후보 / 반복 진동성 이상 후보 / 판정 보류`뿐입니다.
  포트홀·균열·단차 같은 **확정 결함명은 코드 어디에도 없습니다**(P1 범위).
- 단일 관측의 신뢰도는 0.85를 넘지 못하도록 `AnomalyDetector`가 상한을 겁니다.
  확정은 교차검증에서만 일어납니다.
- 규칙 기반 분류기입니다. 학습 모델 정확도를 주장하지 않는 대신, 판정 규칙과 오탐 반례를
  공개할 수 있는 형태를 택했습니다. 임계값은 전부 `GameRules`(= `application.yml`의 `r2d.*`)에
  모여 있어 실측 로그로 검증한 뒤 코드 변경 없이 조정할 수 있습니다.

### 개인정보

- 정산·기여 계산은 내부 가명 `publicId`로만 합니다.
- 공개 화면에는 동의한 닉네임만 나가고, 미동의 시 "익명 라이더"로 내려갑니다(`Player.displayName()`).
- 다른 이용자의 전체 경로·원시 센서·정확한 위치·기기 식별자를 내려보내는 API가 없습니다.
  지도는 셀 단위 집계만 제공하고, 조회 범위도 0.05도로 제한해 전량 조회를 막습니다.

### 게임 재화 / 지역기여 점수 분리

`RewardLedger`(게임 재화)와 `RegionContributionLedger`(캠페인 진행도)는 별도 테이블이며
서로 이체 경로가 없습니다. 지역기여 점수는 **차감 연산 자체를 만들지 않았습니다**(`accrue`만 존재).
유료 상품 입력은 적립 계산 어디에도 들어가지 않습니다.

---

## 5. 데이터 무결성

| 위험 | 방어 |
|---|---|
| 오프라인 큐 재전송 | `RideBatch`의 `idempotencyKey` + `(rideId, batchSeq)` 유일 제약. 중복은 오류가 아니라 `accepted=false`로 응답합니다. |
| 중복 정산 | `RideSettlement.rideId` 유일. 이미 정산된 주행은 재계산 없이 기존 결과를 반환합니다. |
| 중복 보스 피해 | `BossContribution.rideId` 유일. 동시 요청·재전송에도 HP는 한 번만 깎입니다. |
| 같은 주행이 한 셀을 여러 번 통과 | `CellObservation`의 `(rideId, cellId)` 유일. |
| 같은 주행이 후보 관측 수를 부풀림 | `AnomalyObservation`의 `(candidateId, rideId)` 유일. |
| 표본 순서 뒤섞임·중복 | `QualityGateService`가 역순/동일 시각 표본을 구간 생성 전에 제외합니다. |

---

## 6. API

> **프론트엔드 연동은 [API.md](API.md)를 보세요.** 실제 요청/응답 예시, 화면별 호출 순서,
> CORS 설정, 자주 하는 실수까지 정리돼 있습니다. 아래는 엔드포인트 목록입니다.

플레이어 스코프 엔드포인트는 `Authorization: Bearer <accessToken>` 헤더를 받습니다.
(전환 기간 동안 레거시 `X-Player-Id` 헤더도 허용 — `r2d.auth.allow-legacy-player-id-header`로 끕니다.)

### 인증
| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/v1/auth/signup` | 이메일 가입 (계정 + 플레이어 동시 생성) |
| POST | `/api/v1/auth/login` | 로그인 |
| POST | `/api/v1/auth/refresh` | 토큰 갱신 (리프레시 토큰 회전) |
| POST | `/api/v1/auth/logout` | 이 기기 세션 종료 |
| POST | `/api/v1/auth/password` | 비밀번호 변경 (전 기기 로그아웃) |
| GET | `/api/v1/auth/me` | 세션 확인 |

### 플레이어
| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/v1/players` | 닉네임 등록 |
| GET | `/api/v1/players/me` | 내 프로필·원장·오늘 사용량 |
| PATCH | `/api/v1/players/me/nickname-visibility` | 닉네임 공개 여부 변경 |
| GET | `/api/v1/players/me/cards` | 보유 카드(잠금 상태 포함) |
| GET | `/api/v1/players/{publicId}` | 타인 공개 정보(레벨·표시 이름만) |

### 카드·덱
| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/v1/cards` | 카드 도감 |
| POST | `/api/v1/decks/preview` | 덱 3장 편성 효과 미리보기 |

### 지역·보스·미션
| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/v1/regions` | 지역 목록 |
| GET | `/api/v1/regions/{code}/boss` | 진행 중 보스(없으면 데이터 수요로 생성) |
| GET | `/api/v1/regions/{code}/missions` | 오늘의 미션(안전 필터 통과분만) |
| GET | `/api/v1/regions/{code}/progress` | 지역 정화 진행도 |

### 주행
| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/v1/rides` | 출발(보스·덱 선택) |
| POST | `/api/v1/rides/{rideId}/batches` | 센서 배치 업로드 |
| POST | `/api/v1/rides/{rideId}/finish` | 종료·정산 |
| GET | `/api/v1/rides/{rideId}/settlement` | 정산 결과 |
| GET | `/api/v1/rides/active` | 진행 중 주행(앱 재시작 시 이어받기) |

### 팩
| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/v1/packs/pending` | 개봉 대기 팩(내용물 비공개) |
| POST | `/api/v1/packs/{packId}/open` | 개봉 |
| GET | `/api/v1/packs/odds` | 등급별 구성 공개표 |

### 보스 보상
| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/v1/boss-rewards/pending` | 수령 대기 보상 |
| POST | `/api/v1/boss-rewards/{id}/claim` | 개별 수령 |
| POST | `/api/v1/boss-rewards/claim-all` | 일괄 수령 |
| GET | `/api/v1/bosses/{bossId}` | 처치된 보스 포함 상세 조회 |

### 지도
| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/v1/map/cells` | 셀 상태(bbox 필수, 0.05도 제한) |
| GET | `/api/v1/map/anomalies` | 이상 후보(확정만 안전 레이어 사용 가능) |

### 심사 모드
| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/v1/judge/logs` | 재생 가능한 로그와 출처 |
| POST | `/api/v1/judge/replay` | 재생 + 분석 오버레이 + 정산 |

---

## 7. 구현 범위

| 범위 | 내용 | 상태 |
|---|---|---|
| **BUILD** | 주행 세션, 배치 수집, 셀 매칭, 유효 거리 공격, 위치 신뢰도 검증, 주행 수단 판별, 이상 후보 분류, 보스 정산, 팩, 결과 화면 데이터, 지도 갱신 | 구현 완료 |
| **SIM** | 로그 재생, 파생 로그 기반 복수 관측, 의심→확정→소멸 전이, 중복 제거, 분석 오버레이 | 구현 완료 |
| **P1** | 카메라 교차검증, 세부 결함 유형 확정, 지역 상점 쿠폰 발급, 실사용자 대규모 교차검증 | **미구현** (의도적) |
| **P2** | 실시간 대규모 레이드, 길드, 모델 자동 재학습 | **미구현** (의도적) |

P1 쿠폰은 도메인에 **원장 자체를 만들지 않았습니다.** 문서가 "APK에 없으면 기능 목록에 넣지 말 것"을
요구하므로, 절반만 구현된 쿠폰 코드가 남아 있지 않도록 했습니다.

---

## 8. 제출 전 교체가 필요한 항목

- `src/main/resources/judge-logs/*.json` → 실측 주행 로그로 교체 후 `sourceType`을 `REAL_RIDE`로 변경
- `GameRules` 임계값 → 실제 자전거·차량·도보 로그로 검증 후 조정
- AI 성능 지표(정밀도·재현율·F1) → 실측 후 문서에 기입. **측정 전까지 수치를 만들어 넣지 않습니다.**
- 운영 DB → `application.yml`의 `spring.datasource` 블록만 교체 (도메인은 표준 JPA만 사용)
- **`R2D_AUTH_JWT_SECRET` 환경변수 설정** — 비워 두면 기동할 때마다 임의 키가 생겨 재시작 시 로그인이 모두 풀립니다
- **`r2d.auth.allow-legacy-player-id-header: false`** — `X-Player-Id`는 인증이 아니므로 전환이 끝나면 꺼야 합니다
- 이메일 인증 메일 발송 → 현재 미구현. 켜려면 SMTP 설정과 `emailVerified` 게이트 추가 필요
