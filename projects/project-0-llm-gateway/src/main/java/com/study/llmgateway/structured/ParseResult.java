package com.study.llmgateway.structured;

/**
 * 一次解析的结果：要么成功（{@link #value}），要么失败（{@link #failure} + {@link #detail}）。
 *
 * @param rescued 成功了，但原文不是纯 JSON（外面包了 ```json 代码块或说明文字），
 *                是从文本里捞出来的。对照实验里单独统计它——它说明模型没守规矩，只是我们兜住了。
 */
public record ParseResult<T>(T value, FailureKind failure, String detail, boolean rescued) {

    public static <T> ParseResult<T> ok(T value, boolean rescued) {
        return new ParseResult<>(value, null, null, rescued);
    }

    public static <T> ParseResult<T> fail(FailureKind kind, String detail) {
        return new ParseResult<>(null, kind, detail, false);
    }

    public boolean success() {
        return failure == null;
    }
}
