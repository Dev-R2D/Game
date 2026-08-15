# R2D API — 프론트엔드 연동 가이드

모든 예시는 실제 실행 중인 서버에서 캡처한 응답입니다.

---

## 0. 기본 사항

| 항목 | 값 |
|---|---|
| Base URL | `http://localhost:8080/api/v1` |
| Content-Type | `application/json` |
| 인증 | `Authorization: Bearer <accessToken>` |
| 레거시 인증 | `X-Player-Id: <publicId>` — 전환 기간 동안만 허용 |
| CORS | `localhost:3000`, `5173`, `8081`, `127.0.0.1:3000`, `127.0.0.1:5173` 허용 |

### 인증 모델

이메일 + 비밀번호로 가입·로그인하고 **액세스 토큰**을 받습니다. 게임 API는 그 토큰으로 호출합니다.

```js
const api = (path, { method = 'GET', body } = {}) =>
  fetch(`http://localhost:8080/api/v1${path}`, {
    method,
    headers: {
      ...(body && { 'Content-Type': 'application/json' }),
      ...(accessToken && { Authorization: `Bearer ${accessToken}` }),
    },
    body: body && JSON.stringify(body),
  }).then(async (r) => {
    const data = await r.json().catch(() => null);
    if (!r.ok) throw Object.assign(new Error(data?.message), { code: data?.code, status: r.status });
    return data;
  });
```

**토큰 수명**

| 토큰 | 수명 | 저장 위치 |
|---|---|---|
| accessToken | 1시간 | 메모리 권장 |
| refreshToken | 30일 | 안전한 저장소 (앱 보안 저장소 / httpOnly 쿠키) |

액세스 토큰이 만료되면 `401`이 옵니다. 이때 `POST /auth/refresh`로 갱신하고 원래 요청을 한 번 재시도하세요.
**갱신하면 refreshToken도 함께 새 값으로 바뀝니다(회전).** 반드시 새 값으로 교체해 저장해야 합니다 —
이미 쓴 refreshToken을 다시 보내면 탈취로 간주해 **그 계정의 모든 세션이 끊깁니다.**

> `X-Player-Id`는 가명 식별자만 알면 누구나 그 플레이어로 행세할 수 있어 인증이 아닙니다.
> 기존 화면이 아직 쓰고 있어 열어 두었을 뿐이고, `r2d.auth.allow-legacy-player-id-header: false`로 끕니다.
> 실제 서비스 전에는 반드시 꺼야 합니다.

> CORS 허용 출처를 바꾸려면 `application.yml`의 `r2d.web.allowed-origins`를 수정하세요.

### 오류 형식

모든 오류는 동일한 모양입니다.

```json
{ "code": "DECK_SIZE", "message": "덱은 카드 3장으로 편성해야 합니다.", "timestamp": "2026-08-04T08:54:32.078189400Z" }
```

| code | HTTP | 의미 |
|---|---|---|
| `MISSING_REQUIRED_INPUT` | 400 | `X-Player-Id` 등 필수 헤더/파라미터 누락 |
| `VALIDATION_FAILED` | 400 | 요청 본문 검증 실패 |
| `DECK_SIZE` / `DECK_DUPLICATE` | 400 | 덱이 3장이 아니거나 중복 |
| `IDEMPOTENCY_KEY_REQUIRED` | 400 | 배치에 `idempotencyKey` 없음 |
| `BOUNDS_TOO_LARGE` / `INVALID_BOUNDS` | 400 | 지도 조회 영역이 과대하거나 잘못됨 |
| `INVALID_EMAIL` / `WEAK_PASSWORD` | 400 | 가입 입력 오류 |
| `UNAUTHENTICATED` | 401 | 토큰 없음 |
| `UNAUTHORIZED` | 401 | 토큰 만료·위조 → `/auth/refresh` 시도 |
| `INVALID_CREDENTIALS` | 401 | 이메일 또는 비밀번호 불일치 |
| `INVALID_REFRESH_TOKEN` | 401 | 리프레시 토큰 만료·폐기 → 재로그인 |
| `REFRESH_TOKEN_REUSED` | 401 | 사용된 토큰 재사용 → 전 세션 종료, 재로그인 |
| `ACCOUNT_LOCKED` | 429 | 로그인 실패 10회 → 15분 잠금 |
| `PLAYER_NOT_FOUND` / `RIDE_NOT_FOUND` / `PACK_NOT_FOUND` | 404 | 대상 없음(타인 리소스 포함) |
| `NICKNAME_TAKEN` / `EMAIL_TAKEN` | 409 | 닉네임·이메일 중복 |
| `RIDE_NOT_ACTIVE` | 409 | 이미 정산된 주행에 데이터 추가 시도 |
| `PACK_ALREADY_OPENED` | 409 | 팩 중복 개봉 |

> **`null` 필드는 응답에서 생략됩니다** (`non_null` 직렬화). 예: 주행 중인 세션에는 `endedAt`이 없습니다.
> 프론트에서는 `?.`와 기본값으로 방어하세요.

---

## 1. 화면별 호출 순서

기획서 플로우(①~⑧)에 대응합니다.

```
① 미션 선택        GET  /regions/{code}/missions
                   GET  /regions/{code}/boss
