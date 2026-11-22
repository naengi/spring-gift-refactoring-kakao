package gift.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 3. 회원이 위시 아이템을 추가한다 (US-W1, US-W2)
 */
@DisplayName("회원이 위시 아이템을 추가한다")
class WishAcceptanceTest extends AcceptanceTest {

    @Test
    @DisplayName("시나리오 6. 위시에 추가한 상품이 위시 목록에 보인다")
    void 위시에_추가한_상품이_위시_목록에_보인다() {
        // Given 상품이 하나 있다
        TestProduct product = createProduct(15_000, 10);
        // And 회원이 그 상품을 아직 위시에 담지 않았다 (새 회원은 위시가 비어 있다)
        TestMember member = registerMember();

        // When 회원이 그 상품을 위시에 추가한다
        ResponseEntity<JsonNode> added = post("/api/wishes", Map.of("productId", product.productId()), member);
        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // And 회원이 위시 목록을 조회한다
        ResponseEntity<JsonNode> wishes = get("/api/wishes", member);

        // Then 조회 결과(content)에 방금 추가한 상품이 포함되어 있다
        assertThat(wishes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(productIdsOf(wishes.getBody())).contains(product.productId());
    }

    private static List<Long> productIdsOf(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.get("content").forEach(wish -> ids.add(wish.get("productId").asLong()));
        return ids;
    }
}
