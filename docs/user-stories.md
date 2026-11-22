
## 1. 사용자 스토리

현재 코드가 **실제로 어떻게 동작하는지**를 기준으로 정리했다. 의도와 다르게 동작하는 부분은 각 스토리의 "⚠️ 현재 동작"에 따로 적었다.
용어는 [용어 사전](glossary.md)의 통일할 이름(회원, 재고, 주문 금액, 선물 메시지, 카카오 메시지 등)을 따른다.

### 1-0. 공통 사항

| 항목 | 내용 |
|---|---|
| 인증 방식 | `Authorization: Bearer <서비스 토큰(JWT)>` 헤더. 위시·주문 API에만 필요 |
| 인증 실패 | 토큰이 잘못됐거나 만료됐거나 회원이 없으면 `401` (본문 없음) |
| 헤더 누락 | `Authorization` 헤더 자체가 없으면 `401`이 아니라 Spring 기본 `400` |
| 입력값 검증 실패 (`@Valid`) | Spring 기본 에러 JSON과 함께 `400` (`{ timestamp, status, error, path }`) |
| 비즈니스 규칙 위반 | 회원·상품·옵션 API는 `400` + 에러 메시지 문자열. 주문 API는 핸들러가 없어서 `500` |
| 페이징 | 목록 API는 `?page=0&size=20&sort=필드,asc` 형식을 쓰고, 응답은 Spring `Page` JSON(`content`, `totalElements`, `totalPages`, `number`, `size` …) |
| 권한 구분 | 관리자/일반 회원 구분이 없다. 상품·카테고리·옵션 쓰기 API와 `/admin/**` 화면은 인증 없이 누구나 쓸 수 있다 |

테스트용 기본 데이터(`V2__Insert_default_data.sql`): 회원 `admin@example.com`, `user1@example.com`, `user2@example.com` (포인트 1,000만 / 500만 / 300만), 카테고리 3개, 상품 6개와 옵션, 위시 4건.

---

### 1-1. 인증

#### US-A1. 이메일로 회원 가입한다

> 회원으로서, 이메일과 비밀번호로 가입하고 바로 서비스 토큰을 받고 싶다.

**흐름**
1. 회원이 이메일·비밀번호를 보낸다.
2. 이미 가입된 이메일인지 확인한다.
3. 회원을 저장하고(포인트 0) 서비스 토큰을 발급한다.

| API | 요청 |
|---|---|
| `POST /api/members/register` | `{ "email": "a@b.com", "password": "1234" }` |

| 상황 | 응답 |
|---|---|
| 성공 | `201` `{ "token": "<JWT>" }` |
| 이메일 형식 오류 / 빈 값 | `400` (Spring 기본 에러) |
| 이미 가입된 이메일 | `400` `"Email is already registered."` |

⚠️ 현재 동작: 비밀번호를 암호화하지 않고 평문으로 저장한다.

#### US-A2. 이메일로 로그인한다

> 회원으로서, 이메일과 비밀번호로 로그인해서 서비스 토큰을 받고 싶다.

| API | 요청 |
|---|---|
| `POST /api/members/login` | `{ "email": "user1@example.com", "password": "password1" }` |

| 상황 | 응답 |
|---|---|
| 성공 | `200` `{ "token": "<JWT>" }` |
| 없는 이메일 / 비밀번호 불일치 | `400` `"Invalid email or password."` |
| 카카오로만 가입한 회원 (비밀번호 없음) | `400` `"Invalid email or password."` |

⚠️ 현재 동작: 로그인 후 이동할 화면이 없다. 토큰은 클라이언트가 직접 보관해야 한다.

#### US-A3. 카카오 계정으로 로그인한다

> 회원으로서, 카카오 계정으로 로그인하고 싶다. 처음이면 자동으로 가입되면 좋겠다.

