# AGENTS.md

선물하기 서비스(Spring Boot 3.5, Java 21) 리팩터링 저장소. 코드를 읽으면 알 수 있는 내용은 적지 않는다. 여기에는 저장소에서 찾을 수 없거나 다시 찾는 데 오래 걸리는 것만 둔다.

## 참고 문서

| 문서 | 위치 | 용도 |
|---|---|---|
| 용어 사전 | [docs/glossary.md](docs/glossary.md) | 도메인 용어의 한글명·영문명. 코드·문서·커밋에서 이 이름을 쓴다 |
| 사용자 스토리 | [docs/user-stories.md](docs/user-stories.md) | 현재 동작(⚠️)과 리팩터링 후 지켜야 할 목표 동작(인수 테스트) |
| 확인 필요 목록 | [docs/open-questions.md](docs/open-questions.md) | 아직 결정되지 않은 동작·설계 질문 |

### 문서와 다른 것을 발견하면

작업 중 위 세 문서와 **다른 이름이나 작동**을 발견하면:

1. 코드나 문서를 임의로 맞추지 말고 먼저 사용자에게 묻는다. (무엇이, 어디서, 문서와 어떻게 다른지 함께 적는다)
2. 답에 따라 해당 문서(용어 사전 / 사용자 스토리 / 확인 필요 목록)를 고친다.
3. 답이 바로 나오지 않으면 확인 필요 목록에 추가해 둔다.

## 작업 확인 방법

Windows에서는 `gradlew.bat`, 그 외에는 `./gradlew`를 쓴다.

| 목적 | 명령 |
|---|---|
| 빌드 + 테스트 | `./gradlew build` |
| 테스트만 | `./gradlew test` |
| 린트 | `./gradlew ktlintCheck` (자동 수정: `ktlintFormat`) |
| 실행 | `./gradlew bootRun` → `http://localhost:8080` |

- 아직 테스트 코드가 하나도 없다(`src/test/*/gift/.gitkeep`만 있음). `test`가 통과해도 아무것도 검증하지 않은 것이다. 동작을 바꾸면 사용자 스토리의 인수 테스트를 기준으로 테스트를 먼저 만든다.
- ktlint는 Kotlin 파일만 검사한다. 현재 코드는 전부 Java라 `ktlintCheck`가 통과해도 Java 코드 스타일은 확인되지 않는다.
- DB 설정이 없어서 실행하면 인메모리 H2가 뜨고 Flyway가 `V1`(스키마), `V2`(기본 데이터)를 적용한다. 재시작하면 데이터가 초기화된다.
- 수동 확인용 계정은 `V2__Insert_default_data.sql`에 있다 (예: `user1@example.com` / `password1`).
- 카카오 로그인·메시지는 `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET` 환경 변수가 있어야 확인할 수 있다. 키 값은 커밋하지 않는다.

## 코드가 오해를 부르는 곳

- **Kotlin 프로젝트처럼 보이지만 코드는 Java다.** `build.gradle.kts`에 Kotlin·ktlint 플러그인이 있고 `src/main/kotlin`이 있지만 비어 있다. 실제 코드는 `src/main/java/gift`.
- **`Option.quantity`는 재고(stock)다.** 주문의 `quantity`(주문 수량)와 이름이 같다. 용어로는 "재고"라고 부른다. API 응답 `OptionResponse.quantity`도 재고다.
- **가격은 옵션이 아니라 상품(`Product.price`)에 있다.** 주문은 옵션 단위지만 주문 금액은 `상품 단가 × 주문 수량`이다.
- **`@Transactional`이 프로젝트 어디에도 없다.** 각 `save()`가 따로 커밋된다. 여러 엔티티를 바꾸는 흐름(특히 주문)은 중간에 실패해도 앞 단계가 되돌아가지 않는다.
- **예외 처리가 컨트롤러마다 따로 있다.** Member/Product/Option 컨트롤러에만 `@ExceptionHandler(IllegalArgumentException)`이 있고 Order에는 없어서, 같은 종류의 오류가 API에 따라 400 또는 500이 된다.
- **`KakaoMessageClient`는 `order` 패키지에 있고 "나에게 보내기"를 호출한다.** 이름과 "선물이 도착했어요" 문구와 달리 주문한 본인에게 간다.
- **토큰이 두 종류다.** 서비스가 발급하는 JWT(토큰)와 카카오에서 받은 카카오 액세스 토큰. 카카오 쪽은 항상 `kakao` 접두어를 붙인다.
- **API와 관리자 화면의 검증이 다르다.** 상품명의 "카카오"는 API(`ProductController`)에서는 거부되고 관리자 화면(`AdminProductController`)에서는 허용된다(`ProductNameValidator.validate(name, allowKakao)`).
- **관리자 화면에는 인증이 없다.** `/admin/**`과 상품·카테고리·옵션 쓰기 API는 누구나 호출할 수 있다. 관리자/회원 구분이 코드에 없다.
- **`OrderController`에 `WishRepository`가 주입돼 있지만 쓰지 않는다.** 주석의 "위시 정리"는 구현되지 않았다.
- **`Authorization` 헤더가 아예 없으면 401이 아니라 400**이다(Spring 기본 동작).

## 저장소에 없는 결정

- 사용자 스토리의 **인수 테스트가 리팩터링 목표 동작**이다. "🚧 현재 코드와 불일치" 표시는 고쳐야 할 대상이다.
  - 재고·포인트 부족 같은 사용자 잘못은 `400`으로 응답한다.
  - 결제(포인트 차감)가 거절되면 재고도 차감되지 않아야 한다.
  - 카카오 메시지는 주문자가 아니라 **선물 받는 사람**에게 가야 한다.
- 용어는 용어 사전을 따른다. 예: user/account가 아니라 **회원(Member)**, quantity가 아니라 **재고(stock)** / **주문 수량(quantity)**, 총액은 **주문 금액(totalPrice)**.
- 상품명에 "카카오"는 **담당 MD와 협의한 경우에만** 쓸 수 있다(관리자 화면에서 허용하는 이유).
- 상품에는 옵션이 **최소 1개** 있어야 한다. 현재 코드는 옵션 없는 상품 생성을 막지 않는다.
- 관리자 화면의 수정·삭제·충전이 POST로 되어 있는 것은 아직 바꿀지 정하지 않았다 → 확인 필요 목록.

## 두 번 이상 고친 것

아직 없다. 같은 곳을 두 번 이상 고치게 되면 무엇을, 왜 다시 고쳤는지 여기에 한 줄로 남긴다.
