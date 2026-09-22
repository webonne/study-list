package com.study.llmgateway.llm.dto;

/**
 * OpenAI 兼容格式的一条消息。
 *
 * <p>role 取值：{@code system} / {@code user} / {@code assistant}。
 * 阶段四做 tool use 时还会用到 {@code tool}。
 */
public record ChatMessage(String role, String content) {

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }
}
