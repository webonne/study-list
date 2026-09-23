package com.study.llmgateway.chat;

import com.study.llmgateway.chat.dto.ChatResponse;
import com.study.llmgateway.chat.dto.StreamDelta;
import com.study.llmgateway.chat.dto.StreamDone;
import com.study.llmgateway.chat.dto.TokenUsage;
import com.study.llmgateway.chat.store.ConversationStore;
import com.study.llmgateway.chat.store.HistoryWindow;
import com.study.llmgateway.chat.store.Turn;
import com.study.llmgateway.config.LlmProperties;
import com.study.llmgateway.llm.ChatStreamListener;
import com.study.llmgateway.llm.LlmClient;
import com.study.llmgateway.llm.dto.ChatCompletionResponse;
import com.study.llmgateway.llm.dto.ChatMessage;
import com.study.llmgateway.llm.dto.Usage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 对应任务 #01（第一个请求）和 #02（多轮对话）。
 *
 * <p>后续任务的接入点：
 * <ul>
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
    private final ExecutorService chatStreamExecutor;

    public ChatService(LlmClient llmClient,
                       ConversationStore store,
                       LlmProperties properties,
                       @Qualifier("chatStreamExecutor") ExecutorService chatStreamExecutor) {
        this.llmClient = llmClient;
        this.store = store;
        this.properties = properties;
        this.chatStreamExecutor = chatStreamExecutor;
    }

    public ChatResponse chat(String sessionId, String userMessage) {
        long startedAt = System.currentTimeMillis();

        List<Turn> history = HistoryWindow.truncate(store.load(sessionId), properties.getMaxHistoryMessages());
        List<ChatMessage> messages = buildMessages(history, userMessage);
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
     * 把同一轮对话以 SSE 推给客户端。
     *
     * <p>这个方法自己不做读取。它只准备好连接和取消动作，然后把 {@link #runStream} 丢进
     * {@code chat-stream} 线程池并立刻返回。模型要好几秒才说完，这段等待若留在 Tomcat 的
     * servlet 线程上，这条线程就没法再接别的请求。
     *
     * <p>推给客户端的事件：{@code delta} 是增量文本，{@code done} 带结束原因、首字延迟和总耗时。
     * 客户端断开、超时或出错时，关掉上游响应体，模型就不会继续生成。
     */
    public SseEmitter chatStream(String sessionId, String userMessage) {
        // 0 表示不给 SseEmitter 自己设超时。真正的时长由上游读超时和客户端是否断开决定。
        SseEmitter emitter = new SseEmitter(0L);
        if (userMessage == null || userMessage.isBlank()) {
            emitter.completeWithError(new IllegalArgumentException("message 不能为空"));
            return emitter;
        }

        // 读流在另一条线程，断开发生在容器线程。用这两个引用把两边接上：
        // cancelled 让解码循环停下来，upstream 是上游响应体，关掉它读操作才会立刻返回。
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicReference<InputStream> upstream = new AtomicReference<>();
        Runnable cancel = () -> {
            cancelled.set(true);
            closeQuietly(upstream.get());
        };
        emitter.onCompletion(cancel);
        emitter.onTimeout(cancel);
        emitter.onError(error -> cancel.run());

        List<ChatMessage> messages = buildMessages(
                HistoryWindow.truncate(store.load(sessionId), properties.getMaxHistoryMessages()),
                userMessage);

        chatStreamExecutor.execute(() -> runStream(emitter, sessionId, userMessage, messages, cancelled, upstream));
        return emitter;
    }

    /**
     * 在 {@code chat-stream} 线程上读完上游，再逐段写给客户端。
     * 一个字都没收到就失败时不落历史，避免会话里留下一句没有回答的用户消息。
     */
    private void runStream(SseEmitter emitter,
                           String sessionId,
                           String userMessage,
                           List<ChatMessage> messages,
                           AtomicBoolean cancelled,
                           AtomicReference<InputStream> upstream) {
        long startedAt = System.currentTimeMillis();
        long[] firstTokenAt = {0L};
        String[] finishReason = {null};
        StringBuilder reply = new StringBuilder();
        boolean completed = false;
        try {
            llmClient.stream(messages, cancelled::get, new ChatStreamListener() {
                @Override
                public void onUpstream(InputStream body) {
                    // 先存下来，客户端断开时 cancel 才能关到这条上游连接。
                    upstream.set(body);
                }

                @Override
                public void onText(String text) throws IOException {
                    if (cancelled.get()) {
                        return;
                    }
                    if (firstTokenAt[0] == 0L) {
                        // 首字延迟：从开始读到第一段文本到达。数组是为了让匿名类能改外层变量。
                        firstTokenAt[0] = System.currentTimeMillis();
                    }
                    reply.append(text);
                    emitter.send(SseEmitter.event().name("delta").data(new StreamDelta(text)));
                }

                @Override
                public void onFinish(String reason) {
                    finishReason[0] = reason;
                }
            });
            if (!cancelled.get()) {
                long elapsed = System.currentTimeMillis() - startedAt;
                long ttft = firstTokenAt[0] == 0L ? elapsed : firstTokenAt[0] - startedAt;
                emitter.send(SseEmitter.event().name("done").data(new StreamDone(finishReason[0], ttft, elapsed)));
                emitter.complete();
                completed = true;
                log.info("chat stream done sessionId={} ttftMs={} elapsedMs={} chars={}",
                        sessionId, ttft, elapsed, reply.length());
            } else {
                log.info("chat stream cancelled sessionId={} chars={}", sessionId, reply.length());
            }
        } catch (Exception e) {
            if (!cancelled.get()) {
                try {
                    emitter.completeWithError(e);
                } catch (Exception ignored) {
                    // 客户端已经断开时，再 complete 会抛 IllegalStateException
                }
                log.warn("chat stream failed sessionId={}", sessionId, e);
            } else {
                log.info("chat stream cancelled sessionId={} chars={}", sessionId, reply.length());
            }
        } finally {
            // 正常结束，或中途已经吐出过文字：把用户问题和已生成的回复写入历史。
            // 一个字都没有就失败则不写，否则下一轮会看到一句悬空的用户消息。
            if (completed || !reply.isEmpty()) {
                store.append(sessionId, Turn.user(userMessage));
                if (!reply.isEmpty()) {
                    store.append(sessionId, Turn.assistant(reply.toString()));
                }
            }
        }
    }

    private static void closeQuietly(InputStream body) {
        if (body == null) {
            return;
        }
        try {
            body.close();
        } catch (IOException ignored) {
            // 关掉上游只是为了停生成，关失败不影响客户端
        }
    }

    /**
     * 把 system + 历史 + 本轮问题拼成一次完整请求。
     *
     * <p><b>每轮都要把全部历史重新发过去</b>——服务端不记事，所谓"多轮"是你自己拼的。
     *
     * <p>直接推论：token 消耗随轮数线性增长。发出去之前会按
     * {@code app.llm.max-history-messages} 丢掉最早的完整轮次。
     * 压缩（把早期轮次总结后再留）先不做，以后换 {@link HistoryWindow} 的策略即可。
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
