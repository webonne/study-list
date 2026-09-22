package com.study.llmgateway.llm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * token 用量。
 *
 * <p>前三个字段是 OpenAI 兼容的标准字段；后两个是 <b>DeepSeek 特有</b>的缓存统计：
 *
 * <ul>
 *   <li>{@code promptCacheHitTokens} —— 命中缓存的输入 token，单价极低</li>
 *   <li>{@code promptCacheMissTokens} —— 未命中的输入 token，按正常输入价计费</li>
 * </ul>
 *
 * <p>⚠️ 和 Anthropic 不同，<b>DeepSeek 的上下文缓存是自动的</b>——没有"缓存断点"要设置，
 * 命中与否完全取决于你的请求前缀是否和上一次一致（且长度达到阈值，约 1K token 以上）。
 * 所以 #05 的功课不是"怎么开缓存"，而是<b>"怎么让前缀保持稳定"</b>——这个原则两家是一样的。
 *
 * <p>观测方式：看 {@code promptCacheHitTokens} 占总输入的比例。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Usage(
        @JsonProperty("prompt_tokens") long promptTokens,
        @JsonProperty("completion_tokens") long completionTokens,
        @JsonProperty("total_tokens") long totalTokens,
        @JsonProperty("prompt_cache_hit_tokens") Long promptCacheHitTokens,
        @JsonProperty("prompt_cache_miss_tokens") Long promptCacheMissTokens) {

    public long cacheHitTokensOrZero() {
        return promptCacheHitTokens == null ? 0L : promptCacheHitTokens;
    }

    public long cacheMissTokensOrZero() {
        return promptCacheMissTokens == null ? 0L : promptCacheMissTokens;
    }

    /** 缓存命中率。#05 的核心观测指标，目标 ≥ 70%。 */
    public double cacheHitRatio() {
        long hit = cacheHitTokensOrZero();
        long total = hit + cacheMissTokensOrZero();
        return total == 0 ? 0.0 : (double) hit / total;
    }
}
