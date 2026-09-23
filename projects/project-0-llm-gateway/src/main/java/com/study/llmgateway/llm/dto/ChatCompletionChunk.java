package com.study.llmgateway.llm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 流式响应里的一个 chunk。和普通响应的差别是文本在 {@code choices[].delta}，不在 {@code message}。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatCompletionChunk(List<Choice> choices) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(
            Delta delta,
            @JsonProperty("finish_reason") String finishReason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Delta(String role, String content) {
    }

    public String deltaText() {
        if (choices == null || choices.isEmpty() || choices.get(0).delta() == null) {
            return null;
        }
        return choices.get(0).delta().content();
    }

    public String finishReason() {
        return (choices == null || choices.isEmpty()) ? null : choices.get(0).finishReason();
    }
}