**흐름**
1. 브라우저로 `/api/auth/kakao/login`에 접속한다.
2. 서버가 카카오 인가 페이지로 리다이렉트한다(scope: `account_email`, `talk_message`).
3. 회원이 카카오에서 동의하면 카카오가 `/api/auth/kakao/callback?code=...`로 돌려보낸다.
4. 서버가 인가 코드로 카카오 액세스 토큰을 받고, 이메일을 조회한다.
5. 같은 이메일의 회원이 없으면 비밀번호 없는 회원으로 **자동 가입**시킨다.
6. 카카오 액세스 토큰을 회원에 저장하고(카카오 메시지 발송용) 서비스 토큰을 발급한다.

| API | 요청 | 응답 |
|---|---|---|
| `GET /api/auth/kakao/login` | – | `302` → `https://kauth.kakao.com/oauth/authorize?...` |
| `GET /api/auth/kakao/callback` | `?code=<인가 코드>` | `200` `{ "token": "<JWT>" }` |

| 상황 | 응답 |
|---|---|
| 카카오 토큰/회원 정보 요청 실패 | `500` |
| `kakao.login.client-id` 미설정 | 카카오 인가 페이지에서 오류 |

⚠️ 현재 동작: 콜백 결과가 JSON 그대로 브라우저에 보인다. 이메일로 가입한 회원이 같은 이메일로 카카오 로그인하면 기존 계정에 연결된다.

---

### 1-2. 상품

#### US-P1. 상품 목록을 둘러본다

> 회원으로서, 판매 중인 상품을 페이지 단위로 보고 싶다.

| API | 요청 | 응답 |
|---|---|---|
| `GET /api/products` | `?page=0&size=20&sort=price,desc` | `200` `Page<{ id, name, price, imageUrl, categoryId }>` |

인증이 필요 없다. 카테고리 이름 대신 `categoryId`만 내려주므로, 이름은 아래 API로 따로 조회해야 한다.

#### US-P2. 카테고리 목록을 본다

| API | 응답 |
|---|---|
| `GET /api/categories` | `200` `[{ id, name, color, imageUrl, description }]` (페이징 없음) |

⚠️ 현재 동작: 카테고리별 상품 조회 API는 없다.

#### US-P3. 상품 상세와 옵션을 확인한다

> 회원으로서, 상품 하나를 골라 어떤 옵션(색상·사양)이 있고 재고가 얼마나 남았는지 보고 싶다.

| API | 응답 |
|---|---|
| `GET /api/products/{id}` | `200` `{ id, name, price, imageUrl, categoryId }` / `404` |
| `GET /api/products/{id}/options` | `200` `[{ id, name, quantity }]` / `404` (상품 없음) |

주문할 때는 여기서 확인한 **옵션 ID**를 쓴다(US-O1).

#### US-P4. 상품을 등록한다 (판매자/운영자)

> 운영자로서, 상품을 등록하고 옵션을 붙여 판매할 수 있게 하고 싶다.

**흐름**: 상품과 옵션은 **따로** 등록한다.
1. 카테고리가 있는지 확인한다. 없으면 먼저 만든다 (`POST /api/categories`).
2. 상품(이름·가격·이미지 URL·카테고리 ID)을 등록한다.
3. 받은 상품 ID로 옵션을 하나씩 추가한다.

| 단계 | API | 요청 | 응답 |
|---|---|---|---|
| 카테고리 생성 | `POST /api/categories` | `{ name, color, imageUrl, description? }` | `201` |
| 상품 등록 | `POST /api/products` | `{ name, price, imageUrl, categoryId }` | `201` + `Location: /api/products/{id}` |
| 옵션 추가 | `POST /api/products/{id}/options` | `{ name, quantity }` | `201` |

| 상황 | 응답 |
|---|---|
| 상품명 규칙 위반 (15자 초과, 허용 외 특수문자, "카카오" 포함) | `400` + 위반 메시지들을 `, `로 이어 붙인 문자열 |
| 가격 0 이하 / 빈 값 | `400` (Spring 기본 에러) |
| 없는 카테고리 ID | `404` |
| 옵션명 규칙 위반 (50자 초과, 허용 외 특수문자) | `400` |
| 재고 1 미만 또는 99,999,999 초과 | `400` (Spring 기본 에러) |
| 같은 상품에 같은 옵션명 | `400` `"이미 존재하는 옵션명입니다."` |

