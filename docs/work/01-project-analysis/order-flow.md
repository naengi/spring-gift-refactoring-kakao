# 주문 흐름 분석

> 기준 코드: `gift.order.OrderController#createOrder` (`POST /api/orders`)

## 요청 / 응답

```
POST /api/orders
Authorization: Bearer {JWT}
Content-Type: application/json

{ "optionId": 1, "quantity": 2, "message": "생일 축하해!" }
```

| 필드 | 검증 | 비고 |
|---|---|---|
| `optionId` | `@NotNull` | 주문 대상은 상품이 아니라 **옵션** |
| `quantity` | `@Min(1)` | |
| `message` | 없음 | 선택값, 카카오 메시지에 포함 |

성공 시 `201 Created`, `Location: /api/orders/{id}`, 본문은 `OrderResponse(id, optionId, quantity, orderDateTime, message)`.

## 처리 순서

```
Client ──▶ OrderController.createOrder
  1. 인증        AuthenticationResolver.extractMember(Authorization)
                 └ 실패/회원 없음 → 401
  2. 옵션 조회    optionRepository.findById(optionId)
                 └ 없음 → 404
  3. 재고 차감    option.subtractQuantity(quantity) → optionRepository.save()
                 └ 재고 부족 → IllegalArgumentException
  4. 포인트 차감  price = product.price × quantity
                 member.deductPoint(price) → memberRepository.save()
                 └ 포인트 부족 → IllegalArgumentException
  5. 주문 저장    orderRepository.save(new Order(option, memberId, quantity, message))
                 └ orderDateTime = LocalDateTime.now()
  6. (위시 정리)  주석에만 존재, 구현 없음
  7. 카카오 알림  member.kakaoAccessToken 이 있으면 KakaoMessageClient.sendToMe()
                 └ 실패해도 예외 무시 (best-effort)
  ──▶ 201 Created
```

### 단계별 상세

1. **인증** — `Bearer ` 를 떼어낸 JWT에서 이메일을 꺼내 `MemberRepository.findByEmail` 로 회원을 찾는다. 어떤 예외든 `null` 로 삼켜서 401을 반환한다.
2. **옵션 조회** — `Option` 은 `Product` 를 `@ManyToOne` 으로 참조한다. 가격은 옵션이 아니라 상품(`Product.price`)에 있다.
3. **재고 차감** — `Option.subtractQuantity` 가 `amount > quantity` 이면 예외를 던진다.
4. **포인트 차감** — `Member.deductPoint` 가 0 이하 금액이나 잔액 부족 시 예외를 던진다. 결제 수단은 포인트뿐이다.
5. **주문 저장** — `Order` 는 `Option` 은 엔티티로, 회원은 `memberId`(primitive FK)로 들고 있다.
6. **위시 정리** — `WishRepository` 가 주입되어 있지만 사용하지 않는다.
7. **카카오 알림** — 카카오 로그인(`KakaoAuthController`) 시 저장된 access token으로 `kapi.kakao.com/v2/api/talk/memo/default/send`(나에게 보내기)를 호출한다. 템플릿: 상품명(옵션명), 수량, 총 금액, 메시지.

## 관련 클래스

| 클래스 | 역할 |
|---|---|
| `OrderController` | 주문 생성·조회, 흐름 전체를 직접 조립 (서비스 계층 없음) |
| `OrderRequest` / `OrderResponse` | 요청·응답 DTO |
| `Order` | 주문 엔티티 (`orders` 테이블) |
| `Option` | 재고 보유, `subtractQuantity` |
| `Member` | 포인트·카카오 토큰 보유, `deductPoint` |
| `KakaoMessageClient` | 카카오 "나에게 보내기" 호출 |
| `AuthenticationResolver` | Authorization 헤더 → `Member` |

주문 조회(`GET /api/orders`)는 인증 후 `findByMemberId(memberId, pageable)` 로 본인 주문만 페이지 조회한다.

## 발견한 문제점

| # | 문제 | 영향 |
|---|---|---|
| 1 | **트랜잭션 없음** — `@Transactional` 이 프로젝트 어디에도 없고, 각 `save()` 가 개별 트랜잭션으로 커밋됨 | 재고 차감 후 포인트 부족으로 실패하면 **재고만 줄어든 채 남는다** |
| 2 | **주문 쪽 예외 처리 없음** — `OrderController` 에 `@ExceptionHandler` 가 없음 (Member/Option/Product 컨트롤러에는 각각 있음) | 재고·포인트 부족이 400이 아니라 **500**으로 응답 |
| 3 | **위시 정리 미구현** — 주석상 6단계지만 코드 없음, `WishRepository` 미사용 | 주문한 상품이 위시리스트에 그대로 남음 |
| 4 | **동시성 미고려** — 조회→차감→저장 사이에 잠금 없음 | 동시 주문 시 재고/포인트 초과 차감 가능 |
| 5 | **금액 `int` 계산** — `price * quantity` | 큰 수량에서 오버플로 → 음수 금액이면 `deductPoint` 에서 예외 |
| 6 | **카카오 실패 무시** — `catch (Exception ignored)` | 알림 실패를 추적할 수 없음 (로그 없음) |
| 7 | **"나에게 보내기"** — 받는 사람이 아니라 주문자 본인에게 발송 | "선물이 도착했어요" 문구와 실제 수신자가 다름 |
| 8 | **`Location` 대상 없음** — `/api/orders/{id}` 단건 조회 API가 없음 | 응답 헤더가 존재하지 않는 리소스를 가리킴 |
| 9 | **컨트롤러에 비즈니스 로직 집중** — 서비스 계층 없이 6개 의존성을 직접 조립 <br/>| 테스트·재사용·트랜잭션 경계 설정이 어려움 |



#### AdminProductManager
- /admin/products/{id}/edit -> 수정 동작인데 POST로 작동
- /admin/products/{id}/delete -> 삭제인데 POST로 작동

#### AdminMemberController
- /admin/members/{id}/edit -> 수정인데 POST로 작동
- /admin/members/{id}/charge-point -> 포인트 충전(PUT)인데 POST로 작동
-  /admin/members/{id}/delete -> 삭제인데 POST로 작동

#### Order
- 주문 할때 Transaction 누락으로 포인트 부족 시 재고만 줄어드는 문제 발생
- 위시리스트 정리 구현 X
- 카카오 메시지가 주문한 본인에게로 감 -> 받는 사람에게 가야함