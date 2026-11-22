---
name: refactor-style
description: 선물하기 서비스(src/main/java/gift) Java 코드를 리팩터링할 때 따르는 코드 스타일 기준. 기준 예시 코드(ProductController.getProduct), 이름·구조, 요청·예외 처리 통일, 용어 사전 준수, 한국어 주석, 메서드 뎁스 4·길이 15줄 제한, else·switch·3항 연산자 금지, 스타일 검토와 2회 이상 수정 시 보고 규칙을 담고 있다. 컨트롤러·서비스·리포지토리·DTO를 새로 쓰거나 고칠 때, 카테고리·상품·옵션·회원·위시·주문·관리자 영역을 정리할 때, 코드 스타일을 검토할 때 쓴다.
---

# 리팩터링 코드 스타일 기준

이 프로젝트는 선물하기 서비스를 빠르게 시장에 내놓으려고 시작했다. 그래서 영역마다 코드 모양이 제각각이다. 리팩터링의 목표는 **주석 없이도 읽히는 이름과 구조**, **일관된 요청과 예외 처리**다.

동작을 바꾸는 리팩터링이면 먼저 `acceptance-test` 스킬로 인수 테스트를 준비한다. 이 스킬은 코드 모양만 다룬다.

## 1. 기준 예시 코드

모든 코드는 아래 메서드의 모양을 기준으로 맞춘다.

```java
@GetMapping("/{id}")
public ResponseEntity<ProductResponse> getProduct(@PathVariable Long id) {
    Product product = productRepository.findById(id).orElse(null);
    if (product == null) {
        return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(ProductResponse.from(product));
}
```

이 코드에서 가져올 것:

- **조회 → 가드 → 정상 응답** 순서. 실패 조건은 `if`로 먼저 걸러 바로 반환하고, 정상 흐름은 들여쓰기 없이 맨 아래에 둔다.
- **이름만으로 역할이 드러난다.** `getProduct`, `product`, `ProductResponse.from(product)` — 주석이 필요 없다.
- **응답은 `ResponseEntity`로 상태 코드를 드러낸다.** 엔티티를 그대로 내보내지 않고 `XxxResponse.from(entity)`로 바꾼다.
- **없는 리소스는 `404 Not Found`**로 응답한다.
- `else` 없이 `if` + 이른 반환으로 끝낸다.

## 2. 이름과 구조

- 영역(카테고리, 상품, 옵션, 회원, 위시, 주문, 관리자)마다 같은 모양을 쓴다. 한 영역에서 정한 패턴(메서드 이름, 응답 DTO, 예외 처리)을 다른 영역에도 그대로 적용한다. 영역마다 다르게 하지 않는다.
- 메서드 이름은 `동사 + 도메인 용어`로 쓴다. 예: `getProduct`, `createOption`, `chargePoint`, `deductStock`.
- 요청 DTO는 `XxxRequest`, 응답 DTO는 `XxxResponse`이고 응답은 정적 팩터리 `from(entity)`로 만든다.
- 카테고리·상품·관리자 영역은 **기준을 분명히 한다.** API와 관리자 화면이 같은 검증을 쓰는지, 다르면 왜 다른지(예: 상품명 "카카오"는 담당 MD 협의 시에만 관리자 화면에서 허용) 이름이나 구조에서 드러나게 한다.

## 3. 요청과 예외 처리 — 일관되게

- 같은 종류의 오류는 어느 API에서나 같은 상태 코드를 준다. 지금은 컨트롤러마다 `@ExceptionHandler`가 따로 있어 같은 오류가 400 또는 500이 된다 → 한 곳(`@RestControllerAdvice`)으로 모은다.
- 상태 코드 기준:

  | 상황 | 상태 코드 |
  |---|---|
  | 리소스가 없음 | `404 Not Found` |
  | 회원의 잘못(재고 부족, 포인트 부족, 검증 실패) | `400 Bad Request` |
  | 인증 확인 실패 | `401 Unauthorized` |

- 사용자 스토리의 인수 테스트에 정해진 상태 코드가 있으면 그것을 따른다.

## 4. 용어 — `docs/glossary.md`를 따른다

- 클래스·메서드·변수·주석·커밋 메시지 모두 용어 사전의 한글명·영문명을 쓴다.
- 자주 틀리는 것:
  - user / account → **회원(Member)**
  - 옵션의 `quantity` → **재고(stock)**, 주문의 `quantity` → **주문 수량(quantity)**
  - total / amount → **주문 금액(totalPrice)**
  - 서비스 JWT는 **토큰(token)**, 카카오 쪽은 항상 `kakao` 접두어(**kakaoAccessToken**)
