package com.study.llmgateway.chat.store;

import com.study.llmgateway.config.LlmProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存实现，默认启用。让你第一天不用装 Redis 就能跑起来。
 *
 * <p>⚠️ 仅供本地开发：重启即丢、不支持多实例、没有过期清理（会一直涨）。
 * 真要用就切 Redis：配置 {@code app.conversation.store=redis}。
 */
@Component
public class InMemoryConversationStore implements ConversationStore {

    private final Map<String, List<Turn>> sessions = new ConcurrentHashMap<>();
    private final LlmProperties properties;

    public InMemoryConversationStore(LlmProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<Turn> load(String sessionId) {
        List<Turn> turns = sessions.get(sessionId);
        return turns == null ? List.of() : List.copyOf(turns);
    }

    @Override
    public void append(String sessionId, Turn turn) {
        sessions.compute(sessionId, (key, existing) -> {
            List<Turn> turns = existing == null
                    ? Collections.synchronizedList(new ArrayList<>())
                    : existing;
            turns.add(turn);
            trim(turns);
            return turns;
        });
    }

    @Override
    public void clear(String sessionId) {
        sessions.remove(sessionId);
    }

    /**
     * 从最早的消息开始丢弃。
     *
     * <p>这是最朴素的裁剪策略：省 token，但会让模型"忘记"开头说过的话。
     * 阶段四（#31 上下文管理）会用 compaction / memory 换掉它。
     */
    private void trim(List<Turn> turns) {
        int max = properties.getMaxHistoryMessages();
        while (turns.size() > max) {
            turns.remove(0);
        }
    }
}
