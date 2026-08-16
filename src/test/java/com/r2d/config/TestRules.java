package com.r2d.config;

/** 단위 테스트용 기본 규칙. application.yml의 기본값과 같은 숫자를 씁니다. */
public final class TestRules {

    private TestRules() {
    }

    public static GameRules defaults() {
        return new GameRules(
                new GameRules.Quality(30.0, 25.0, 30.0, 0.6, 0.5),
                new GameRules.Transport(2.5, 12.0, 14.0, 0.12, 0.7),
                new GameRules.Anomaly(150, 300, 1.8, 12.0, 0.55, 800, 2, 3, 0.7, 3),
                new GameRules.Cell(30, 0.6, 7, 0.5),
                new GameRules.Damage(1.0, 0.15, 0.20, 1.30, 1.15, 1.05),
                new GameRules.Boss(1200.0, 3, 20000.0),
                new GameRules.Pack(5, 4, 0.15),
                new GameRules.DailyCap(5, 3000, 500)
        );
    }
}
