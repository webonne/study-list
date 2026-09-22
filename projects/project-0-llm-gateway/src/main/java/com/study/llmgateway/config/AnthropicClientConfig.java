package com.study.llmgateway.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AnthropicClientConfig {

    /**
     * {@code fromEnv()} 会读环境变量 {@code ANTHROPIC_API_KEY}。
     *
     * <p>⚠️ 超时和重试要一起看：SDK 默认会重试 2 次，
     * 所以最坏情况的总耗时 ≈ timeout × (重试次数 + 1)。
     * 只调小 timeout 而不管重试次数，并不会让请求更快失败。
     */
    @Bean
    public AnthropicClient anthropicClient() {
        return AnthropicOkHttpClient.builder()
                .fromEnv()
                .timeout(Duration.ofSeconds(120))
                .maxRetries(2)
                .build();
    }
}
