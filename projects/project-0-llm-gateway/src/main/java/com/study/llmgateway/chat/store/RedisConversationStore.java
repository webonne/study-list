package com.study.llmgateway.chat.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.study.llmgateway.config.LlmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(RedisConversationStore.class);

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
        trim(sessionId, key);
        redis.expire(key, properties.getSessionTtl());
    }

    @Override
    public void clear(String sessionId) {
        redis.delete(key(sessionId));
    }

    /**
     * 只保留最近 N 条，再丢掉开头落单的 assistant，避免从一轮对话中间切开。
     * {@code max <= 0} 时整段清空。Redis 的 {@code LTRIM key 0 -1} 会保留全部，不能拿来表示 0。
     */
    private void trim(String sessionId, String key) {
        int max = Math.max(properties.getMaxHistoryMessages(), 0);
        Long size = redis.opsForList().size(key);
        if (size == null || size <= max) {
            return;
        }
        if (max == 0) {
            redis.delete(key);
            log.info("history truncated sessionId={} dropped={} kept={} max={}", sessionId, size, 0, max);
            return;
        }
        redis.opsForList().trim(key, -max, -1);
        String head = redis.opsForList().index(key, 0);
        if (head != null && readTurn(head).role() == Turn.Role.ASSISTANT) {
            redis.opsForList().leftPop(key);
        }
        Long kept = redis.opsForList().size(key);
        long keptCount = kept == null ? 0 : kept;
        log.info("history truncated sessionId={} dropped={} kept={} max={}",
                sessionId, size - keptCount, keptCount, max);
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
