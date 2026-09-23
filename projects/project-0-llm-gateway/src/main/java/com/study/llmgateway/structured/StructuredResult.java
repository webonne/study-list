package com.study.llmgateway.structured;

import java.util.List;

/**
 * 成功拿到的结构化结果。
 *
 * @param attempts 一共调了几次模型（1 = 一次成功；2 = 修了一次才成功）。<b>每一次都是钱</b>
 * @param failures 成功之前遇到过哪些失败，用来观察"修复"到底在修什么
 * @param rescued  最终结果是不是从非纯 JSON 文本里捞出来的
 */
public record StructuredResult<T>(T data, int attempts, List<FailureKind> failures, boolean rescued) {
}
