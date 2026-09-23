package com.study.llmgateway.llm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * {@code POST /chat/completions} 的请求体（OpenAI 兼容格式）。
 *
 * <p>{@code @JsonInclude(NON_NULL)} 很关键：不设置的字段就不要发出去。
 * 发一个 {@code "temperature": null} 过去，有些网关会直接报 400。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatCompletionRequest(
        String model,
        List<ChatMessage> messages,
        @JsonProperty("max_tokens") Integer maxTokens,
        Double temperature,
        Boolean stream,
        @JsonProperty("response_format") ResponseFormat responseFormat) {

    public static ChatCompletionRequest of(String model, List<ChatMessage> messages, int maxTokens) {
        return new ChatCompletionRequest(model, messages, maxTokens, null, false, null);
    }

    public static ChatCompletionRequest streaming(String model, List<ChatMessage> messages, int maxTokens) {
        return new ChatCompletionRequest(model, messages, maxTokens, null, true, null);
    }

    /** #04：打开 JSON 模式。提示词里必须出现 "json" 这个词，见 {@link ResponseFormat}。 */
    public static ChatCompletionRequest json(String model, List<ChatMessage> messages, int maxTokens) {
        return new ChatCompletionRequest(model, messages, maxTokens, null, false, ResponseFormat.JSON_OBJECT);
    }
}
