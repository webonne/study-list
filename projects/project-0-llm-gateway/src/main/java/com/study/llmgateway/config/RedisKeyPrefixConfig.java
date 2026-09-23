package com.study.llmgateway.config;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * 在 {@link StringRedisTemplate} 上统一加 key 前缀。
 * 业务代码继续写逻辑 key（如 {@code chat:session:s1}），真正落到 Redis 的是 {@code llm-gateway:chat:session:s1}。
 */
@Configuration
@EnableConfigurationProperties(RedisKeyProperties.class)
public class RedisKeyPrefixConfig {

    @Bean
    static BeanPostProcessor redisKeyPrefixProcessor(Environment environment) {
        String prefix = RedisKeyProperties.normalize(
                environment.getProperty("app.redis.key-prefix", "llm-gateway"));
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (prefix.isEmpty() || !(bean instanceof StringRedisTemplate template)) {
                    return bean;
                }
                RedisSerializer<String> serializer = new PrefixedStringRedisSerializer(prefix);
                template.setKeySerializer(serializer);
                template.setHashKeySerializer(serializer);
                return bean;
            }
        };
    }
}
