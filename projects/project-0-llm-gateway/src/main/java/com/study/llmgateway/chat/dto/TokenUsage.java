package com.study.llmgateway.chat.dto;

/**
 * 对外暴露的 token 用量。
 *
 * <p>{@code cacheHitTokens} 是 DeepSeek 的自动上下文缓存命中量，
 * 是 #05 的核心观测指标——命中率上不去，说明你的请求前缀不稳定。
 */
public record TokenUsage(
        long promptTokens,
        long completionTokens,
        long totalTokens,
        long cacheHitTokens,
        long cacheMissTokens,
        double cacheHitRatio) {
}
