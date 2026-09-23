package com.study.llmgateway.structured;

import com.study.llmgateway.llm.LlmClient;
import jakarta.validation.Validator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 解析器和服务不带 Spring 注解，方便单测里直接 new；在这里统一装配。 */
@Configuration
@EnableConfigurationProperties(StructuredProperties.class)
public class StructuredOutputConfig {

    @Bean
    public StructuredOutputParser structuredOutputParser(Validator validator) {
        return new StructuredOutputParser(validator);
    }

    @Bean
    public StructuredOutputService structuredOutputService(LlmClient llmClient,
                                                           StructuredOutputParser parser,
                                                           StructuredProperties properties) {
        return new StructuredOutputService(llmClient, parser, properties);
    }
}
