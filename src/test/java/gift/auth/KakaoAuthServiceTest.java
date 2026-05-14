package gift.auth;

import static org.assertj.core.api.Assertions.assertThat;

import gift.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Import(KakaoAuthServiceTest.FakeKakaoConfig.class)
class KakaoAuthServiceTest extends AbstractIntegrationTest {

    static final String FAKE_EMAIL = "kakao_" + UUID.randomUUID() + "@test.com";

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
    private KakaoAuthService kakaoAuthService;

    @Test
    @DisplayName("신규 이메일로 callback 시 TokenResponse.token()이 비어있지 않다")
    void test01() {
        // arrange
        // FAKE_EMAIL은 UUID 기반으로 DB에 없는 신규 이메일

        // act
        TokenResponse result = kakaoAuthService.callback("any-code");

        // assert
        assertThat(result.token()).isNotBlank();
    }
}
