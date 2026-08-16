package com.r2d.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 애플리케이션 시작 시 기준 데이터를 넣습니다. */
@Configuration
public class DataSeeder {

    @Bean
    ApplicationRunner seedGameData(GameDataSeeder seeder) {
        return args -> seeder.seed();
    }
}
