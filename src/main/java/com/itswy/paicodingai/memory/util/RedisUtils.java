package com.itswy.paicodingai.memory.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Redis 工具类 - 统一管理键名和调用
 *
 * 设计原则：
 * 1. 所有Redis操作都通过此类，便于后期切换存储方案
 * 2. 键名管理统一，避免硬编码
 * 3. 封装常用操作，简化业务代码
 * 4. 支持配置化，便于环境切换
 *
 * 使用示例：
 * redisUtils.opsForList().rightPush("key", "value");
 * redisUtils.opsForHash().increment("key", "field", 1);
 * redisUtils.delete("key");
 */
@Slf4j
@Component
public class RedisUtils {

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${paicoding.ai.memory.redis.key-prefix:chat:memory:}")
    private String memoryKeyPrefix;

    @Value("${paicoding.ai.memory.redis.lock-prefix:chat:lock:}")
    private String lockKeyPrefix;

    @Value("${paicoding.ai.memory.redis.token-prefix:token:count:}")
    private String tokenKeyPrefix;

    @Value("${paicoding.ai.memory.redis.expire-days:7}")
    private int expireDays;

    public RedisUtils(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // ==================== 键名生成 ====================

    /**
     * 生成会话记忆键
     */
    public String memoryKey(String conversationId) {
        return memoryKeyPrefix + conversationId;
    }

    /**
     * 生成分布式锁键
     */
    public String lockKey(String conversationId) {
        return lockKeyPrefix + conversationId;
    }

    /**
     * 生成Token统计键
     */
    public String tokenKey(String conversationId) {
        return tokenKeyPrefix + conversationId;
    }

    /**
     * 从会话记忆键中提取conversationId
     */
    public String extractConversationId(String key) {
        if (key == null || !key.startsWith(memoryKeyPrefix)) {
            return null;
        }
        return key.substring(memoryKeyPrefix.length());
    }

    // ==================== 通用操作 ====================

    /**
     * 删除键
     */
    public Boolean delete(String key) {
        try {
            return stringRedisTemplate.delete(key);
        } catch (Exception e) {
            log.error("Redis删除失败: key={}", key, e);
            return false;
        }
    }

    /**
     * 批量删除键
     */
    public Long delete(List<String> keys) {
        try {
            return stringRedisTemplate.delete(keys);
        } catch (Exception e) {
            log.error("Redis批量删除失败: keys={}", keys, e);
            return 0L;
        }
    }

    /**
     * 检查键是否存在
     */
    public Boolean hasKey(String key) {
        try {
            return stringRedisTemplate.hasKey(key);
        } catch (Exception e) {
            log.error("Redis检查键失败: key={}", key, e);
            return false;
        }
    }

    /**
     * 设置过期时间（天）
     */
    public Boolean expire(String key, int days) {
        try {
            return stringRedisTemplate.expire(key, days, TimeUnit.DAYS);
        } catch (Exception e) {
            log.error("Redis设置过期时间失败: key={}, days={}", key, days, e);
            return false;
        }
    }

    /**
     * 获取过期时间（秒）
     */
    public Long getExpire(String key) {
        try {
            return stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Redis获取过期时间失败: key={}", key, e);
            return -1L;
        }
    }

    /**
     * 获取所有匹配的键
     */
    public Set<String> keys(String pattern) {
        try {
            return stringRedisTemplate.keys(pattern);
        } catch (Exception e) {
            log.error("Redis查询键失败: pattern={}", pattern, e);
            return Set.of();
        }
    }

    // ==================== List操作 ====================

    /**
     * List操作封装
     */
    public ListOps opsForList() {
        return new ListOps();
    }

    public class ListOps {
        /**
         * 右端推入
         */
        public Long rightPush(String key, String value) {
            try {
                Long result = stringRedisTemplate.opsForList().rightPush(key, value);
                // 首次推入时设置过期时间
                if (result != null && result == 1) {
                    expire(key, expireDays);
                }
                return result;
            } catch (Exception e) {
                log.error("Redis List右推失败: key={}", key, e);
                return 0L;
            }
        }

        /**
         * 左端弹出
         */
        public String leftPop(String key) {
            try {
                return stringRedisTemplate.opsForList().leftPop(key);
            } catch (Exception e) {
                log.error("Redis List左弹失败: key={}", key, e);
                return null;
            }
        }

        /**
         * 右端弹出
         */
        public String rightPop(String key) {
            try {
                return stringRedisTemplate.opsForList().rightPop(key);
            } catch (Exception e) {
                log.error("Redis List右弹失败: key={}", key, e);
                return null;
            }
        }

        /**
         * 获取范围内的元素
         */
        public List<String> range(String key, long start, long end) {
            try {
                List<String> result = stringRedisTemplate.opsForList().range(key, start, end);
                return result != null ? result : List.of();
            } catch (Exception e) {
                log.error("Redis List查询失败: key={}", key, e);
                return List.of();
            }
        }

        /**
         * 获取列表长度
         */
        public Long size(String key) {
            try {
                Long size = stringRedisTemplate.opsForList().size(key);
                return size != null ? size : 0L;
            } catch (Exception e) {
                log.error("Redis List获取长度失败: key={}", key, e);
                return 0L;
            }
        }

        /**
         * 获取所有元素
         */
        public List<String> all(String key) {
            return range(key, 0, -1);
        }
    }

    // ==================== Hash操作 ====================

    /**
     * Hash操作封装
     */
    public HashOps opsForHash() {
        return new HashOps();
    }

    public class HashOps {
        /**
         * 设置字段值
         */
        public void put(String key, String field, String value) {
            try {
                stringRedisTemplate.opsForHash().put(key, field, value);
                // 首次设置时设置过期时间
                if (size(key) == 1) {
                    expire(key, expireDays);
                }
            } catch (Exception e) {
                log.error("Redis Hash设置失败: key={}, field={}", key, field, e);
            }
        }

        /**
         * 获取字段值
         */
        public Object get(String key, String field) {
            try {
                return stringRedisTemplate.opsForHash().get(key, field);
            } catch (Exception e) {
                log.error("Redis Hash获取失败: key={}, field={}", key, field, e);
                return null;
            }
        }

        /**
         * 原子递增
         */
        public Long increment(String key, String field, long delta) {
            try {
                Long result = stringRedisTemplate.opsForHash().increment(key, field, delta);
                // 首次递增时设置过期时间
                if (size(key) == 1) {
                    expire(key, expireDays);
                }
                return result;
            } catch (Exception e) {
                log.error("Redis Hash递增失败: key={}, field={}, delta={}", key, field, delta, e);
                return 0L;
            }
        }

        /**
         * 删除字段
         */
        public Long delete(String key, String... fields) {
            try {
                return stringRedisTemplate.opsForHash().delete(key, (Object[]) fields);
            } catch (Exception e) {
                log.error("Redis Hash删除失败: key={}, fields={}", key, fields, e);
                return 0L;
            }
        }

        /**
         * 获取所有字段和值
         */
        public Map<Object, Object> entries(String key) {
            try {
                return stringRedisTemplate.opsForHash().entries(key);
            } catch (Exception e) {
                log.error("Redis Hash获取所有失败: key={}", key, e);
                return Map.of();
            }
        }

        /**
         * 检查字段是否存在
         */
        public Boolean hasKey(String key, String field) {
            try {
                return stringRedisTemplate.opsForHash().hasKey(key, field);
            } catch (Exception e) {
                log.error("Redis Hash检查字段失败: key={}, field={}", key, field, e);
                return false;
            }
        }

        /**
         * 获取Hash大小
         */
        public Long size(String key) {
            try {
                Long size = stringRedisTemplate.opsForHash().size(key);
                return size != null ? size : 0L;
            } catch (Exception e) {
                log.error("Redis Hash获取大小失败: key={}", key, e);
                return 0L;
            }
        }
    }

    // ==================== Value操作 ====================

    /**
     * Value操作封装
     */
    public ValueOps opsForValue() {
        return new ValueOps();
    }

    public class ValueOps {
        /**
         * 设置值
         */
        public void set(String key, String value) {
            try {
                stringRedisTemplate.opsForValue().set(key, value);
                expire(key, expireDays);
            } catch (Exception e) {
                log.error("Redis Value设置失败: key={}", key, e);
            }
        }

        /**
         * 设置值（带过期时间）
         */
        public void set(String key, String value, long timeout, TimeUnit unit) {
            try {
                stringRedisTemplate.opsForValue().set(key, value, timeout, unit);
            } catch (Exception e) {
                log.error("Redis Value设置失败: key={}, timeout={}, unit={}", key, timeout, unit, e);
            }
        }

        /**
         * 获取值
         */
        public String get(String key) {
            try {
                return stringRedisTemplate.opsForValue().get(key);
            } catch (Exception e) {
                log.error("Redis Value获取失败: key={}", key, e);
                return null;
            }
        }

        /**
         * 原子递增
         */
        public Long increment(String key) {
            try {
                Long result = stringRedisTemplate.opsForValue().increment(key);
                expire(key, expireDays);
                return result;
            } catch (Exception e) {
                log.error("Redis Value递增失败: key={}", key, e);
                return 0L;
            }
        }

        /**
         * 原子递增（带步长）
         */
        public Long increment(String key, long delta) {
            try {
                Long result = stringRedisTemplate.opsForValue().increment(key, delta);
                expire(key, expireDays);
                return result;
            } catch (Exception e) {
                log.error("Redis Value递增失败: key={}, delta={}", key, delta, e);
                return 0L;
            }
        }

        /**
         * 设置值（如果不存在）
         */
        public Boolean setIfAbsent(String key, String value, long timeout, TimeUnit unit) {
            try {
                Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit);
                return result != null ? result : false;
            } catch (Exception e) {
                log.error("Redis Value设置失败(key不存在): key={}", key, e);
                return false;
            }
        }
    }