관리자 화면에서도 등록할 수 있다: `/admin/products/new` → `POST /admin/products` → `/admin/products`로 리다이렉트. 검증에 실패하면 같은 폼을 에러 메시지와 함께 다시 보여준다.

⚠️ 현재 동작
- 옵션이 없는 상품도 만들 수 있고, 그런 상품은 주문할 수 없다.
- 관리자 화면에는 옵션 입력 기능이 없다.
- 관리자 화면에서는 "카카오"가 들어간 상품명이 허용된다(API는 거부).
- 관리자 화면에서 없는 카테고리 ID를 보내면 `500`.

#### US-P5. 상품·옵션을 수정하거나 삭제한다 (판매자/운영자)

| API | 요청 | 응답 |
|---|---|---|
| `PUT /api/products/{id}` | `{ name, price, imageUrl, categoryId }` | `200` / `400` / `404` |
| `DELETE /api/products/{id}` | – | `204` |
| `DELETE /api/products/{id}/options/{optionId}` | – | `204` / `404` |
| `PUT /api/categories/{id}` | `{ name, color, imageUrl, description? }` | `200` / `404` |
| `DELETE /api/categories/{id}` | – | `204` |

| 상황 | 응답 |
|---|---|
| 옵션이 1개뿐인 상품의 옵션 삭제 | `400` `"옵션이 1개인 상품은 옵션을 삭제할 수 없습니다."` |
| 다른 상품의 옵션 ID | `404` |

⚠️ 현재 동작
- 위시·옵션·주문이 연결된 상품을 삭제하면 외래키 제약 때문에 `500`.
- 없는 상품 ID로 삭제해도 `204`(상품 쪽 기준).
- 옵션 수정 API는 없다.

---

### 1-3. 위시리스트

#### US-W1. 마음에 드는 상품을 찜한다

> 회원으로서, 나중에 사려고 상품을 위시리스트에 담고 싶다.

**흐름**
1. 토큰으로 회원을 확인한다.
2. 상품이 있는지 확인한다.
3. 이미 찜한 상품이면 새로 만들지 않고 기존 위시를 돌려준다.
4. 아니면 위시를 저장한다.

| API | 요청 |
|---|---|
| `POST /api/wishes` 🔒 | `{ "productId": 3 }` |

| 상황 | 응답 |
|---|---|
| 새로 찜함 | `201` `{ id, productId, name, price, imageUrl }` + `Location: /api/wishes/{id}` |
| 이미 찜한 상품 | `200` + 기존 위시 |
| 토큰 오류 | `401` |
| 없는 상품 | `404` |

⚠️ 현재 동작: 중복 방지가 코드에서만 이루어지고 DB 유니크 제약은 없다. 동시에 요청하면 중복이 생길 수 있고, 중복이 생긴 뒤에는 그 상품을 찜할 때마다 `500`.

#### US-W2. 내 위시리스트를 본다

| API | 요청 | 응답 |
|---|---|---|
| `GET /api/wishes` 🔒 | `?page=0&size=20` | `200` `Page<{ id, productId, name, price, imageUrl }>` / `401` |

본인 위시만 조회된다. 옵션과 재고 정보는 들어 있지 않다.

#### US-W3. 위시를 삭제한다

| API | 응답 |
|---|---|
| `DELETE /api/wishes/{id}` 🔒 | `204` / `401` / `404` / `403` |

| 상황 | 응답 |
|---|---|
| 성공 | `204` |
| 없는 위시 ID | `404` |
| 다른 회원의 위시 | `403` |

`{id}`는 **상품 ID가 아니라 위시 ID**다.

---

### 1-4. 주문

#### US-O1. 상품을 주문(선물)한다

> 회원으로서, 상품의 옵션과 수량을 골라 포인트로 결제하고 선물 메시지를 남기고 싶다.

**사전 조건**
- 로그인해서 서비스 토큰이 있다 (US-A2, US-A3).
- 주문할 옵션 ID를 안다 (US-P3).
- 포인트가 충분하다. 회원이 직접 충전하는 API는 없고, 관리자 화면(`/admin/members`의 "충전")에서만 충전된다.

