package gift.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import gift.auth.KakaoLoginClient;
import gift.auth.KakaoLoginClient.KakaoTokenResponse;
import gift.auth.KakaoLoginClient.KakaoUserResponse;
import gift.auth.KakaoLoginClient.KakaoUserResponse.KakaoAccount;
import gift.category.Category;
import gift.category.CategoryRepository;
import gift.member.Member;
import gift.member.MemberRepository;
import gift.option.Option;
import gift.option.OptionRepository;
import gift.order.KakaoMessageClient;
import gift.order.OrderRepository;
import gift.product.Product;
import gift.product.ProductRepository;
import gift.wish.WishRepository;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.BDDMockito.given;

/**
 * 인수 테스트 공통 설정.
 * <p>
 * 실제 서버를 띄우고(RANDOM_PORT) HTTP로 요청한다. 카카오 서버와 통신하는 클라이언트만 목으로 바꾼다.
 * 요청이 다른 스레드에서 처리되므로 테스트의 @Transactional 롤백이 적용되지 않는다.
 * 그래서 테스트가 만든 데이터를 기록해 두었다가 {@link #cleanUp()}에서 외래키 순서대로 지운다.
 * 모든 인수 테스트가 같은 목 구성을 쓰도록 여기에 모아서 스프링 컨텍스트를 한 번만 띄운다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AcceptanceTest {

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected MemberRepository memberRepository;

    @Autowired
    protected CategoryRepository categoryRepository;

    @Autowired
    protected ProductRepository productRepository;

    @Autowired
    protected OptionRepository optionRepository;

    @Autowired
    protected OrderRepository orderRepository;

    @Autowired
    protected WishRepository wishRepository;

    @MockitoBean
    protected KakaoLoginClient kakaoLoginClient;

    @MockitoBean
    protected KakaoMessageClient kakaoMessageClient;

    private final List<Long> memberIds = new ArrayList<>();
    private final List<Long> productIds = new ArrayList<>();
    private final List<Long> categoryIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        orderRepository.findAll().stream()
            .filter(order -> memberIds.contains(order.getMemberId()))
            .forEach(orderRepository::delete);
        wishRepository.findAll().stream()
            .filter(wish -> memberIds.contains(wish.getMemberId()))
            .forEach(wishRepository::delete);
        productIds.forEach(productRepository::deleteById); // 옵션은 cascade로 함께 지워진다
        categoryIds.forEach(categoryRepository::deleteById);
        memberIds.forEach(memberRepository::deleteById);
    }

    // ---------- 회원 ----------

    /** 이메일 회원 가입 API로 새 회원을 만들고 서비스 토큰을 받는다. 새 회원은 포인트 0, 위시·주문 없음. */
    protected TestMember registerMember() {
        return registerMember(uniqueEmail(), "password");
    }

    /** 시나리오에 적힌 이메일·비밀번호로 가입한다. 고정 이메일이라 {@link #cleanUp()}으로 꼭 지워져야 한다. */
    protected TestMember registerMember(String email, String password) {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
            "/api/members/register",
            Map.of("email", email, "password", password),
            JsonNode.class
        );
        return remember(email, response.getBody().get("token").asText());
    }

    /**
     * 카카오 로그인 콜백 API로 회원을 만들고 서비스 토큰을 받는다.
     * 카카오 서버 통신({@link KakaoLoginClient})만 목으로 바꾸고, 회원 생성·카카오 액세스 토큰 저장·토큰 발급은 실제 코드를 거친다.
     */
    protected TestMember kakaoLogin(String kakaoAccessToken) {
        String email = uniqueEmail();
        String code = "code-" + UUID.randomUUID();
        given(kakaoLoginClient.requestAccessToken(code)).willReturn(new KakaoTokenResponse(kakaoAccessToken));
        given(kakaoLoginClient.requestUserInfo(kakaoAccessToken))
            .willReturn(new KakaoUserResponse(new KakaoAccount(email)));

        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
            "/api/auth/kakao/callback?code=" + code,
            JsonNode.class
        );
        return remember(email, response.getBody().get("token").asText());
    }

    /**
     * 포인트를 정확히 amount로 맞춘다. 새 회원은 포인트가 0이라 amount만큼 충전하면 된다.
     * 회원이 포인트를 조회·충전하는 API가 없어서 리포지토리를 쓴다.
     */
    protected void setPoint(TestMember member, int amount) {
        Member entity = memberRepository.findById(member.id()).orElseThrow();
        if (entity.getPoint() != 0) {
            throw new IllegalStateException("새 회원에게만 쓸 수 있다. 현재 포인트: " + entity.getPoint());
        }
        entity.chargePoint(amount);
        memberRepository.save(entity);
    }

    protected int pointOf(TestMember member) {
        return memberRepository.findById(member.id()).orElseThrow().getPoint();
    }

    private TestMember remember(String email, String token) {
        Long id = memberRepository.findByEmail(email).orElseThrow().getId();
        memberIds.add(id);
        return new TestMember(id, email, token);
    }

    private static String uniqueEmail() {
        return "test-" + UUID.randomUUID() + "@example.com";
    }

    // ---------- 상품·옵션 ----------

    /**
     * 카테고리·상품·옵션을 리포지토리로 직접 만든다.
     * 상품은 시나리오의 전제일 뿐 검증 대상이 아니고, 상품 생성 API는 리팩터링 중 바뀔 가능성이 높아서 API를 거치지 않는다.
     */
    protected TestProduct createProduct(int price, int stock) {
        Category category = categoryRepository.save(new Category("테스트", "#000000", "https://example.com/c.jpg", "테스트"));
        categoryIds.add(category.getId());
        Product product = productRepository.save(new Product("테스트 상품", price, "https://example.com/p.jpg", category));
        productIds.add(product.getId());
        Option option = optionRepository.save(new Option(product, "기본", stock));
        return new TestProduct(product.getId(), option.getId());
    }

    /** 옵션 목록 API로 재고를 조회한다. */
    protected int stockOf(TestProduct product) {
        JsonNode options = restTemplate.getForEntity(
            "/api/products/" + product.productId() + "/options",
            JsonNode.class
        ).getBody();
        for (JsonNode option : options) {
            if (option.get("id").asLong() == product.optionId()) {
                return option.get("quantity").asInt(); // OptionResponse.quantity는 재고다
            }
        }
        throw new IllegalStateException("옵션을 찾을 수 없다: " + product.optionId());
    }

    // ---------- HTTP ----------

    protected ResponseEntity<JsonNode> post(String path, Object body, TestMember member) {
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, auth(member)), JsonNode.class);
    }

    protected ResponseEntity<JsonNode> get(String path, TestMember member) {
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(auth(member)), JsonNode.class);
    }

    private static HttpHeaders auth(TestMember member) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(member.token());
        return headers;
    }

    protected record TestMember(Long id, String email, String token) {
    }

    protected record TestProduct(Long productId, Long optionId) {
    }
}
