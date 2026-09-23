package com.study.llmgateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class RedisKeyPrefixConfigTest {

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void everyKeyGetsTheConfiguredPrefix() {
        RedisSerializer<String> serializer = keySerializer();
        byte[] stored = serializer.serialize("chat:session:s1");

        assertEquals("llm-gateway:chat:session:s1", new String(stored, StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private RedisSerializer<String> keySerializer() {
        return (RedisSerializer<String>) redis.getKeySerializer();
    }
}