② 미션 상세        POST /decks/preview          (덱 3장 고를 때마다)
③ 주행 시작        POST /rides                  → rideId 저장
④ 주행 중          POST /rides/{rideId}/batches (N초마다, 화면 꺼짐 상태)
⑥ 주행 종료        POST /rides/{rideId}/finish  → 정산 결과
⑦ 데미지 임팩트    (⑥ 응답의 damage / cellLines 로 연출)
⑧ 라이더 팩        GET  /packs/pending → POST /packs/{packId}/open
```

앱이 재시작되면 `GET /rides/active`로 진행 중인 주행을 이어받습니다.

---

## 2. 인증

### `POST /auth/signup` — 가입

```json
// 요청
{ "email": "rider@example.com", "password": "supersecret1", "nickname": "동탄라이더" }
```
```json
// 응답 200 — 가입과 동시에 로그인 상태가 됩니다
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "NplO4qHoKx9ka2F-_nE9...",
  "expiresInSeconds": 3600,
  "tokenType": "Bearer",
  "playerPublicId": "545ec266-9851-467b-ba6e-6a9d2cf92889",
  "nickname": "동탄라이더"
}
```

비밀번호 8자 이상. 이메일은 대소문자를 구분하지 않습니다(`A@b.com` = `a@b.com`).

### `POST /auth/login` — 로그인

```json
{ "email": "rider@example.com", "password": "supersecret1" }
```

응답은 signup과 동일합니다.

**이메일이 없을 때와 비밀번호가 틀렸을 때 응답이 완전히 같습니다**(`INVALID_CREDENTIALS`).
"가입되지 않은 이메일입니다" 같은 안내를 클라이언트에서 만들어 붙이지 마세요 — 가입 여부가 새어 나갑니다.

### `POST /auth/refresh` — 토큰 갱신

```json
{ "refreshToken": "NplO4qHoKx9ka2F-_nE9..." }
```

응답은 signup과 동일하며 **refreshToken이 새 값으로 바뀝니다.** 반드시 교체 저장하세요.

### `POST /auth/logout`

```json
{ "refreshToken": "..." }
```

이 기기의 refreshToken만 폐기합니다(다른 기기 로그인은 유지). accessToken은 만료까지 유효하니 앱에서도 지워야 합니다.

### `POST /auth/password` — 비밀번호 변경 (인증 필요)

```json
{ "currentPassword": "supersecret1", "newPassword": "brandnewsecret" }
```

변경하면 **모든 기기의 세션이 끊깁니다.**

### `GET /auth/me` — 세션 확인 (인증 필요)

```json
{ "playerPublicId": "545ec266-...", "nickname": "동탄라이더",
  "email": "rider@example.com", "emailVerified": false, "lastLoginAt": "2026-08-09T01:15:22Z" }
