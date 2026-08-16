package com.r2d;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * R2D 백엔드.
 *
 * <p>자전거를 타는 행위가 그대로 전투가 되는 위치기반 오토배틀 RPG의 서버입니다.
 * 서버가 유일한 권위이며, 클라이언트의 예상치는 표시용으로만 받습니다.
 *
 * <p>주행 중 호출되는 API는 센서 배치 업로드 하나뿐입니다. 실시간 전투나 보상 조작 API를
 * 제공하지 않는 것이 "주행 중 화면을 볼 이유를 구조적으로 제거한다"는 설계의 서버 측 구현입니다.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class R2dApplication {

    public static void main(String[] args) {
        SpringApplication.run(R2dApplication.class, args);
    }
}
