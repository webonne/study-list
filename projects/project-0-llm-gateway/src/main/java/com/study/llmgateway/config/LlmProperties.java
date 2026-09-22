package com.study.llmgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM 相关配置。
 *
 * <p>注意：API Key 不在这里配置，SDK 会自己读环境变量 {@code ANTHROPIC_API_KEY}。
 * 不要把 key 写进任何配置文件——那是最常见的泄露方式。
 */
@ConfigurationProperties(prefix = "app.llm")
public class LlmProperties {

    /**
     * 模型 id。
     *
     * <p>SDK 里有 {@code Model.*} 类型化常量，但常量的更新会滞后于模型发布，
     * 用字符串 id 对任何 SDK 版本都有效。
     */
    private String model = "claude-opus-5";

    /** 单次响应的最大输出 token。非流式建议 16000 左右，太小会把回答截断。 */
    private long maxTokens = 16000L;

    /** 系统提示。#05 做 prompt caching 时，这里会是被缓存的稳定前缀。 */
    private String systemPrompt = "你是一个简洁、准确的助手。回答用中文。";

    /** 保留的最大对话轮数（一问一答算两条）。超出后丢弃最早的消息。 */
    private int maxHistoryMessages = 20;

    /** 会话过期时间（秒），仅 Redis 实现使用。 */
    private long sessionTtlSeconds = 3600L;

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public long getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(long maxTokens) {
        this.maxTokens = maxTokens;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public int getMaxHistoryMessages() {
        return maxHistoryMessages;
    }

    public void setMaxHistoryMessages(int maxHistoryMessages) {
        this.maxHistoryMessages = maxHistoryMessages;
    }

    public long getSessionTtlSeconds() {
        return sessionTtlSeconds;
    }

    public void setSessionTtlSeconds(long sessionTtlSeconds) {
        this.sessionTtlSeconds = sessionTtlSeconds;
    }
}