```

이메일은 **본인 조회에만** 나옵니다. `GET /players/{publicId}`에는 절대 포함되지 않습니다.

> 인증 메일은 현재 발송하지 않습니다. `emailVerified`는 항상 `false`이며 로그인을 막지 않습니다.

---

## 3. 플레이어

### `POST /players` — 게스트 등록 (레거시)

계정 없이 플레이어만 만듭니다. 기존 화면 호환용이며, 신규 가입은 `POST /auth/signup`을 쓰세요.

```json
// 요청
{ "nickname": "김OO" }
```
```json
// 응답 200
{ "publicId": "b7b1eac9-7f1f-40a1-a0e3-54182d761a90", "nickname": "김OO", "nicknamePublic": false }
```

`publicId`를 저장하세요. 이후 모든 요청의 신원입니다. 닉네임은 기본 **비공개**입니다.

### `GET /players/me` — 내 프로필

```json
{
  "player": { "publicId": "b7b1...", "nickname": "김OO", "nicknamePublic": false },
  "xp": 16, "coins": 4, "level": 1,
  "regionContributions": [
    { "regionCode": "DONGTAN2", "points": 15,
      "note": "캠페인 진행도입니다. 차감·양도·현금화되지 않습니다." }
  ],
  "todayUsage": { "packs": 1, "xp": 16, "regionContributionPoints": 15 }
}
```

`todayUsage`는 일일 상한 대비 사용량입니다(기본 팩 5 / XP 3000 / 지역기여 500).

### `GET /players/me/cards` — 보유 카드

```json
[
  { "cardCode": "EN_LONGHAUL_3", "name": "장거리 주행", "line": "지구", "tier": 3, "locked": false },
  { "cardCode": "SC_REVISIT_3", "name": "재탐사 기록", "line": "정찰", "tier": 3, "locked": false }
]
```

`locked: true`는 이상 후보를 발견했지만 아직 교차검증이 끝나지 않은 **기념 카드**입니다.
`sourceCellId`가 함께 옵니다. 잠금 상태로 표시하세요.

### 기타

| 메서드 | 경로 | 설명 |
|---|---|---|
| `PATCH` | `/players/me/nickname-visibility` | `{"nicknamePublic": true}` — 닉네임 공개 동의 |
| `GET` | `/players/{publicId}` | 타인 공개 정보. `displayName`은 미동의 시 `"익명 라이더"` |

---

## 4. 출발 전 — 미션 / 보스 / 덱

### `GET /regions/{code}/missions` — 오늘의 미션

지역 코드: `DONGTAN2`(동탄2동), `YEONGTONG`(영통동), `GWANGGYO`(광교동)

```json
[{
  "code": "A",
  "title": "미탐사 구간 정찰",
  "summary": "아직 아무도 기록하지 않은 4개 구간이 남아 있습니다.",
  "type": "EXPLORE",
  "targetCellCount": 4,
  "estimatedMinutesMin": 20, "estimatedMinutesMax": 35,
  "recommendedLine": "TRAILBLAZE",
  "rewardSummary": "탐사 팩 등급 하한 상승 · 신규 기여 배율 최대",
  "sampleCellIds": ["C82799_224458", "C82801_224458", "C82802_224458", "C82803_224458"],
  "safetyNote": "처음 가는 길입니다. 경로를 미리 확인하고, 주행 중에는 화면을 보지 마세요."
}]
```

`type`은 `EXPLORE` / `UPDATE` / `VERIFY` / `BOSS`. `code`는 `A`/`B`/`V`/`C`.
미션 수는 지역 상태에 따라 **달라집니다**(정화될수록 줄어듦). `BOSS`는 항상 포함됩니다.
`safetyNote`는 반드시 함께 노출해 주세요.

### `GET /regions/{code}/boss` — 지역 보스

```json
{
  "id": 1, "regionCode": "DONGTAN2", "name": "동탄2동 오염원",
  "maxHp": 20000, "currentHp": 20000,
  "phase": 1, "phaseCount": 4, "progressRatio": 0,
  "tier": "지역 공동형", "status": "진행 중",
  "dataNeed": 12, "participants": 0,
  "note": "체력은 이 지역에 남은 데이터 수요에서 계산됩니다. 활성 라이더 수로 나누지 않습니다."
}
```

체력바는 `phaseCount`만큼 나눠 그리고, 현재 단계는 `phase`입니다.

### `GET /cards` — 카드 도감

```json
{ "code": "TB_PATH_3", "name": "미개척 경로", "line": "개척", "tier": 3,
  "description": "처음 가는 길과 원정 라이딩에서 신규 기여 배율이 크게 올라갑니다.",
  "commemorative": false }
