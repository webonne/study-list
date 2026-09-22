package com.study.llmgateway.demo;

import com.study.llmgateway.llm.LlmClient;
import com.study.llmgateway.llm.dto.ChatCompletionResponse;
import com.study.llmgateway.llm.dto.ChatMessage;
import com.study.llmgateway.llm.dto.Usage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 任务 #01：发出第一个请求，打印回复、finish_reason 和 token 用量。
 *
 * <p>⚠️ <b>默认不启用</b>——每次启动都发请求 = 每次启动都花钱。
 * 需要时显式打开：{@code --app.demo.first-request=true}
 */
@Component
@ConditionalOnProperty(name = "app.demo.first-request", havingValue = "true")
public class FirstRequestRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FirstRequestRunner.class);

    private final LlmClient llmClient;

    public FirstRequestRunner(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        ChatCompletionResponse response = llmClient.complete(List.of(
                ChatMessage.user("用一句话解释什么是 RAG。")));

        log.info("[#01] 回复: {}", response.firstText().orElse("(空)"));
        log.info("[#01] finishReason={}", response.finishReason());

        Usage usage = response.usage();
        if (usage != null) {
            log.info("[#01] promptTokens={} completionTokens={} cacheHit={} cacheMiss={}",
                    usage.promptTokens(), usage.completionTokens(),
                    usage.cacheHitTokensOrZero(), usage.cacheMissTokensOrZero());
        }

        // 自检：这几个数字分别是什么意思？finish_reason 除了 stop 还可能是什么？
        // 答不上来就回去看 stage-1 任务卡。
    }
}