**흐름**
1. 토큰으로 회원을 확인한다.
2. 옵션이 있는지 확인한다.
3. 옵션 **재고를 차감**하고 바로 저장한다.
4. **주문 금액**(상품 단가 × 주문 수량)만큼 **포인트를 차감**하고 저장한다.
5. 주문을 저장한다.
6. 카카오로 로그인한 회원이면 카카오 메시지를 보낸다("🎁 선물이 도착했어요!", 상품·옵션·수량·금액·선물 메시지). 실패해도 주문은 유지된다.

| API | 요청 |
|---|---|
| `POST /api/orders` 🔒 | `{ "optionId": 1, "quantity": 2, "message": "생일 축하해!" }` (`message`는 선택) |

| 상황 | 응답 |
|---|---|
| 성공 | `201` `{ id, optionId, quantity, orderDateTime, message }` + `Location: /api/orders/{id}` |
| 토큰 오류 | `401` |
| 수량 1 미만 / `optionId` 누락 | `400` (Spring 기본 에러) |
| 없는 옵션 | `404` |
| 재고 부족 | `500` (내부 메시지: "차감할 수량이 현재 재고보다 많습니다.") |
| 포인트 부족 | `500` (내부 메시지: "포인트가 부족합니다.") |

⚠️ 현재 동작
- **트랜잭션이 없다.** 포인트가 부족해서 4단계에서 실패해도 3단계에서 차감한 재고는 되돌아오지 않는다.
- 재고·포인트 부족처럼 사용자 잘못인 경우에도 `400`이 아니라 `500`이 나간다.
- 카카오 메시지는 "나에게 보내기" API로 **주문한 본인**에게 간다. 받는 사람을 지정할 수 없다.
- 주석에는 "주문 후 위시 정리"가 있지만 구현되지 않아서, 주문한 상품이 위시에 남는다.
- `Location`이 가리키는 `GET /api/orders/{id}`는 없다(`404`).

#### US-O2. 내 주문 내역을 본다

| API | 요청 | 응답 |
|---|---|---|
| `GET /api/orders` 🔒 | `?page=0&size=20&sort=orderDateTime,desc` | `200` `Page<{ id, optionId, quantity, orderDateTime, message }>` / `401` |

본인 주문만 조회된다. 응답에는 `optionId`만 있고 상품명·금액은 없다.

---

### 1-5. 시나리오: 처음 온 회원이 선물하기까지

```
[가입/로그인]   POST /api/members/register  또는  GET /api/auth/kakao/login → callback
                 └─▶ { token }
[상품 탐색]     GET /api/products → GET /api/products/{id} → GET /api/products/{id}/options
[찜하기]        POST /api/wishes { productId }               (선택)
[포인트 충전]   관리자가 /admin/members 에서 충전           (회원용 API 없음)
[주문]          POST /api/orders { optionId, quantity, message }
                 ├─▶ 재고 차감 → 포인트 차감 → 주문 저장
                 └─▶ (카카오 회원) 카카오 메시지 발송
[확인]          GET /api/orders, GET /api/wishes             (주문한 상품이 위시에 그대로 남음)
```

---

## 인수 테스트 

각 스토리를 Given-When-Then으로 정리했다. 리팩터링 후에도 지켜야 할 **목표 동작** 기준이다.
현재 코드와 결과가 다른 시나리오는 "🚧 현재 코드와 불일치"로 표시했다(코드는 이번에 수정하지 않음).

### 1. 회원이 옵션을 골라 포인트로 결제하고 주문한다 (US-O1)

#### 시나리오 1. 주문하면 재고가 주문 수량만큼 줄어든다

- **Given** 옵션의 재고가 15개이고, 회원의 포인트는 주문 금액보다 많다
- **When** 회원이 그 옵션을 1개 주문한다 (`POST /api/orders` `{ optionId, quantity: 1 }`)
- **Then** `201 Created`와 함께 주문이 생성된다
- **And** 옵션의 재고는 14개가 된다

#### 시나리오 2. 주문하면 포인트가 주문 금액만큼 줄어든다