```

`line`: `개척`(신규) / `정찰`(갱신) / `지구`(거리) / `검증`(검증). `tier` 1~5.

### `POST /decks/preview` — 덱 미리보기

```json
// 요청
{ "cardCodes": ["TB_PATH_3", "SC_REVISIT_3", "VF_CHAIN_3"] }
```
```json
// 응답
{
  "cardCodes": ["TB_PATH_3", "SC_REVISIT_3", "VF_CHAIN_3"],
  "synergy": 1.05,
  "synergyLabel": "범용 덱 (정찰·개척·검증)",
  "contributionBonus": { "신규": 0.24, "검증": 0.24, "갱신": 0.24 },
  "enduranceBonus": 0,
  "note": "범용 덱입니다. 최대 배율은 낮지만 어떤 상태의 구간을 만나도 손해가 적습니다."
}
```

`synergy`: 같은 계열 3장 `1.30` / 2장 `1.15` / 혼합 `1.05`.
`note`를 그대로 "오늘 주행에 유리한 이유"로 쓰면 됩니다.

---

## 5. 주행

### `POST /rides` — 출발

```json
// 요청  (헤더: X-Player-Id)
{ "regionCode": "DONGTAN2", "deckCardCodes": ["TB_SCOUT_1", "TB_PATH_3", "TB_FRONTIER_5"] }
```
```json
// 응답
{
  "rideId": "f1ad8fa8-eadb-4591-8b94-64b6bc63b762",
  "regionCode": "DONGTAN2", "bossId": 1,
  "deckCardCodes": ["TB_SCOUT_1", "TB_PATH_3", "TB_FRONTIER_5"],
  "mode": "실제 주행", "status": "주행 중",
  "startedAt": "2026-08-04T08:54:31.386661300Z",
  "nextBatchSeq": 0
}
```

`deckCardCodes`는 3장이거나 빈 배열(중립 덱)이어야 합니다.
**덱은 출발 시에만 정할 수 있습니다** — 주행 중 변경 API는 없습니다.

### `POST /rides/{rideId}/batches` — 센서 업로드

주행 중 호출하는 **유일한** API입니다.

```json
{
  "batchSeq": 0,
  "idempotencyKey": "ride-f1ad-batch-0",
  "points": [
    { "epochMs": 1800000000000, "lat": 37.2300, "lon": 127.1200, "accuracyM": 8.0, "speedMps": 5.0 }
  ],
  "imuWindows": [
    { "epochMs": 1800000012000, "lat": 37.2305, "lon": 127.1200,
      "durationMs": 90, "peakG": 3.4, "rmsG": 0.5, "dominantHz": 26.0,
      "gyroRmsDps": 30.0, "entrySpeedMps": 4.5, "mountStable": true }
  ]
}
```
```json
// 응답 200
{ "accepted": true, "batchSeq": 0, "message": "위치 40건, IMU 윈도우 0건을 반영했습니다." }
```

**중요한 규칙:**

- `idempotencyKey`는 **필수**이고 전역에서 유일해야 합니다.
- 같은 배치를 재전송해도 **오류가 아닙니다.** `accepted: false`로 "이미 반영됨"을 알려 줍니다.
  오프라인 큐가 복구 후 재전송하는 것은 정상 동작이므로, `accepted: false`를 실패로 처리하지 마세요.
- `batchSeq`는 0부터 증가. 같은 seq도 중복으로 걸러집니다.
- `imuWindows`는 **원시 가속도가 아니라 앱이 추출한 파형 특징**입니다. 없으면 빈 배열로 보내세요
  (IMU 없이도 거리 기반 정산은 정상 동작합니다).
- 정산 후 업로드는 `409 RIDE_NOT_ACTIVE`.

### `GET /rides/active` — 진행 중 주행 이어받기

앱 재시작·강제 종료 후 복구용. 없으면 `404 NO_ACTIVE_RIDE`.

---

## 6. 정산 (결과 화면)

### `POST /rides/{rideId}/finish`

```json
// 요청 (선택)
{ "clientEstimatedDamage": 3000 }
```
```json
// 응답 200
{
  "rideId": "f1ad8fa8-...",
  "mode": "실제 주행",
  "distance": { "totalM": 195.15, "validM": 195.15, "pendingM": 0, "invalidM": 0 },
  "transport": {
    "mode": "자전거", "confidence": 0.7998,
    "reason": "평균 5.00m/s, 정지 비율 0.00, 노면 진동 0.00g로 자전거 주행에 부합합니다."
  },
  "damage": {
    "baseDamage": 195.15,
    "contributionMultiplier": 3.44,
    "deckSynergy": 1.3,
    "confidenceCoefficient": 0.9199,
    "finalDamage": 802.86,
    "appliedDamage": 802.86,
    "clientEstimate": 3000,
    "formula": "확정 피해 = 유효 거리 기반 피해 × 데이터 기여 배율 × 덱 시너지 × 신뢰도 계수"
  },
  "contribution": { "newCells": 5, "updatedCells": 0, "verifiedCells": 0, "invalidCells": 0 },
  "anomaly": { "discovered": 0, "confirmed": 0, "resolved": 0,
    "note": "발견 단계는 의심 상태이며, 확정은 교차검증이 끝난 뒤에만 일어납니다." },
  "reward": { "packGrade": "골드", "packId": 1, "xp": 16, "coins": 4,
    "regionContributionPoints": 15, "dailyCapApplied": false },
  "cellLines": [
    { "cellId": "C82888_224493", "state": "미조사", "contributionType": "신규", "validity": "유효",
      "distanceM": 5.00, "multiplier": 3.44, "damage": 20.59, "note": "미조사 구간 신규 기여" }
  ],
  "notes": [],
  "settledAt": "2026-08-04T08:54:31.821242600Z"
}
```

**연출 매핑:**

| 화면 요소 | 필드 |
|---|---|
| 큰 데미지 숫자 (`-8,420`) | `damage.finalDamage` |
| `CRITICAL x2 · COMBO x1.3` | `damage.contributionMultiplier`, `damage.deckSynergy` |
| 데미지 내역 리스트 | `cellLines[]` (`note`가 그대로 한 줄 설명) |
| 무효 구간 표시 | `distance.invalidM`, `contribution.invalidCells` |
| 서버 확정 배지 | `damage.appliedDamage` |
| 로컬 예상치 배지 | `damage.clientEstimate` |
| 하단 안내 문구 | `notes[]` (상한 도달·처치 완료 등) |

- `clientEstimatedDamage`는 주행 중 앱이 누적한 예상치입니다. **정산에는 쓰이지 않고** 비교 표시용으로만 되돌아옵니다.
- `finalDamage` ≠ `appliedDamage`인 경우: 보스 잔여 HP보다 피해가 커서 잘렸을 때.
- **재호출해도 안전합니다.** 같은 결과가 돌아오고 보상이 두 번 지급되지 않습니다.

### `GET /rides/{rideId}/settlement`

이미 정산된 주행의 결과를 다시 조회합니다(응답 형식 동일).

---

## 7. 탐사 팩

### `GET /packs/pending` → `POST /packs/{packId}/open`

```json
// pending — 개봉 전에는 내용물이 비어 있습니다
{ "id": 1, "rideId": "f1ad...", "grade": "골드",
  "gradeReason": "미탐사 셀 5개 확보 (개척 덱 보정으로 기준 2개)",
  "cardCodes": [], "coins": 0, "opened": false }
