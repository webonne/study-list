package com.study.llmgateway.demo;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.study.llmgateway.config.LlmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 任务 #01：发出第一个请求，打印返回文本和 usage。
 *
 * <p>⚠️ <b>默认不启用</b>，因为每次启动都发请求 = 每次启动都花钱。
 * 需要时显式打开：{@code --app.demo.first-request=true}
 */
@Component
@ConditionalOnProperty(name = "app.demo.first-request", havingValue = "true")
public class FirstRequestRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FirstRequestRunner.class);

    private final AnthropicClient client;
    private final LlmProperties properties;

    public FirstRequestRunner(AnthropicClient client, LlmProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(properties.getModel())
                .maxTokens(1024L)
                .addUserMessage("用一句话解释什么是 RAG。")
                .build();

        Message response = client.messages().create(params);

        response.content().stream()
                .flatMap(block -> block.text().stream())
                .forEach(text -> log.info("[#01] 回复: {}", text.text()));

        var usage = response.usage();
        log.info("[#01] stopReason={} inputTokens={} outputTokens={}",
                response.stopReason().map(Object::toString).orElse("null"),
                usage.inputTokens(),
                usage.outputTokens());

        // 自检：能不能解释清楚这几个数字分别是什么？答不上来就回去看 stage-1 任务卡。
    }
}