    // ==================== 分布式锁操作 ====================

    /**
     * 尝试获取锁
     */
    public Boolean tryLock(String conversationId, long waitTime, long leaseTime) {
        String lockKey = lockKey(conversationId);
        return opsForValue().setIfAbsent(lockKey, "1", leaseTime, TimeUnit.SECONDS);
    }

    /**
     * 释放锁
     */
    public Boolean releaseLock(String conversationId) {
        String lockKey = lockKey(conversationId);
        return delete(lockKey);
    }

    // ==================== 批量操作 ====================

    /**
     * 批量获取会话记忆
     */
    public List<String> getAllMemory(String conversationId) {
        String key = memoryKey(conversationId);
        return opsForList().all(key);
    }

    /**
     * 批量存储会话记忆
     */
    public void saveAllMemory(String conversationId, List<String> messages) {
        String key = memoryKey(conversationId);

        // 先删除原有数据
        delete(key);

        // 批量存储
        for (String message : messages) {
            opsForList().rightPush(key, message);
        }
    }

    /**
     * 清空会话记忆
     */
    public void clearMemory(String conversationId) {
        String key = memoryKey(conversationId);
        delete(key);
    }

    /**
     * 获取会话记忆数量
     */
    public long getMemorySize(String conversationId) {
        String key = memoryKey(conversationId);
        return opsForList().size(key);
    }