```
```json
// open — 개봉 후 공개
{ "id": 1, "grade": "골드",
  "gradeReason": "미탐사 셀 5개 확보 (개척 덱 보정으로 기준 2개)",
  "cardCodes": ["EN_LONGHAUL_3", "SC_REVISIT_3", "EN_LONGHAUL_3"],
  "coins": 350, "opened": true }
```

등급: `브론즈` / `실버` / `골드` / `시그니처`.
`gradeReason`은 "왜 이 등급이 나왔는지"의 근거이므로 팩 화면에 노출하세요.
중복 개봉은 `409 PACK_ALREADY_OPENED`.

일일 팩 상한(5개)에 걸리면 정산 응답의 `reward.packGrade`가 `null`이고 팩이 생성되지 않습니다.

### `GET /packs/odds` — 확률 공개표 (인증 불필요)

```json
{
  "등급결정방식": "신규·갱신·검증 기여가 등급 하한을 정하고, 하한 위에서만 15% 확률로 한 등급 승급합니다.",
  "주의": "카드와 게임 재화는 구매·양도·현금화할 수 없습니다.",
  "등급별구성": {
    "브론즈": { "카드 장수": 2, "카드 티어 범위": "1~2", "코인": 100 },
    "골드":   { "카드 장수": 3, "카드 티어 범위": "2~4", "코인": 350 }
  }
}
```

확률형 요소가 있으므로 앱에서 항상 확인 가능한 위치에 두어야 합니다.

---

## 7-B. 보스 처치 보상

보스가 쓰러지면 **참여자 전원**에게 각자의 누적 기여 비율대로 보상이 만들어집니다.
단계를 넘길 때마다 중간 보상도 나옵니다.

> **막타 보너스가 아닙니다.** 마지막 일격을 넣은 사람에게 몰아주면 "누가 끝내느냐" 경쟁이
> 생기고, 그건 속도 경쟁과 무리한 주행으로 이어집니다. 기획서의
> "속도·막타보다 참여 구간 중심으로 보상을 배분"을 그대로 구현했습니다.

실제 측정값 (HP 5,000 보스, 두 명 참여):

| 라이더 | 주행 | 기여도 | 처치 보상 |
|---|---|---|---|
| 큰기여자 | 2.4km | 88.2% | XP +88 · 코인 +44 · 지역기여 +32 |
| 막타라이더 (마지막 일격) | 0.9km | 11.8% | XP +12 · 코인 +6 · 지역기여 +4 |

### 왜 즉시 지급되지 않는가

보스가 쓰러진 순간 요청을 보내고 있는 사람은 마지막 기여자 한 명뿐입니다. 나머지 참여자는
접속해 있지도 않습니다. 그래서 처치 시점에는 각자의 몫을 **계산해 남겨 두고**, 이용자가 앱을
열었을 때 수령합니다.

### `GET /boss-rewards/pending` — 수령 대기 목록

앱을 켤 때 호출해 알림을 띄우세요.

```json
[{
  "id": 12, "bossId": 1, "regionCode": "DONGTAN2",
  "kind": "보스 처치",              // 또는 "단계 돌파"
  "phase": 0,                      // 단계 돌파일 때만 1 이상
  "contributionRatio": 0.882,
  "contributedCells": 47,
  "xp": 88, "coins": 44, "regionContributionPoints": 32,
  "claimed": false,
  "reason": "동탄2동 오염원 처치 · 내 기여도 88.2%",
  "createdAt": "2026-08-09T02:11:03Z"
}]
```

### `POST /boss-rewards/{rewardId}/claim` — 개별 수령

### `POST /boss-rewards/claim-all` — 일괄 수령

```json
{ "count": 4, "totalXp": 118, "totalCoins": 53,
  "totalRegionContributionPoints": 32, "rewards": [] }
