package com.study.llmgateway.structured;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 把模型的原始输出变成强类型对象，失败时说清楚是哪一层失败。纯函数，不调网络，方便单测。
 *
 * <p>顺序就是 {@link FailureKind} 的三层：先判空和截断，再解析 JSON，再映射到类型，最后跑字段校验。
 *
 * <p><b>格式上宽容，语义上严格</b>：
 * <ul>
 *   <li>宽容：枚举不分大小写、多余字段忽略、JSON 外面包了代码块也能捞出来</li>
 *   <li>严格：缺字段、类型不对、枚举越界、空串空列表，一律不放行</li>
 * </ul>
 */
public class StructuredOutputParser {

    private final ObjectMapper objectMapper;
    private final Validator validator;

    /**
     * 解析器自己持有 mapper，不用 Spring 全局的那个：
     * 解析模型输出的宽容策略是这里的事，不该跟着 Web 层的 JSON 配置变。
     */
    public StructuredOutputParser(Validator validator) {
        this.objectMapper = JsonMapper.builder()
                // 模型给 "p1" 也认。注意这是 MapperFeature，写在枚举上的 @JsonFormat 实测不生效
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .build();
        this.validator = validator;
    }

    public <T> ParseResult<T> parse(String raw, String finishReason, Class<T> type) {
        // 截断要先判：被截断的 JSON 往往解析失败，不先判就会被误归成 NOT_JSON，
        // 然后你会去改提示词——而真正该改的是 max_tokens。
        if ("length".equals(finishReason)) {
            return ParseResult.fail(FailureKind.TRUNCATED,
                    "finish_reason=length，输出被 max_tokens 截断（已输出 " + lengthOf(raw) + " 字符）");
        }
        if (raw == null || raw.isBlank()) {
            return ParseResult.fail(FailureKind.EMPTY, "模型返回了空内容");
        }

        // 语法层：先按纯 JSON 解析；不行再试着从文本里把 JSON 捞出来
        boolean rescued = false;
        JsonNode tree = readTree(raw.trim());
        if (tree == null) {
            String extracted = extractJson(raw);
            tree = extracted == null ? null : readTree(extracted);
            if (tree == null) {
                return ParseResult.fail(FailureKind.NOT_JSON, "不是合法 JSON：" + abbreviate(raw));
            }
            rescued = true;
        }
        if (!tree.isObject()) {
            return ParseResult.fail(FailureKind.SCHEMA_MISMATCH, "期望一个 JSON 对象，实际是 " + tree.getNodeType());
        }

        // 结构层：字段名、类型。多出来的字段直接忽略——模型多说两句无害，别因此判失败
        T value;
        try {
            value = objectMapper.readerFor(type)
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(tree);
        } catch (InvalidFormatException e) {
            // 值的格式不对，典型是枚举给了一个不认识的值（severity="紧急"）
            return ParseResult.fail(FailureKind.INVALID_VALUE, describe(e));
        } catch (MismatchedInputException e) {
            return ParseResult.fail(FailureKind.SCHEMA_MISMATCH, describe(e));
        } catch (Exception e) {
            return ParseResult.fail(FailureKind.SCHEMA_MISMATCH, e.getMessage());
        }

        // 语义层：必填、非空、长度、取值范围。JSON 模式完全不管这一层
        Set<ConstraintViolation<T>> violations = validator.validate(value);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining("；"));
            return ParseResult.fail(FailureKind.INVALID_VALUE, detail);
        }
        return ParseResult.ok(value, rescued);
    }

    private JsonNode readTree(String text) {
        try {
            return objectMapper.readTree(text);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * 从"好的，以下是结果：```json {...} ```"这类文本里取出第一个 { 到最后一个 } 之间的内容。
     *
     * <p>这是兜底手段，不是正路。不开 JSON 模式时模型很爱加说明文字和代码块，
     * 靠它能救回不少，但救回来的比例本身就是"模型没守规矩"的证据——对照实验里要单独统计。
     */
    static String extractJson(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        return (start >= 0 && end > start) ? raw.substring(start, end + 1) : null;
    }

    private static String describe(MismatchedInputException e) {
        String path = e.getPath().stream()
                .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                .collect(Collectors.joining("."));
        String message = e.getOriginalMessage();
        return (path.isEmpty() ? "" : path + "：") + abbreviate(message);
    }

    private static int lengthOf(String raw) {
        return raw == null ? 0 : raw.length();
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replaceAll("\\s+", " ");
        return oneLine.length() <= 200 ? oneLine : oneLine.substring(0, 200) + "…";
    }
}
