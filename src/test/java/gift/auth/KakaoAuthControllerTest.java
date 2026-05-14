package gift.auth;

import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gift.AbstractIntegrationTest;
import gift.member.Member;
import gift.member.MemberRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

@Import(KakaoAuthControllerTest.FakeKakaoConfig.class)
class KakaoAuthControllerTest extends AbstractIntegrationTest {

    static final String FAKE_EMAIL = "kakao_" + UUID.randomUUID() + "@test.com";
    static final String FAKE_EMAIL_EXISTING = "kakao_existing_" + UUID.randomUUID() + "@test.com";

    @TestConfiguration
    static class FakeKakaoConfig {
        @Bean
        @Primary
        public KakaoLoginClient kakaoLoginClient() {
            FakeKakaoLoginClient client = new FakeKakaoLoginClient();
            client.setFixedEmail(FAKE_EMAIL);
            return client;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private KakaoLoginClient kakaoLoginClient;

    @Test
    @DisplayName("GET /api/auth/kakao/login 요청 시 Kakao 인증 URL로 302 리다이렉트한다")
    void test01() throws Exception {
        // arrange
        // (no special setup needed — endpoint uses KakaoLoginProperties from application-test.properties)

        // act & assert
        mockMvc.perform(get("/api/auth/kakao/login"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("https://kauth.kakao.com/oauth/authorize")))
            .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("client_id=test-client-id")));
    }

    @Test
    @DisplayName("신규 이메일로 callback 요청 시 회원이 자동 생성되고 200 OK와 비어있지 않은 token을 반환한다")
    void test02() throws Exception {
        // arrange
        // FAKE_EMAIL은 UUID 기반으로 유니크하며 FakeKakaoLoginClient가 반환한다

        // act & assert
        mockMvc.perform(get("/api/auth/kakao/callback").param("code", "any-code"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").exists())
            .andExpect(jsonPath("$.token").value(not(emptyString())));
    }

    @Test
    @DisplayName("이미 DB에 존재하는 이메일로 callback 요청 시 기존 회원을 재사용하고 200 OK와 token을 반환한다 (새 회원이 생성되지 않는다)")
    void test03() throws Exception {
        // arrange
        ((FakeKakaoLoginClient) kakaoLoginClient).setFixedEmail(FAKE_EMAIL_EXISTING);
        memberRepository.save(new Member(FAKE_EMAIL_EXISTING));
        long countBefore = memberRepository.findByEmail(FAKE_EMAIL_EXISTING).stream().count();

        // act
        mockMvc.perform(get("/api/auth/kakao/callback").param("code", "any-code"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").exists())
            .andExpect(jsonPath("$.token").value(not(emptyString())));

        // assert
        long countAfter = memberRepository.findAll().stream()
            .filter(m -> FAKE_EMAIL_EXISTING.equals(m.getEmail()))
            .count();
        org.assertj.core.api.Assertions.assertThat(countAfter).isEqualTo(countBefore);
    }

    @Test
    @DisplayName("GET /api/auth/kakao/callback 호출 시 code 쿼리 파라미터가 없으면 400을 반환한다")
    void test04() throws Exception {
        // arrange

        // act & assert
        mockMvc.perform(get("/api/auth/kakao/callback"))
            .andExpect(status().isBadRequest());
    }
}
