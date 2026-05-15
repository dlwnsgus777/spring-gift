package gift;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationTest extends AbstractIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Test
    @DisplayName("Flyway V1, V2 마이그레이션이 정상 적용된다")
    void test01() {
        // arrange
        // AbstractIntegrationTest가 컨테이너와 Spring Context를 준비

        // act
        MigrationInfo[] appliedMigrations = flyway.info().applied();

        // assert
        assertThat(appliedMigrations).hasSize(2);
        assertThat(appliedMigrations[0].getDescription()).contains("Initialize");
        assertThat(appliedMigrations[1].getDescription()).contains("Insert");
    }
}