- **Given** 상품 가격이 15,000원이고, 그 상품 옵션의 재고가 1개 이상이다
- **And** 회원의 포인트가 20,000이다
- **When** 회원이 그 옵션을 1개 주문한다
- **Then** `201 Created`와 함께 주문이 생성된다
- **And** 회원의 포인트는 5,000이 된다

#### 시나리오 3. 포인트가 부족하면 결제가 거절된다

- **Given** 주문 금액이 15,000원이다
- **And** 회원의 포인트가 10,000이다
- **When** 회원이 주문한다
- **Then** `400 Bad Request`로 결제가 거절된다
- **And** 주문은 생성되지 않는다

> 🚧 현재 코드와 불일치: `OrderController`에 예외 핸들러가 없어서 지금은 `500 Internal Server Error`가 반환된다.

#### 시나리오 4. 결제가 거절되면 재고는 차감되지 않는다

- **Given** 옵션의 재고가 15개이다
- **And** 주문 금액이 15,000원이고, 회원의 포인트가 10,000이다
- **When** 회원이 그 옵션을 주문한다
- **Then** 결제가 거절된다
- **And** 옵션의 재고는 15개 그대로다

> 🚧 현재 코드와 불일치: 트랜잭션이 없어서 재고 차감(`optionRepository.save`)이 포인트 차감보다 먼저 DB에 반영된다. 지금은 결제가 거절돼도 재고가 14개로 남는다.

### 2. 회원이 주문을 마친다 (US-O1, 주문 후 처리)

#### 시나리오 5. 주문을 마치면 상대방에게 카카오톡 메시지가 전송된다

- **Given** 회원이 카카오 로그인으로 서비스 토큰을 받았다
- **And** 회원이 선물 메시지를 입력했다
- **When** 회원이 받는 사람을 지정해 주문을 마친다 (`POST /api/orders` `{ optionId, quantity, message, receiverId }`, `receiverId`는 받는 사람의 회원 ID)
- **Then** `201 Created`와 함께 주문이 생성된다
- **And** 선물 받는 상대방에게 선물 메시지가 담긴 카카오톡 메시지가 전송된다

> 🚧 현재 코드와 불일치: `KakaoMessageClient`가 "나에게 보내기" API(`/v2/api/talk/memo/default/send`)를 써서 메시지가 **주문한 본인**에게 간다. `OrderRequest`에 받는 사람 필드(`receiverId`)도 없다.

### 3. 회원이 위시 아이템을 추가한다 (US-W1, US-W2)

#### 시나리오 6. 위시에 추가한 상품이 위시 목록에 보인다

- **Given** 상품이 하나 있다
- **And** 회원이 그 상품을 아직 위시에 담지 않았다
- **When** 회원이 그 상품을 위시에 추가한다 (`POST /api/wishes` `{ productId }`)
- **And** 회원이 위시 목록을 조회한다 (`GET /api/wishes`)
- **Then** 조회 결과(`content`)에 방금 추가한 상품이 포함되어 있다

### 4. 회원이 이메일로 로그인한다 (US-A2)

#### 시나리오 7. 이메일 형식이 아닌 값으로 로그인하면 400과 기본 에러가 응답된다

- **Given** 이메일 `test@gmail.com`, 비밀번호 `password123`으로 가입한 회원이 있다
- **When** 이메일 `test.com`, 비밀번호 `password123`으로 로그인한다 (`POST /api/members/login`)
- **Then** `400 Bad Request`가 응답된다
- **And** 응답은 Spring 기본 에러 JSON이고 `error`는 `"Bad Request"`다

#### 시나리오 8. 가입되지 않은 이메일로 로그인하면 400과 로그인 실패 메시지가 응답된다

- **Given** 이메일 `test@gmail.com`, 비밀번호 `password123`으로 가입한 회원이 있다
- **When** 이메일 `test2@gmail.com`, 비밀번호 `password123`으로 로그인한다 (`POST /api/members/login`)
- **Then** `400 Bad Request`가 응답된다
- **And** 응답 메시지는 `"Invalid email or password."`다
