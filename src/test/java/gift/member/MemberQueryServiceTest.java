package gift.member;

import gift.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class MemberQueryServiceTest extends AbstractIntegrationTest {

    @Autowired
    private MemberQueryService memberQueryService;

    @Test
    @DisplayName("전체 회원 목록을 반환한다")
    void test01() {
        // arrange - Flyway V2 시드 데이터: admin@example.com, user1@example.com, user2@example.com

        // act
        List<Member> result = memberQueryService.findAll();

        // assert
        assertThat(result).extracting(Member::getEmail)
            .contains("admin@example.com", "user1@example.com", "user2@example.com");
    }

    @Test
    @DisplayName("존재하는 ID로 조회하면 해당 회원을 반환한다")
    void test02() {
        // arrange
        Member saved = memberQueryService.findAll().get(0);

        // act
        Member result = memberQueryService.findById(saved.getId());

        // assert
        assertThat(result.getId()).isEqualTo(saved.getId());
        assertThat(result.getEmail()).isEqualTo(saved.getEmail());
    }

    @Test
    @DisplayName("존재하지 않는 ID로 조회하면 NoSuchElementException을 던진다")
    void test03() {
        // act & assert
        assertThatThrownBy(() -> memberQueryService.findById(999999L))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("존재하는 이메일로 조회하면 해당 회원을 반환한다")
    void test04() {
        // arrange - Flyway V2 시드 데이터
        String email = "user1@example.com";

        // act
        Member result = memberQueryService.findByEmail(email);

        // assert
        assertThat(result.getEmail()).isEqualTo(email);
    }

    @Test
    @DisplayName("존재하지 않는 이메일로 조회하면 NoSuchElementException을 던진다")
    void test05() {
        // act & assert
        assertThatThrownBy(() -> memberQueryService.findByEmail("ghost@nowhere.com"))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("존재하는 이메일로 findByEmailOptional 조회하면 Optional.of(member)를 반환한다")
    void test06() {
        // arrange - Flyway V2 시드 데이터
        String email = "user1@example.com";

        // act
        Optional<Member> result = memberQueryService.findByEmailOptional(email);

        // assert
        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo(email);
    }

    @Test
    @DisplayName("존재하지 않는 이메일로 findByEmailOptional 조회하면 Optional.empty()를 반환한다")
    void test07() {
        // arrange
        String email = "ghost@nowhere.com";

        // act
        Optional<Member> result = memberQueryService.findByEmailOptional(email);

        // assert
        assertThat(result).isEmpty();
    }
}
