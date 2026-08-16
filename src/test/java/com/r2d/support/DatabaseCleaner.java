package com.r2d.support;

import java.util.List;

import com.r2d.config.GameDataSeeder;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 테스트 사이에 DB를 비웁니다.
 *
 * <p>통합 테스트가 서로의 상태에 의존하면 순서에 따라 결과가 달라집니다. 특히 서브몹 상태
 * 전이는 누적 관측이 판정 근거이므로, 이전 테스트가 남긴 관측이 그대로 남으면 검증이 무의미해집니다.
 */
@Component
public class DatabaseCleaner {

    private final JdbcTemplate jdbcTemplate;
    private final GameDataSeeder seeder;

    public DatabaseCleaner(JdbcTemplate jdbcTemplate, GameDataSeeder seeder) {
        this.jdbcTemplate = jdbcTemplate;
        this.seeder = seeder;
    }

    /** 모든 테이블을 비우고 기준 데이터를 다시 넣습니다. */
    @Transactional
    public void resetToSeedState() {
        List<String> tables = jdbcTemplate.queryForList(
                "select table_name from information_schema.tables where table_schema = 'PUBLIC'",
                String.class);

        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        for (String table : tables) {
            jdbcTemplate.execute("TRUNCATE TABLE \"" + table + "\" RESTART IDENTITY");
        }
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");

        seeder.seed();
    }
}
