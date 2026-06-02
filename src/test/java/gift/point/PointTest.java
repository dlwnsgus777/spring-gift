package gift.point;

import gift.member.point.Point;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PointTest {

    @Test
    @DisplayName("양수 금액을 충전하면 잔액이 증가한다")
    void test01() {
        // arrange
        Point point = new Point(1000);

        // act
        Point result = point.charge(500);

        // assert
        assertThat(result.getValue()).isEqualTo(1500);
    }

    @Test
    @DisplayName("0 이하 금액을 충전하면 IllegalArgumentException을 던진다")
    void test02() {
        // arrange
        Point point = new Point(1000);

        // act & assert
        assertThatThrownBy(() -> point.charge(0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("잔액 이하 금액을 차감하면 잔액이 감소한다")
    void test03() {
        // arrange
        Point point = new Point(1000);

        // act
        Point result = point.deduct(300);

        // assert
        assertThat(result.getValue()).isEqualTo(700);
    }

    @Test
    @DisplayName("잔액보다 큰 금액을 차감하면 IllegalArgumentException을 던진다")
    void test04() {
        // arrange
        Point point = new Point(100);

        // act & assert
        assertThatThrownBy(() -> point.deduct(200))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("포인트가 부족합니다.");
    }

    @Test
    @DisplayName("0 이하 금액을 차감하면 IllegalArgumentException을 던진다")
    void test05() {
        // arrange
        Point point = new Point(1000);

        // act & assert
        assertThatThrownBy(() -> point.deduct(0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
