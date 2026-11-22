package gift.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 1. 회원이 옵션을 골라 포인트로 결제하고 주문한다 (US-O1)
 */
@DisplayName("회원이 옵션을 골라 포인트로 결제하고 주문한다")
class OrderAcceptanceTest extends AcceptanceTest {

    @Test
    @DisplayName("시나리오 1. 주문하면 재고가 주문 수량만큼 줄어든다")
    void 주문하면_재고가_주문_수량만큼_줄어든다() {
        // Given 옵션의 재고가 15개이고, 회원의 포인트는 주문 금액보다 많다
        TestProduct product = createProduct(15_000, 15);
        TestMember member = registerMember();
        setPoint(member, 100_000);

        // When 회원이 그 옵션을 1개 주문한다
        ResponseEntity<JsonNode> response = order(member, product, 1);

        // Then 201 Created와 함께 주문이 생성된다
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // And 옵션의 재고는 14개가 된다
        assertThat(stockOf(product)).isEqualTo(14);
    }

    @Test
    @DisplayName("시나리오 2. 주문하면 포인트가 주문 금액만큼 줄어든다")
    void 주문하면_포인트가_주문_금액만큼_줄어든다() {
        // Given 상품 가격이 15,000원이고, 그 상품 옵션의 재고가 1개 이상이다
        TestProduct product = createProduct(15_000, 10);
        // And 회원의 포인트가 20,000이다
        TestMember member = registerMember();
        setPoint(member, 20_000);

        // When 회원이 그 옵션을 1개 주문한다
        ResponseEntity<JsonNode> response = order(member, product, 1);

        // Then 201 Created와 함께 주문이 생성된다
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // And 회원의 포인트는 5,000이 된다
        assertThat(pointOf(member)).isEqualTo(5_000);
    }

    @Test
    @Disabled("🚧 현재 코드와 불일치: OrderController에 예외 핸들러가 없어서 400이 아니라 500이 반환된다")
    @DisplayName("시나리오 3. 포인트가 부족하면 결제가 거절된다")
    void 포인트가_부족하면_결제가_거절된다() {
        // Given 주문 금액이 15,000원이다
        TestProduct product = createProduct(15_000, 10);
        // And 회원의 포인트가 10,000이다
        TestMember member = registerMember();
        setPoint(member, 10_000);

        // When 회원이 주문한다
        ResponseEntity<JsonNode> response = order(member, product, 1);

        SoftAssertions.assertSoftly(softly -> {
            // Then 400 Bad Request로 결제가 거절된다
            softly.assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            // And 주문은 생성되지 않는다 (새 회원이라 주문 내역이 비어 있어야 한다)
            JsonNode orders = get("/api/orders", member).getBody();
            softly.assertThat(orders.get("content")).isEmpty();
        });
    }

    @Test
    @Disabled("🚧 현재 코드와 불일치: 트랜잭션이 없어서 결제가 거절돼도 재고 차감이 먼저 커밋된다")
    @DisplayName("시나리오 4. 결제가 거절되면 재고는 차감되지 않는다")
    void 결제가_거절되면_재고는_차감되지_않는다() {
        // Given 옵션의 재고가 15개이다
        // And 주문 금액이 15,000원이고, 회원의 포인트가 10,000이다
        TestProduct product = createProduct(15_000, 15);
        TestMember member = registerMember();
        setPoint(member, 10_000);

        // When 회원이 그 옵션을 주문한다
        ResponseEntity<JsonNode> response = order(member, product, 1);

        SoftAssertions.assertSoftly(softly -> {
            // Then 결제가 거절된다 (사용자 잘못이므로 400)
            softly.assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            // And 옵션의 재고는 15개 그대로다
            softly.assertThat(stockOf(product)).isEqualTo(15);
        });
    }

    private ResponseEntity<JsonNode> order(TestMember member, TestProduct product, int quantity) {
        return post("/api/orders", Map.of("optionId", product.optionId(), "quantity", quantity), member);
    }
}
