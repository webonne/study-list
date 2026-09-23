package com.study.llmgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis key 的统一前缀。这个服务写出的每个 key 都会带上它，
 * 避免和同一个 Redis 实例里其他应用的 key 撞在一起。
 */
@ConfigurationProperties(prefix = "app.redis")
public class RedisKeyProperties {

    /** 末尾冒号可写可不写。留空表示不加前缀。 */
    private String keyPrefix = "llm-gateway";

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String normalizedPrefix() {
        return normalize(keyPrefix);
    }

    static String normalize(String keyPrefix) {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            return "";
        }
        String trimmed = keyPrefix.trim();
        return trimmed.endsWith(":") ? trimmed : trimmed + ":";
    }
}
