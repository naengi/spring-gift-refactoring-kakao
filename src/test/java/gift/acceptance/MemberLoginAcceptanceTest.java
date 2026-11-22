package gift.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 4. 회원이 이메일로 로그인한다 (US-A2)
 * <p>
 * 로그인 실패 응답은 경우에 따라 형식이 다르다. 형식 오류는 Spring 기본 에러 JSON, 없는 회원은 문자열이다.
 * 그래서 응답을 문자열로 받고 필요할 때 JSON으로 읽는다.
 */
@DisplayName("회원이 이메일로 로그인한다")
class MemberLoginAcceptanceTest extends AcceptanceTest {

    private static final String EMAIL = "test@gmail.com";
    private static final String PASSWORD = "password123";

    @Test
    @DisplayName("시나리오 7. 이메일 형식이 아닌 값으로 로그인하면 400과 기본 에러가 응답된다")
    void 이메일_형식이_아닌_값으로_로그인하면_400과_기본_에러가_응답된다() throws Exception {
        // Given 이메일 test@gmail.com, 비밀번호 password123으로 가입한 회원이 있다
        registerMember(EMAIL, PASSWORD);

        // When 이메일 test.com, 비밀번호 password123으로 로그인한다
        ResponseEntity<String> response = login("test.com", PASSWORD);

        // Then 400 Bad Request가 응답된다
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // And 응답은 Spring 기본 에러 JSON이고 error는 "Bad Request"다
        JsonNode body = new ObjectMapper().readTree(response.getBody());
        assertThat(body.get("error").asText()).isEqualTo("Bad Request");
    }

    @Test
    @DisplayName("시나리오 8. 가입되지 않은 이메일로 로그인하면 400과 로그인 실패 메시지가 응답된다")
    void 가입되지_않은_이메일로_로그인하면_400과_로그인_실패_메시지가_응답된다() {
        // Given 이메일 test@gmail.com, 비밀번호 password123으로 가입한 회원이 있다
        registerMember(EMAIL, PASSWORD);

        // When 이메일 test2@gmail.com, 비밀번호 password123으로 로그인한다
        ResponseEntity<String> response = login("test2@gmail.com", PASSWORD);

        // Then 400 Bad Request가 응답된다
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // And 응답 메시지는 "Invalid email or password."다
        assertThat(response.getBody()).isEqualTo("Invalid email or password.");
    }

    private ResponseEntity<String> login(String email, String password) {
        return restTemplate.postForEntity(
            "/api/members/login",
            Map.of("email", email, "password", password),
            String.class
        );
    }
}
