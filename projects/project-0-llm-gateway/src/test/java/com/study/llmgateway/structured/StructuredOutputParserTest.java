package com.study.llmgateway.structured;

import com.study.llmgateway.extract.Severity;
import com.study.llmgateway.extract.TicketExtraction;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredOutputParserTest {

    private final StructuredOutputParser parser = new StructuredOutputParser(
            Validation.buildDefaultValidatorFactory().getValidator());

    private static final String VALID = """
            {"title":"下单接口超时","severity":"P1","component":"order-service",
             "summary":"晚高峰三成用户下不了单","steps":["看错误日志","查连接池"]}""";

    @Test
    void parsesValidJson() {
        ParseResult<TicketExtraction> r = parser.parse(VALID, "stop", TicketExtraction.class);

        assertThat(r.success()).isTrue();
        assertThat(r.rescued()).isFalse();
        assertThat(r.value().severity()).isEqualTo(Severity.P1);
        assertThat(r.value().steps()).containsExactly("看错误日志", "查连接池");
    }

    @Test
    void truncationIsDetectedBeforeParsing() {
        // 被截断的 JSON 本身也解析不了；如果不先看 finish_reason，会被误判成 NOT_JSON
        ParseResult<TicketExtraction> r = parser.parse("{\"title\":\"下单接口", "length", TicketExtraction.class);

        assertThat(r.failure()).isEqualTo(FailureKind.TRUNCATED);
        assertThat(r.failure().retryable()).isFalse();
    }

    @Test
    void emptyContent() {
        assertThat(parser.parse("  ", "stop", TicketExtraction.class).failure()).isEqualTo(FailureKind.EMPTY);
        assertThat(parser.parse(null, "stop", TicketExtraction.class).failure()).isEqualTo(FailureKind.EMPTY);
    }

    @Test
    void rescuesJsonWrappedInCodeFenceAndPreamble() {
        String raw = "好的，以下是抽取结果：\n```json\n" + VALID + "\n```\n如有需要请告诉我。";

        ParseResult<TicketExtraction> r = parser.parse(raw, "stop", TicketExtraction.class);

        assertThat(r.success()).isTrue();
        assertThat(r.rescued()).isTrue();
    }

    @Test
    void plainTextIsNotJson() {
        ParseResult<TicketExtraction> r = parser.parse("这个问题比较严重，建议先重启服务。", "stop", TicketExtraction.class);

        assertThat(r.failure()).isEqualTo(FailureKind.NOT_JSON);
    }

    @Test
    void wrongTypeIsSchemaMismatch() {
        // steps 应该是数组，给了字符串
        String raw = VALID.replace("[\"看错误日志\",\"查连接池\"]", "\"看错误日志\"");

        ParseResult<TicketExtraction> r = parser.parse(raw, "stop", TicketExtraction.class);

        assertThat(r.failure()).isEqualTo(FailureKind.SCHEMA_MISMATCH);
        assertThat(r.detail()).contains("steps");
    }

    @Test
    void jsonArrayInsteadOfObjectIsSchemaMismatch() {
        ParseResult<TicketExtraction> r = parser.parse("[" + VALID + "]", "stop", TicketExtraction.class);

        assertThat(r.failure()).isEqualTo(FailureKind.SCHEMA_MISMATCH);
    }

    @Test
    void unknownEnumValueIsInvalidValue() {
        ParseResult<TicketExtraction> r = parser.parse(VALID.replace("\"P1\"", "\"紧急\""), "stop", TicketExtraction.class);

        assertThat(r.failure()).isEqualTo(FailureKind.INVALID_VALUE);
        assertThat(r.detail()).contains("severity");
    }

    @Test
    void missingRequiredFieldIsInvalidValue() {
        // JSON 模式只保证是 JSON，不保证字段齐全 —— 少了 component 要靠校验兜住
        String raw = VALID.replace("\"component\":\"order-service\",", "");

        ParseResult<TicketExtraction> r = parser.parse(raw, "stop", TicketExtraction.class);

        assertThat(r.failure()).isEqualTo(FailureKind.INVALID_VALUE);
        assertThat(r.detail()).contains("component");
    }

    @Test
    void emptyStepsIsInvalidValue() {
        String raw = VALID.replace("[\"看错误日志\",\"查连接池\"]", "[]");

        ParseResult<TicketExtraction> r = parser.parse(raw, "stop", TicketExtraction.class);

        assertThat(r.failure()).isEqualTo(FailureKind.INVALID_VALUE);
        assertThat(r.detail()).contains("steps");
    }

    @Test
    void lenientOnFormatStrictOnMeaning() {
        // 宽容：小写枚举、多出来的字段都放行
        String raw = VALID.replace("\"P1\"", "\"p1\"").replace("{", "{\"confidence\":0.9,");

        ParseResult<TicketExtraction> r = parser.parse(raw, "stop", TicketExtraction.class);

        assertThat(r.success()).isTrue();
        assertThat(r.value().severity()).isEqualTo(Severity.P1);
    }
}
