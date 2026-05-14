package gift.auth;

import static org.assertj.core.api.Assertions.assertThat;

import gift.AbstractIntegrationTest;
import gift.member.MemberRepository;
import gift.support.MemberFixture;
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

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private KakaoLoginClient kakaoLoginClient;

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

    @Test
    @DisplayName("기존 이메일로 callback 시 회원을 새로 만들지 않고 JWT 토큰을 반환한다")
    void test02() {
        // arrange
        String existingEmail = "existing_" + UUID.randomUUID() + "@test.com";
        memberRepository.save(MemberFixture.builder().email(existingEmail).build());
        ((FakeKakaoLoginClient) kakaoLoginClient).setFixedEmail(existingEmail);

        // act
        TokenResponse result = kakaoAuthService.callback("any-code");

        // assert
        assertThat(result.token()).isNotBlank();
        long count = memberRepository.findAll().stream()
                .filter(m -> m.getEmail().equals(existingEmail))
                .count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("callback 후 member.kakaoAccessToken이 갱신되어 DB에 저장된다")
    void test03() {
        // arrange
        String newEmail = "new_" + UUID.randomUUID() + "@test.com";
        ((FakeKakaoLoginClient) kakaoLoginClient).setFixedEmail(newEmail);

        // act
        kakaoAuthService.callback("any-code");

        // assert
        gift.member.Member savedMember = memberRepository.findByEmail(newEmail).orElseThrow();
        assertThat(savedMember.getKakaoAccessToken()).isEqualTo("fake-access-token");
    }
}
