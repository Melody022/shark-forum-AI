package com.itswy.paicodingai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 转人工队列(Redis ZSET,不建表)。
 *
 * <p>意图命中 human-service → 入队(PENDING);管理员在后台把会话标记为 ACTIVE(已接入)。</p>
 */
@Slf4j
@Service
public class HumanQueueService {

    public static final String QUEUE_KEY = "human:queue";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ACTIVE = "ACTIVE";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HumanQueueService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 入队(若该会话已有 PENDING 则不重复)。返回 true 表示新入队。 */
    public boolean enqueue(String sessionId, String userId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        if (isPending(sessionId)) {
            return false;
        }
        Map<String, Object> entry = Map.of(
                "sessionId", sessionId,
                "userId", userId == null ? "" : userId,
                "status", STATUS_PENDING,
                "ts", System.currentTimeMillis());
        try {
            redisTemplate.opsForZSet().add(QUEUE_KEY, objectMapper.writeValueAsString(entry), (double) System.currentTimeMillis());
            log.info("会话已转人工入队: sessionId={}, userId={}", sessionId, userId);
            return true;
        } catch (Exception e) {
            log.error("转人工入队失败: sessionId={}", sessionId, e);
            return false;
        }
    }

    public boolean isPending(String sessionId) {
        return members().stream().anyMatch(m -> m.get("sessionId") != null
                && m.get("sessionId").equals(sessionId)
                && STATUS_PENDING.equals(m.get("status")));
    }

    /** 待人工接入的会话(按时间升序)。 */
    public List<Map<String, Object>> listPending() {
        return members().stream()
                .filter(m -> STATUS_PENDING.equals(m.get("status")))
                .toList();
    }

    /** 标记已接入。 */
    public boolean markActive(String sessionId) {
        return updateStatus(sessionId, STATUS_ACTIVE);
    }

    private boolean updateStatus(String sessionId, String status) {
        ZSetOperations<String, String> zset = redisTemplate.opsForZSet();
        Set<ZSetOperations.TypedTuple<String>> all = zset.rangeWithScores(QUEUE_KEY, 0, -1);
        if (all == null) {
            return false;
        }
        for (ZSetOperations.TypedTuple<String> tuple : all) {
            String value = tuple.getValue();
            double score = tuple.getScore() == null ? 0 : tuple.getScore();
            if (value == null) {
                continue;
            }
            try {
                Map<?, ?> node = objectMapper.readValue(value, Map.class);
                if (sessionId.equals(node.get("sessionId"))) {
                    zset.remove(QUEUE_KEY, value);
                    Map<String, Object> updated = new java.util.HashMap<>((Map<String, Object>) node);
                    updated.put("status", status);
                    zset.add(QUEUE_KEY, objectMapper.writeValueAsString(updated), score);
                    log.info("转人工会话状态更新: sessionId={} -> {}", sessionId, status);
                    return true;
                }
            } catch (Exception e) {
                log.warn("解析转人工队列成员失败: {}", value, e);
            }
        }
        return false;
    }

    private List<Map<String, Object>> members() {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> raw = redisTemplate.opsForZSet().range(QUEUE_KEY, 0, -1);
        if (raw == null) {
            return result;
        }
        for (String value : raw) {
            try {
                result.add(objectMapper.readValue(value, Map.class));
            } catch (Exception e) {
                log.warn("解析转人工队列成员失败: {}", value, e);
            }
        }
        return result;
    }
}
