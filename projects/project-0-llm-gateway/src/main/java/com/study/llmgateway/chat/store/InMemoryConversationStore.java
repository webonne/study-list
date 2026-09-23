package com.study.llmgateway.chat.store;

import com.study.llmgateway.config.LlmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(InMemoryConversationStore.class);

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
            trim(sessionId, turns);
            return turns;
        });
    }

    @Override
    public void clear(String sessionId) {
        sessions.remove(sessionId);
    }

    /**
     * 超出 {@code app.llm.max-history-messages} 时丢掉最早的完整轮次。
     * 压缩留到后面，现在直接丢。
     */
    private void trim(String sessionId, List<Turn> turns) {
        int before = turns.size();
        List<Turn> fitted = HistoryWindow.truncate(turns, properties.getMaxHistoryMessages());
        if (fitted.size() == before) {
            return;
        }
        turns.clear();
        turns.addAll(fitted);
        log.info("history truncated sessionId={} dropped={} kept={} max={}",
                sessionId, before - fitted.size(), fitted.size(), properties.getMaxHistoryMessages());
    }
}
