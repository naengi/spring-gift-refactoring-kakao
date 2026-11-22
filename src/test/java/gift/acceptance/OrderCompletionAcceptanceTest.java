package gift.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import gift.order.Order;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;

/**
 * 2. 회원이 주문을 마친다 (US-O1, 주문 후 처리)
 * <p>
 * 받는 사람은 주문 요청의 {@code receiverId}(받는 사람의 회원 ID)로 지정한다.
 * 카카오 API는 실제로 호출하지 않는다. 우리 쪽 메시지 클라이언트(KakaoMessageClient)를 목으로 바꾸고
 * 어떤 카카오 액세스 토큰으로, 어떤 내용이 전송됐는지 잡아서 확인한다.
 * 카카오 메시지는 카카오 액세스 토큰 주인의 카카오톡으로 가므로, 받는 사람의 카카오 액세스 토큰으로 보냈다면 받는 사람에게 간 것이다.
 */
@DisplayName("회원이 주문을 마친다")
class OrderCompletionAcceptanceTest extends AcceptanceTest {

    private static final String GIFT_MESSAGE = "생일 축하해!";
    private static final String ORDERER_KAKAO_TOKEN = "kakao-access-token-orderer";
    private static final String RECEIVER_KAKAO_TOKEN = "kakao-access-token-receiver";

    @Test
    @Disabled("🚧 현재 코드와 불일치: OrderRequest에 receiverId가 없고, 카카오 메시지가 주문자의 카카오 액세스 토큰으로 주문자 본인에게 간다")
    @DisplayName("시나리오 5. 주문을 마치면 상대방에게 카카오톡 메시지가 전송된다")
    void 주문을_마치면_상대방에게_카카오톡_메시지가_전송된다() {
        // Given 회원이 카카오 로그인으로 서비스 토큰을 받았다
        TestMember orderer = kakaoLogin(ORDERER_KAKAO_TOKEN);
        setPoint(orderer, 100_000);
        // 선물 받는 상대방도 카카오 로그인한 회원이다
        TestMember receiver = kakaoLogin(RECEIVER_KAKAO_TOKEN);
        TestProduct product = createProduct(15_000, 10);

        // And 회원이 선물 메시지를 입력했다
        // When 회원이 받는 사람을 지정해 주문을 마친다
        ResponseEntity<JsonNode> response = post(
            "/api/orders",
            Map.of(
                "optionId", product.optionId(),
                "quantity", 1,
                "message", GIFT_MESSAGE,
                "receiverId", receiver.id()
            ),
            orderer
        );

        // Then 201 Created와 함께 주문이 생성된다
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("message").asText()).isEqualTo(GIFT_MESSAGE);

        // And 선물 받는 상대방에게 선물 메시지가 담긴 카카오톡 메시지가 전송된다
        ArgumentCaptor<String> kakaoToken = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Order> sentOrder = ArgumentCaptor.forClass(Order.class);
        then(kakaoMessageClient).should().sendToMe(kakaoToken.capture(), sentOrder.capture(), any());

        assertThat(kakaoToken.getValue())
            .as("받는 사람의 카카오 액세스 토큰으로 보내야 한다 (주문자 본인에게 가면 안 된다)")
            .isEqualTo(RECEIVER_KAKAO_TOKEN)
            .isNotEqualTo(ORDERER_KAKAO_TOKEN);
        assertThat(sentOrder.getValue().getMessage()).isEqualTo(GIFT_MESSAGE);
    }
}
