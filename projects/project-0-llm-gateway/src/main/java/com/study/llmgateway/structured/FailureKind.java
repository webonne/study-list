package com.study.llmgateway.structured;

/**
 * 结构化输出失败的五种情况。
 *
 * <p><b>为什么要分类</b>："拿到的 JSON 不能用"这句话里藏着五种完全不同的问题，
 * 修法也完全不同。混在一起统计，你只知道失败率，不知道该改哪儿。
 *
 * <p>从外到内是三层：<b>语法层</b>（是不是 JSON）→ <b>结构层</b>（字段、类型对不对）
 * → <b>语义层</b>（值合不合理）。DeepSeek 的 JSON 模式只管得了第一层。
 */
public enum FailureKind {

    /** 返回了空内容。DeepSeek 文档明确说 JSON 模式偶尔会这样。重试通常能好。 */
    EMPTY(true),

    /**
     * 被 {@code max_tokens} 截断（{@code finish_reason=length}）。
     * <b>重试没用</b>——同样的上限还会截断。要么调大 max_tokens，要么让模型输出更短。
     */
    TRUNCATED(false),

    /** 语法层：根本不是 JSON，连把 JSON 从文本里捞出来也失败了。 */
    NOT_JSON(true),

    /** 结构层：是 JSON，但缺字段、类型不对（比如该是数组的给了字符串）。 */
    SCHEMA_MISMATCH(true),

    /** 语义层：结构对，但值不合法（枚举越界、必填字段是空串、列表为空）。 */
    INVALID_VALUE(true);

    private final boolean retryable;

    FailureKind(boolean retryable) {
        this.retryable = retryable;
    }

    /** 让模型再改一次有没有意义。 */
    public boolean retryable() {
        return retryable;
    }
}
