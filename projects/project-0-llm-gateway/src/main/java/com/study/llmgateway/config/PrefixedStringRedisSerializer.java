package com.study.llmgateway.config;

import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * 只给 key 加前缀，value 保持原样。
 */
public class PrefixedStringRedisSerializer implements RedisSerializer<String> {

    private final String prefix;
    private final StringRedisSerializer delegate = StringRedisSerializer.UTF_8;

    public PrefixedStringRedisSerializer(String prefix) {
        this.prefix = prefix == null ? "" : prefix;
    }

    @Override
    public byte[] serialize(String value) throws SerializationException {
        if (value == null || prefix.isEmpty()) {
            return delegate.serialize(value);
        }
        return delegate.serialize(prefix + value);
    }

    @Override
    public String deserialize(byte[] bytes) throws SerializationException {
        String value = delegate.deserialize(bytes);
        if (value == null || prefix.isEmpty() || !value.startsWith(prefix)) {
            return value;
        }
        return value.substring(prefix.length());
    }
}
