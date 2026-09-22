package com.study.llmgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * LLM 相关配置。
 *
 * <p>⚠️ API Key 从环境变量 {@code DEEPSEEK_API_KEY} 注入，
 * <b>不要写进任何配置文件</b>——那是最常见的泄露方式（配置文件会进 Git）。
 */
@ConfigurationProperties(prefix = "app.llm")
public class LlmProperties {

    /**
     * API 基地址。
     *
     * <p>因为走的是 <b>OpenAI 兼容协议</b>，换成任何兼容网关都只改这一行 +
     * 模型名：通义、Kimi、本地 vLLM / Ollama 都可以。这是本项目不绑定 SDK 的最大好处。
     */
    private String baseUrl = "https://api.deepseek.com";

    /** API Key，由 {@code DEEPSEEK_API_KEY} 环境变量注入。 */
    private String apiKey = "";

    /**
     * 模型 id。
     *
     * <p>⚠️ <b>模型名会变，以 DeepSeek 官方文档当前版本为准。</b>
     * 旧别名 {@code deepseek-chat} / {@code deepseek-reasoner} 已于 2026-07-24 下线，
     * 不要照抄网上的老教程（也不要照抄 AI 给你的答案，包括这一行）。
     * 报 "model not found" 时第一反应是查官方文档，而不是换个写法碰运气。
     */
    private String model = "deepseek-v4-pro";

    /** 单次响应的最大输出 token。太小会把回答截断（finish_reason 会变成 length）。 */
    private int maxTokens = 4096;

    /**
     * 系统提示。
     *
     * <p>#05 做缓存优化时，这里是被复用的<b>稳定前缀</b>——
     * 往里塞时间戳、随机 id 会让缓存命中率直接归零。
     */
    private String systemPrompt = "你是一个简洁、准确的助手。回答用中文。";

    /** 保留的最大消息条数（一问一答算两条）。超出后丢弃最早的。 */
    private int maxHistoryMessages = 20;

    /** 会话过期时间，仅 Redis 实现使用。 */
    private Duration sessionTtl = Duration.ofHours(1);

    /** 建立连接的超时。 */
    private Duration connectTimeout = Duration.ofSeconds(10);

    /**
     * 读取响应的超时。
     *
     * <p>非流式请求下，模型要把整段回答生成完才返回，所以这个值要给得比普通 RPC 大得多。
     * 输出越长、模型越"会思考"，耗时越久。
     */
    private Duration readTimeout = Duration.ofSeconds(120);

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
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

    public Duration getSessionTtl() {
        return sessionTtl;
    }

    public void setSessionTtl(Duration sessionTtl) {
        this.sessionTtl = sessionTtl;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }
}
