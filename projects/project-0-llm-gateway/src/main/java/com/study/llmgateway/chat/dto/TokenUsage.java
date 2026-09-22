package com.study.llmgateway.chat.dto;

/**
 * token 用量四件套。
 *
 * <p>四类分开计价，缓存读取远比普通输入便宜——所以 #05 prompt caching
 * 是成本优化里第一个该做的（免费收益，不牺牲任何质量）。
 *
 * <p>验证缓存有没有生效，就看 {@code cacheReadInputTokens} 是不是 &gt; 0。
 */
public record TokenUsage(
        long inputTokens,
        long outputTokens,
        long cacheReadInputTokens,
        long cacheCreationInputTokens) {
}
