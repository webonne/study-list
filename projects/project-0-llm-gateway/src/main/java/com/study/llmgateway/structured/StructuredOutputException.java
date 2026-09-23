package com.study.llmgateway.structured;

import java.util.List;

/** 用完修复次数还没拿到合格结果。带着最后一次的失败类型，方便调用方决定怎么处理。 */
public class StructuredOutputException extends RuntimeException {

    private final FailureKind kind;
    private final int attempts;
    private final List<FailureKind> failures;

    public StructuredOutputException(FailureKind kind, String detail, int attempts, List<FailureKind> failures) {
        super(kind + "（第 " + attempts + " 次调用后放弃）：" + detail);
        this.kind = kind;
        this.attempts = attempts;
        this.failures = List.copyOf(failures);
    }

    public FailureKind kind() {
        return kind;
    }

    public int attempts() {
        return attempts;
    }

    public List<FailureKind> failures() {
        return failures;
    }
}