    // ==================== Token统计操作 ====================

    /**
     * 记录Token消耗
     */
    public void recordTokenUsage(String conversationId, int inputTokens, int outputTokens) {
        String key = tokenKey(conversationId);
        opsForHash().increment(key, "input", inputTokens);
        opsForHash().increment(key, "output", outputTokens);
        opsForHash().increment(key, "count", 1);
    }

    /**
     * 获取Token使用统计
     */
    public Map<String, Long> getTokenUsage(String conversationId) {
        String key = tokenKey(conversationId);
        Map<Object, Object> usage = opsForHash().entries(key);

        return usage.entrySet().stream()
                .filter(e -> e.getKey() instanceof String && e.getValue() instanceof Number)
                .collect(Collectors.toMap(
                        e -> (String) e.getKey(),
                        e -> ((Number) e.getValue()).longValue()
                ));
    }

    /**
     * 获取总输入Token数
     */
    public long getTotalInputTokens(String conversationId) {
        String key = tokenKey(conversationId);
        Object value = opsForHash().get(key, "input");
        return value instanceof Number ? ((Number) value).longValue() : 0;
    }

    /**
     * 获取总输出Token数
     */
    public long getTotalOutputTokens(String conversationId) {
        String key = tokenKey(conversationId);
        Object value = opsForHash().get(key, "output");
        return value instanceof Number ? ((Number) value).longValue() : 0;
    }

    /**
     * 获取LLM调用次数
     */
    public long getLlmCallCount(String conversationId) {
        String key = tokenKey(conversationId);
        Object value = opsForHash().get(key, "count");
        return value instanceof Number ? ((Number) value).longValue() : 0;
    }

    /**
     * 清空Token统计
     */
    public void clearTokenUsage(String conversationId) {
        String key = tokenKey(conversationId);
        delete(key);
    }

    // ==================== 配置信息 ====================

    /**
     * 获取配置信息
     */
    public String getConfigSummary() {
        return String.format(
                "Redis配置: memoryKeyPrefix=%s, lockKeyPrefix=%s, tokenKeyPrefix=%s, expireDays=%d",
                memoryKeyPrefix, lockKeyPrefix, tokenKeyPrefix, expireDays
        );
    }
}
