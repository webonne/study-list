package com.study.llmgateway.chat.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.study.llmgateway.config.LlmProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Redis 实现。用 {@code app.conversation.store=redis} 开启。
 *
 * <p>用 List 结构存，每条消息一个 JSON。TTL 保证不再活跃的会话会自动回收——
 * 这是内存实现最缺的那一块。
 */
@Component
@Primary
@ConditionalOnProperty(name = "app.conversation.store", havingValue = "redis")
public class RedisConversationStore implements ConversationStore {

    private static final String KEY_PREFIX = "chat:session:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final LlmProperties properties;

    public RedisConversationStore(StringRedisTemplate redis,
                                  ObjectMapper objectMapper,
                                  LlmProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public List<Turn> load(String sessionId) {
        List<String> raw = redis.opsForList().range(key(sessionId), 0, -1);
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<Turn> turns = new ArrayList<>(raw.size());
        for (String json : raw) {
            turns.add(readTurn(json));
        }
        return turns;
    }

    @Override
    public void append(String sessionId, Turn turn) {
        String key = key(sessionId);
        redis.opsForList().rightPush(key, writeTurn(turn));
        // 只保留最近 N 条：负数下标从右往左数，-N 到 -1 就是最后 N 条
        redis.opsForList().trim(key, -properties.getMaxHistoryMessages(), -1);
        redis.expire(key, properties.getSessionTtl());
    }

    @Override
    public void clear(String sessionId) {
        redis.delete(key(sessionId));
    }

    private String key(String sessionId) {
        return KEY_PREFIX + sessionId;
    }

    private String writeTurn(Turn turn) {
        try {
            return objectMapper.writeValueAsString(turn);
        } catch (Exception e) {
            throw new IllegalStateException("序列化对话消息失败", e);
        }
    }

    private Turn readTurn(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Turn>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("反序列化对话消息失败: " + json, e);
        }
    }
}
