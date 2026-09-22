package com.study.llmgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM 相关配置。
 *
 * <p>API Key 只从环境变量 {@code LLM_GATEWAY_API_KEY} 注入到 {@code api-key}。
 * 故意不用 {@code ANTHROPIC_API_KEY}，避免和本机其他工具的变量撞名。
 * 不要把 key 明文写进任何配置文件。
 */
@ConfigurationProperties(prefix = "app.llm")
public class LlmProperties {

    /**
     * API Key。由 {@code application.yml} 的 {@code ${LLM_GATEWAY_API_KEY:}} 注入。
     * 留空时客户端仍能启动，真正发请求才会因缺少密钥失败。
     */
    private String apiKey = "";

    /**
     * 密钥放进哪个请求头。
     * {@code bearer}：{@code Authorization: Bearer}，Packy 这类中转站用这个。
     * {@code api-key}：{@code x-api-key}，官方 {@code api.anthropic.com} 用这个。
     */
    private String auth = "bearer";

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

    /**
     * API 根地址。SDK 会在后面拼 {@code /v1/messages}，这里不要带 {@code /v1}。
     */
    private String baseUrl = "https://api.anthropic.com";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getAuth() {
        return auth;
    }

    public void setAuth(String auth) {
        this.auth = auth;
    }

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

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