- 코드에서 용어 사전과 다른 이름을 발견하면 임의로 바꾸지 말고 AGENTS.md의 "문서와 다른 것을 발견하면" 절차를 따른다. 단, API 응답 필드명처럼 외부에 보이는 이름은 바꾸기 전에 사용자에게 묻는다.

## 5. 주석

- **반드시 한국어로 쓴다.** 기존 영어 주석은 한국어로 옮기거나, 필요 없으면 지운다.
- 단순한 작동 원리(코드를 읽으면 알 수 있는 것)는 쓰지 않는다.
- 이름과 구조로 드러나지 않는 **중요한 핵심 원리**가 있을 때만 쓴다. 예: 왜 이 순서로 차감해야 하는지, 왜 실패해도 주문을 유지하는지.

```java
// 나쁨: 코드를 다시 말한다
// 재고를 차감한다
option.deductStock(quantity);

// 좋음: 코드로 드러나지 않는 이유를 말한다
// 결제가 거절되면 재고도 되돌려야 하므로 포인트를 먼저 차감한다
member.deductPoint(totalPrice);
option.deductStock(quantity);
```

## 6. 메서드 크기 제한

- **뎁스는 최대 4.** 메서드 본문이 뎁스 1이고, `if`·`for`·`while`·`try`·람다 블록에 들어갈 때마다 1씩 늘어난다.
- **길이는 최대 15줄.** 메서드 선언 줄부터 닫는 중괄호까지 센다. 애너테이션 줄은 세지 않는다.
- 넘으면 의미 있는 단위로 메서드를 뽑는다. 뽑은 메서드 이름이 그 단계를 설명하게 한다.

## 7. 금지 문법

- **`else`(그리고 `else if`)를 쓰지 않는다.** `if` + 이른 반환(가드 절)으로 바꾼다.
- **`switch`(문·식 모두)를 쓰지 않는다.** `if`를 나열하거나, 분기가 많으면 `Map`·enum 메서드로 바꾼다.
- **3항 연산자(`조건 ? a : b`)를 쓰지 않는다.** `if` + 반환, 또는 별도 메서드로 바꾼다.

```java
// 나쁨
if (stock < quantity) {
    throw new IllegalArgumentException("재고가 부족합니다.");
} else {
    stock -= quantity;
}
String label = isKakao ? "카카오" : "일반";

// 좋음
if (stock < quantity) {
    throw new IllegalArgumentException("재고가 부족합니다.");
}
stock -= quantity;
```

## 8. 검토와 수정 — 두 번 넘게 고치지 않는다

리팩터링이 끝나면 바꾼 파일마다 아래를 검토한다.

1. 1~7절의 조건을 하나씩 확인한다. 기계적으로 찾을 수 있는 것은 먼저 검색한다(아래 명령).
2. 조건에 맞지 않으면 맞게 고친다. **이것이 1회차다.**
3. 다시 검토한다. 여기서도 같은 곳이 조건에 맞지 않아 **두 번째 수정이 필요하면, 그 부분은 고치지 않는다.** 그대로 두고 보고 목록에 올린다.
4. 작업이 끝나면 고친 것과 별도로 아래 형식으로 보고한다.

   | 위치(파일:줄) | 어긋난 조건 | 1회차에 한 수정 | 다시 어긋난 이유 | 제안 |
   |---|---|---|---|---|

5. 사용자가 답을 주어 그 부분을 다시 고치게 되면, AGENTS.md "두 번 이상 고친 것"에 무엇을 왜 다시 고쳤는지 한 줄 남긴다.

### 기계적으로 찾는 명령 (Git Bash)

```bash
# else / switch
grep -rnE '\belse\b|\bswitch\b' src/main/java/gift
# 3항 연산자 후보 (제네릭 와일드카드 <?> 등은 직접 걸러낸다)
grep -rnE '[^<]\?[^>]*:' src/main/java/gift --include=*.java | grep -v '//'
# 영어 주석 후보
grep -rnE '//\s*[A-Za-z]|/\*\*?\s*[A-Za-z]' src/main/java/gift
```

뎁스·길이·이름·용어·예외 처리 일관성은 검색으로 확인되지 않는다. 파일을 읽고 직접 확인한다.

### 마무리 확인

- `gradlew.bat build`로 빌드와 테스트가 통과하는지 확인한다. (ktlint는 Kotlin만 검사하므로 Java 스타일은 위 검토로만 확인된다.)
- 결과에는 고친 파일 목록, 검토 결과, 8절의 보고 표를 함께 적는다.
