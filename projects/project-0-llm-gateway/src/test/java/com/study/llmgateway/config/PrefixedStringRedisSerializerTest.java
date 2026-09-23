package com.study.llmgateway.config;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrefixedStringRedisSerializerTest {

    private final PrefixedStringRedisSerializer serializer = new PrefixedStringRedisSerializer("llm-gateway:");

    @Test
    void addsPrefixOnWriteAndStripsItOnRead() {
        byte[] stored = serializer.serialize("chat:session:s1");

        assertEquals("llm-gateway:chat:session:s1", new String(stored, StandardCharsets.UTF_8));
        assertEquals("chat:session:s1", serializer.deserialize(stored));
    }

    @Test
    void blankPrefixIsANoOp() {
        RedisKeyProperties properties = new RedisKeyProperties();
        properties.setKeyPrefix("  ");
        PrefixedStringRedisSerializer plain = new PrefixedStringRedisSerializer(properties.normalizedPrefix());

        assertEquals("", properties.normalizedPrefix());
        assertEquals("chat:session:s1", new String(plain.serialize("chat:session:s1"), StandardCharsets.UTF_8));
    }

    @Test
    void appendsColonWhenMissing() {
        RedisKeyProperties properties = new RedisKeyProperties();
        properties.setKeyPrefix("llm-gateway");

        assertEquals("llm-gateway:", properties.normalizedPrefix());
    }
}