```

수령해야 원장(XP·코인·지역기여)에 반영됩니다.
중복 수령은 `409 BOSS_REWARD_ALREADY_CLAIMED`, 남의 보상은 `404 BOSS_REWARD_NOT_FOUND`입니다.

**보스 보상에는 일일 상한이 적용되지 않습니다.** 상한은 무제한 반복 주행 경쟁을 막는 장치인데
보스는 지역당 한 번뿐이라 반복 파밍이 불가능합니다. 상한을 걸면 그날 많이 달린 사람이 오히려
보스 보상을 못 받는 이상한 결과가 됩니다.

### `GET /bosses/{bossId}` — 처치 화면용 보스 조회

**중요:** `GET /regions/{code}/boss`는 **진행 중인** 보스를 돌려줍니다. 처치가 끝나면
곧바로 다음 보스가 생성되므로, 방금 쓰러뜨린 보스를 보여주려면 정산 응답의 `bossId`로
이 API를 호출해야 합니다.

```json
{
  "id": 1, "name": "동탄2동 오염원",
  "maxHp": 5000, "currentHp": 0,
  "cleared": true, "status": "처치 완료",
  "participants": 2,
  "myContributionRatio": 0.882,
  "myRewards": [{ "rewardId": 12, "kind": "보스 처치", "xp": 88, "claimed": false }],
  "clearedAt": "2026-08-09T02:11:03Z"
}
```

### 연출 순서 제안

```
정산 화면(⑦) → settlement.notes 에 "처치 완료" 문구가 있으면
  → GET /bosses/{settlement.bossId}      보스 처치 연출 + 내 기여도
  → POST /boss-rewards/claim-all         보상 수령 연출
  → 팩 오프닝(⑧)
