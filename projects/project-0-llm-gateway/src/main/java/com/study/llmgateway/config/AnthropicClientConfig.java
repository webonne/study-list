package com.study.llmgateway.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AnthropicClientConfig {

    /**
     * 不调用 {@code fromEnv()}，否则 SDK 会读本机的 {@code ANTHROPIC_API_KEY}。
     * 密钥只来自 {@code app.llm.api-key}（环境变量 {@code LLM_GATEWAY_API_KEY}）。
     * {@code app.llm.auth=bearer} 时放进 {@code Authorization: Bearer}，
     * {@code api-key} 时放进 {@code x-api-key}。
     * 域名只来自 {@code app.llm.base-url}。
     *
     * <p>⚠️ 超时和重试要一起看：SDK 默认会重试 2 次，
     * 所以最坏情况的总耗时 ≈ timeout × (重试次数 + 1)。
     * 只调小 timeout 而不管重试次数，并不会让请求更快失败。
     */
    @Bean
    public AnthropicClient anthropicClient(LlmProperties properties) {
        var builder = AnthropicOkHttpClient.builder()
                .timeout(Duration.ofSeconds(120))
                .maxRetries(2);
        String apiKey = properties.getApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            if ("api-key".equalsIgnoreCase(properties.getAuth())) {
                builder.apiKey(apiKey);
            } else {
                builder.authToken(apiKey);
            }
        }
        String baseUrl = properties.getBaseUrl();
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }
}
