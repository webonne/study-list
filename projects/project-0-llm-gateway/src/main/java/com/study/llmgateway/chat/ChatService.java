package com.study.llmgateway.chat;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.study.llmgateway.chat.dto.ChatResponse;
import com.study.llmgateway.chat.dto.TokenUsage;
import com.study.llmgateway.chat.store.ConversationStore;
import com.study.llmgateway.chat.store.Turn;
import com.study.llmgateway.config.LlmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 对应任务 #01（第一个请求）和 #02（多轮对话）。
 *
 * <p>后续任务在这里接着长：
 * <ul>
 *   <li>#03 流式：新增 {@code chatStream()}，用 {@code client.messages().createStreaming(params)}</li>
 *   <li>#04 结构化输出：加 {@code outputConfig}，把回复反序列化成 DTO</li>
 *   <li>#05 缓存：{@code .system(...)} 换成 {@code .systemOfTextBlockParams(...)} 并设 cacheControl</li>
 *   <li>#06 错误处理：把 {@link #chat} 的调用包进分类 catch 链</li>
 *   <li>#07 计量：把 usage 打到 Micrometer</li>
 * </ul>
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final AnthropicClient client;
    private final ConversationStore store;
    private final LlmProperties properties;

    public ChatService(AnthropicClient client, ConversationStore store, LlmProperties properties) {
        this.client = client;
        this.store = store;
        this.properties = properties;
    }

    public ChatResponse chat(String sessionId, String userMessage) {
        long startedAt = System.currentTimeMillis();

        List<Turn> history = store.load(sessionId);
        MessageCreateParams params = buildParams(history, userMessage);

        Message response = client.messages().create(params);

        String reply = extractText(response);

        // ⚠️ #02 的头号 bug：只存了用户消息，没存 assistant 回复。
        // 结果就是模型每轮都"失忆"——因为下一轮发过去的历史里根本没有它说过的话。
        store.append(sessionId, Turn.user(userMessage));
        store.append(sessionId, Turn.assistant(reply));

        TokenUsage usage = extractUsage(response);
        long elapsed = System.currentTimeMillis() - startedAt;

        log.info("chat done sessionId={} elapsedMs={} input={} output={} cacheRead={} cacheWrite={}",
                sessionId, elapsed,
                usage.inputTokens(), usage.outputTokens(),
                usage.cacheReadInputTokens(), usage.cacheCreationInputTokens());

        return new ChatResponse(
                sessionId,
                reply,
                response.stopReason().map(Object::toString).orElse(null),
                usage,
                elapsed);
    }

    public void reset(String sessionId) {
        store.clear(sessionId);
    }

    /**
     * 把历史 + 本轮问题拼成一次完整请求。
     *
     * <p>每一轮都要把<b>全部历史</b>重新发过去——服务端不记事。
     * 这也意味着 token 消耗随轮数线性增长，所以才需要 #05 缓存和 #31 上下文管理。
     */
    private MessageCreateParams buildParams(List<Turn> history, String userMessage) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(properties.getModel())
                .maxTokens(properties.getMaxTokens())
                .system(properties.getSystemPrompt());

        for (Turn turn : history) {
            if (turn.role() == Turn.Role.USER) {
                builder.addUserMessage(turn.text());
            } else {
                builder.addAssistantMessage(turn.text());
            }
        }
        builder.addUserMessage(userMessage);

        return builder.build();
    }

    /**
     * 响应的 content 是一个<b>块列表</b>，不是一个字符串。
     *
     * <p>除了文本块，还可能有思考块、工具调用块。这里只取文本块拼起来；
     * 阶段四做 tool use 时，就是在这里分出 {@code toolUse()} 分支。
     */
    private String extractText(Message response) {
        return response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(textBlock -> textBlock.text())
                .reduce("", String::concat);
    }

    private TokenUsage extractUsage(Message response) {
        var usage = response.usage();
        return new TokenUsage(
                usage.inputTokens(),
                usage.outputTokens(),
                orZero(usage.cacheReadInputTokens()),
                orZero(usage.cacheCreationInputTokens()));
    }

    private long orZero(java.util.Optional<Long> value) {
        return value.orElse(0L);
    }
}