```

정산 응답에 `bossId`와 `regionCode`가 새로 포함됩니다.

---

## 8. 지도

### `GET /map/cells?minLat=&maxLat=&minLon=&maxLon=`

```json
{
  "cellId": "C82888_224493", "lat": 37.2298, "lon": 127.1198,
  "state": "낮은 신뢰도", "confidence": 0.5520,
  "observationCount": 1, "distinctObservers": 1,
  "lastObservedAt": "2026-08-04T08:54:31.608457Z",
  "bikeAccessible": true
}
```

`state` → 지도 색: `미조사` / `갱신 필요` / `낮은 신뢰도` / `보수 후 재확인` / `최근 조사됨`
`bikeAccessible: false`면 `exclusionReason`이 함께 옵니다(미션 후보 제외 사유).

### `GET /map/anomalies?minLat=&maxLat=&minLon=&maxLon=`

```json
{
  "cellId": "C82820_224447", "lat": 37.198, "lon": 127.094,
  "anomalyClass": "충격성 이상 후보", "state": "확정",
  "confidence": 0.804, "distinctObservers": 2,
  "usableForSafetyLayer": true,
  "note": "검증된 정보입니다. 위험 레이어와 안전 경로에 반영됩니다."
}
```

- `state`: `의심`(회색/반투명 몹) / `확정`(활성 몹) / `소멸`
- **`usableForSafetyLayer: true`인 것만 위험 레이어에 그리세요.** 의심 단계는 검증 전이라
  안전 경로 안내의 근거로 쓰면 안 됩니다.
- `anomalyClass`는 후보 분류입니다 — 포트홀·균열 같은 확정 결함명이 아닙니다. UI 문구도 후보로 유지해 주세요.

**두 지도 API 모두 bbox가 필수이고 한 변 0.05도(약 5.5km)로 제한됩니다.** 초과 시 `400 BOUNDS_TOO_LARGE`.

---

## 9. 심사 모드 (데모/시연 화면)

### `GET /judge/logs`

```json
[{ "logId": "A", "title": "신규 구간 기준 로그", "sourceType": "테스트 로그",
   "provenance": "테스트 로그 · 실측 로그 확보 전까지 쓰는 자리표시자 경로 정의입니다...",
   "purpose": "미탐사 셀 확보와 개척 덱 정산 확인",
   "regionCode": "DONGTAN2", "derived": false }]
