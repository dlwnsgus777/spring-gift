package gift.point;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class Point {

    @Column(name = "point")
    private int value;

    protected Point() {
        this.value = 0;
    }

    public Point(int value) {
        this.value = value;
    }

    public Point charge(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
        return new Point(this.value + amount);
    }

    public Point deduct(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("차감 금액은 1 이상이어야 합니다.");
        }
        if (amount > this.value) {
            throw new IllegalArgumentException("포인트가 부족합니다.");
        }
        return new Point(this.value - amount);
    }

    public int getValue() {
        return value;
    }
}
