package com.r2d.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AuthConfig {

    /**
     * 비밀번호 해시.
     *
     * <p>BCrypt는 의도적으로 느리게 설계된 해시입니다. SHA-256 같은 범용 해시를 쓰면 GPU로
     * 초당 수십억 번 대입할 수 있어 비밀번호 저장에는 쓰면 안 됩니다.
     * strength 10은 대략 50~100ms으로, 로그인 응답 속도와 대입 공격 방어 사이의 표준값입니다.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
