package com.study.llmgateway.chat;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ChatStreamConfig {

    /**
     * 流式请求会占着一条连接直到模型说完，不能堵在 servlet 线程上。
     * 不用 {@code ForkJoinPool.commonPool()}，避免和别的任务抢线程。
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService chatStreamExecutor() {
        return Executors.newFixedThreadPool(8, runnable -> {
            Thread thread = new Thread(runnable, "chat-stream");
            thread.setDaemon(false);
            return thread;
        });
    }
}
