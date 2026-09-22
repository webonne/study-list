package com.study.llmgateway.llm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Optional;

/**
 * {@code POST /chat/completions} 的响应体。
 *
 * <p>⚠️ {@code ignoreUnknown = true} 不是偷懒：模型厂商会不定期给响应加字段
 * （比如 reasoner 系列会多返回 {@code reasoning_content}）。
 * 不加这个注解，对方加个字段你的服务就 500。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatCompletionResponse(
        String id,
        String model,
        List<Choice> choices,
        Usage usage) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(
            int index,
            ChatMessage message,
            @JsonProperty("finish_reason") String finishReason) {
    }

    /**
     * 取第一条回复的文本。
     *
     * <p>这里用 Optional 链是有原因的：{@code choices} 可能为空
     * （内容被过滤、上游异常），直接 {@code get(0)} 会抛 IndexOutOfBounds，
     * 而且报错信息完全看不出真实原因。
     */
    public Optional<String> firstText() {
        if (choices == null || choices.isEmpty()) {
            return Optional.empty();
        }
        ChatMessage message = choices.get(0).message();
        return message == null ? Optional.empty() : Optional.ofNullable(message.content());
    }

    public String finishReason() {
        return (choices == null || choices.isEmpty()) ? null : choices.get(0).finishReason();
    }
}
