package com.study.llmgateway.structured;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** #04 结构化输出的配置，前缀 {@code app.structured}。 */
@ConfigurationProperties(prefix = "app.structured")
public class StructuredProperties {

    /**
     * 结构化输出的 max_tokens。比聊天小得多：抽取结果通常几百 token。
     * 给太小会截断（{@link FailureKind#TRUNCATED}），给太大在出错时白白多烧钱。
     */
    private int maxTokens = 1024;

    /**
     * 校验失败后，让模型"带着错误原因改一次"的最大次数。
     * 每次修复都是一次完整的付费调用，所以默认只给 1 次。
     */
    private int maxRepairAttempts = 1;

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public int getMaxRepairAttempts() {
        return maxRepairAttempts;
    }

    public void setMaxRepairAttempts(int maxRepairAttempts) {
        this.maxRepairAttempts = maxRepairAttempts;
    }
}
