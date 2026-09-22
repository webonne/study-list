package com.study.llmgateway.chat;

import com.study.llmgateway.chat.dto.ChatResponse;
import com.study.llmgateway.chat.dto.TokenUsage;
import com.study.llmgateway.chat.store.ConversationStore;
import com.study.llmgateway.chat.store.Turn;
import com.study.llmgateway.config.LlmProperties;
import com.study.llmgateway.llm.LlmClient;
import com.study.llmgateway.llm.dto.ChatCompletionResponse;
import com.study.llmgateway.llm.dto.ChatMessage;
import com.study.llmgateway.llm.dto.Usage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 对应任务 #01（第一个请求）和 #02（多轮对话）。
 *
 * <p>后续任务的接入点：
 * <ul>
 *   <li>#03 流式：新增 {@code chatStream()}，走 {@code LlmClient} 的 SSE 方法</li>
 *   <li>#04 结构化输出：请求加 {@code response_format}，回复反序列化成 DTO</li>
 *   <li>#05 缓存：把系统提示等稳定内容固定在最前面，观测 cacheHitRatio</li>
 *   <li>#06 错误处理：包一层分类异常处理 + 重试退避</li>
 *   <li>#07 计量：把 TokenUsage 打到 Micrometer</li>
 * </ul>
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final LlmClient llmClient;
    private final ConversationStore store;
    private final LlmProperties properties;

    public ChatService(LlmClient llmClient, ConversationStore store, LlmProperties properties) {
        this.llmClient = llmClient;
        this.store = store;
        this.properties = properties;
    }

    public ChatResponse chat(String sessionId, String userMessage) {
        long startedAt = System.currentTimeMillis();

        List<ChatMessage> messages = buildMessages(store.load(sessionId), userMessage);
        ChatCompletionResponse response = llmClient.complete(messages);

        String reply = response == null
                ? ""
                : response.firstText().orElse("");

        // ⚠️ #02 的头号 bug：只存用户消息、漏存 assistant 回复。
        // 症状很迷惑——不报错、回答也通顺，就是完全不记得上一轮说过什么。
        store.append(sessionId, Turn.user(userMessage));
        store.append(sessionId, Turn.assistant(reply));

        TokenUsage usage = toTokenUsage(response == null ? null : response.usage());
        long elapsed = System.currentTimeMillis() - startedAt;

        log.info("chat done sessionId={} elapsedMs={} prompt={} completion={} cacheHit={} cacheMiss={} hitRatio={}",
                sessionId, elapsed,
                usage.promptTokens(), usage.completionTokens(),
                usage.cacheHitTokens(), usage.cacheMissTokens(),
                String.format("%.2f", usage.cacheHitRatio()));

        return new ChatResponse(
                sessionId,
                reply,
                response == null ? null : response.finishReason(),
                usage,
                elapsed);
    }

    public void reset(String sessionId) {
        store.clear(sessionId);
    }

    /**
     * 把 system + 历史 + 本轮问题拼成一次完整请求。
     *
     * <p><b>每轮都要把全部历史重新发过去</b>——服务端不记事，所谓"多轮"是你自己拼的。
     *
     * <p>直接推论：token 消耗随轮数线性增长。所以才需要 #05 的缓存
     * （稳定前缀能被复用）和阶段四的上下文管理（#31 压缩历史）。
     */
    private List<ChatMessage> buildMessages(List<Turn> history, String userMessage) {
        List<ChatMessage> messages = new ArrayList<>(history.size() + 2);

        // 系统提示放最前面且保持不变 —— 这是缓存能命中的前提
        messages.add(ChatMessage.system(properties.getSystemPrompt()));

        for (Turn turn : history) {
            messages.add(turn.role() == Turn.Role.USER
                    ? ChatMessage.user(turn.text())
                    : ChatMessage.assistant(turn.text()));
        }
        messages.add(ChatMessage.user(userMessage));

        return messages;
    }

    private TokenUsage toTokenUsage(Usage usage) {
        if (usage == null) {
            return new TokenUsage(0, 0, 0, 0, 0, 0.0);
        }
        return new TokenUsage(
                usage.promptTokens(),
                usage.completionTokens(),
                usage.totalTokens(),
                usage.cacheHitTokensOrZero(),
                usage.cacheMissTokensOrZero(),
                usage.cacheHitRatio());
    }
}