```

### `POST /judge/replay`

```json
// 요청 (인증 불필요 — 심사용 가상 라이더가 자동 배정됩니다)
{ "logId": "A", "deckCardCodes": ["TB_SCOUT_1","TB_PATH_3","TB_FRONTIER_5"], "playbackSpeed": 30 }
```

응답에 `settlement`(위 정산 형식과 동일) + `overlay` + `checkpoints`가 들어옵니다.

```json
{
  "rideId": "...", "logId": "A",
  "provenance": "테스트 로그 · ...",
  "simulatedRider": "심사용 가상 라이더 A",
  "overlay": {
    "playbackSpeed": 30,
    "transport": { "mode": "BICYCLE", "confidence": 0.80, "reason": "..." },
    "frames": [
      { "epochMs": 1800000001000, "lat": 37.2005, "lon": 127.098,
        "cellId": "C82815_224446", "cellState": "UNSURVEYED",
        "gpsAccuracyM": 8.2, "speedMps": 5.0, "validity": "VALID",
        "cumulativeDistanceM": 5.0, "cumulativeEstimatedDamage": 10.0 }
    ],
    "detections": [
      { "cellId": "C82820_224447", "anomalyClass": "IMPACT_ANOMALY_CANDIDATE",
        "confidence": 0.6697, "reason": "지속 90ms · 정규화 피크 2.34g · 우세 26.00Hz의 충격성 파형입니다." }
    ]
  },
  "settlement": { "...정산 형식과 동일..." },
  "checkpoints": ["로그 출처: ...", "ride_id ... 기준으로 ..."],
  "banner": "이 화면은 심사용 분석 화면입니다. 실제 주행 중에는 이 정보를 표시하지 않습니다."
}
```

`overlay.frames`를 `playbackSpeed`에 맞춰 애니메이션하면 30배속 재생 화면이 됩니다.
**`banner`를 반드시 화면에 노출하세요** — 실제 주행 화면과 구분되어야 합니다.

시연 순서: `A` → `B` → `C1` → `C2` → `D`×3 → `E`

---

## 10. 프론트에서 자주 실수하는 지점

1. **`accepted: false`를 에러로 처리하지 마세요.** 재전송 중복은 정상입니다.
2. **`idempotencyKey`를 매 요청 새로 만들지 마세요.** 재시도 시 같은 키를 보내야 중복이 걸러집니다.
   `${rideId}-${batchSeq}` 조합을 권장합니다.
3. **주행 중 정산/보상/팩 API를 호출하지 마세요.** 서버가 거부하며, 애초에 화면을 보지 않는 설계입니다.
4. **`null` 필드는 생략됩니다.** `endedAt`, `packGrade`, `exclusionReason` 등은 없을 수 있습니다.
5. **의심 상태 이상은 안전 경로에 쓰지 마세요.** `usableForSafetyLayer`만 신뢰하세요.
6. **속도 관련 랭킹 UI를 만들지 마세요.** 서버가 속도 기반 지표를 제공하지 않습니다(안전 설계).
7. `finalDamage` 등 수치는 `double`입니다. 화면 표시 전에 반올림하세요.
8. **refreshToken을 재사용하지 마세요.** 갱신할 때마다 새 값으로 바뀝니다. 옛 값을 다시 보내면
   탈취로 간주해 그 계정의 **모든 세션이 끊깁니다.** 갱신 요청이 동시에 두 번 나가지 않도록
   진행 중인 갱신 Promise를 공유하세요.

```js
let refreshing = null;
const refreshOnce = () =>
  (refreshing ??= api('/auth/refresh', { method: 'POST', body: { refreshToken } })
    .finally(() => { refreshing = null; }));
```

9. **로그인 실패 사유를 추측해 보여주지 마세요.** 서버가 일부러 구분하지 않습니다.

---

## 11. 빠른 확인용 curl

```bash
curl -s -X POST localhost:8080/api/v1/players -H 'Content-Type: application/json' -d '{"nickname":"테스터"}'
```

```bash
curl -s -X POST localhost:8080/api/v1/judge/replay -H 'Content-Type: application/json' -d '{"logId":"A","deckCardCodes":[],"playbackSpeed":30}'
```
