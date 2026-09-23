package com.study.llmgateway.structured;

import com.study.llmgateway.llm.LlmClient;
import com.study.llmgateway.llm.dto.ChatCompletionResponse;
import com.study.llmgateway.llm.dto.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * #04：让模型按 JSON 输出，解析、校验，不合格时带着错误原因让它改。
 *
 * <p><b>修复 ≠ 重试</b>。原样重发一遍，模型大概率犯同样的错；
 * 把它上一次的输出和"哪里不对"一起发回去，它才知道要改什么。
 *
 * <p>三种情况不修：
 * <ul>
 *   <li>{@link FailureKind#TRUNCATED}：同样的 max_tokens 还会截断，修也白修</li>
 *   <li>{@link FailureKind#EMPTY}：没有"上一次的输出"可以指出错误，原样重发一次</li>
 *   <li>修复次数用完：抛 {@link StructuredOutputException}，交给调用方</li>
 * </ul>
 */
public class StructuredOutputService {

    private static final Logger log = LoggerFactory.getLogger(StructuredOutputService.class);

    private final LlmClient llmClient;
    private final StructuredOutputParser parser;
    private final StructuredProperties properties;

    public StructuredOutputService(LlmClient llmClient,
                                   StructuredOutputParser parser,
                                   StructuredProperties properties) {
        this.llmClient = llmClient;
        this.parser = parser;
        this.properties = properties;
    }

    public <T> StructuredResult<T> generate(String systemPrompt, String userInput, Class<T> type) {
        // DeepSeek 要求提示词里出现 "json"，否则 JSON 模式不生效（具体表现以官方文档为准）。
        // 在我们这边先拦住：这种错在线上表现得很隐蔽，本地直接失败最省事。
        if (!mentionsJson(systemPrompt) && !mentionsJson(userInput)) {
            throw new IllegalArgumentException("使用 JSON 模式时，提示词里必须出现 \"json\" 这个词");
        }

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));
        messages.add(ChatMessage.user(userInput));

        List<FailureKind> failures = new ArrayList<>();
        int maxAttempts = 1 + Math.max(properties.getMaxRepairAttempts(), 0);

        for (int attempt = 1; ; attempt++) {
            ChatCompletionResponse response = llmClient.completeJson(messages, properties.getMaxTokens());
            String raw = response == null ? null : response.firstText().orElse(null);
            String finishReason = response == null ? null : response.finishReason();

            ParseResult<T> result = parser.parse(raw, finishReason, type);
            if (result.success()) {
                if (attempt > 1 || result.rescued()) {
                    log.info("structured ok type={} attempts={} failures={} rescued={}",
                            type.getSimpleName(), attempt, failures, result.rescued());
                }
                return new StructuredResult<>(result.value(), attempt, failures, result.rescued());
            }

            failures.add(result.failure());
            log.warn("structured failed type={} attempt={} kind={} detail={}",
                    type.getSimpleName(), attempt, result.failure(), result.detail());

            if (!result.failure().retryable() || attempt >= maxAttempts) {
                throw new StructuredOutputException(result.failure(), result.detail(), attempt, failures);
            }

            if (result.failure() != FailureKind.EMPTY) {
                // 把上一次的输出和错误原因一起发回去，让模型在它自己的答案上改
                messages.add(ChatMessage.assistant(raw));
                messages.add(ChatMessage.user(repairPrompt(result)));
            }
        }
    }

    private static String repairPrompt(ParseResult<?> failed) {
        return "上一次的输出没有通过校验。\n"
                + "问题类型：" + failed.failure() + "\n"
                + "具体原因：" + failed.detail() + "\n"
                + "请按最初要求的格式重新输出完整的 json 对象，只输出 json，不要任何解释。";
    }

    private static boolean mentionsJson(String text) {
        return text != null && text.toLowerCase(Locale.ROOT).contains("json");
    }
}
