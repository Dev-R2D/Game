package com.r2d.judge;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.r2d.common.R2dException;

import tools.jackson.databind.ObjectMapper;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/** {@code classpath:judge-logs/*.json}에서 심사용 로그 정의를 읽어 둡니다. */
@Component
public class JudgeLogRepository {

    private static final String LOCATION = "classpath:judge-logs/*.json";

    private final ObjectMapper objectMapper;
    private final Map<String, JudgeLog> logs = new LinkedHashMap<>();

    public JudgeLogRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void load() throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources(LOCATION);
        for (Resource resource : resources) {
            try (InputStream in = resource.getInputStream()) {
                JudgeLog log = objectMapper.readValue(in, JudgeLog.class);
                logs.put(log.logId(), log);
            }
        }
    }

    public List<JudgeLog> findAll() {
        return List.copyOf(logs.values());
    }

    public JudgeLog get(String logId) {
        JudgeLog log = logs.get(logId);
        if (log == null) {
            throw R2dException.notFound("JUDGE_LOG_NOT_FOUND", "심사용 로그를 찾을 수 없습니다: " + logId);
        }
        return log;
    }
}
