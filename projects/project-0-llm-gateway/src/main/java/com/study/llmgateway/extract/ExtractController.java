package com.study.llmgateway.extract;

import com.study.llmgateway.structured.StructuredOutputException;
import com.study.llmgateway.structured.StructuredResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/extract")
public class ExtractController {

    private final TicketExtractor ticketExtractor;

    public ExtractController(TicketExtractor ticketExtractor) {
        this.ticketExtractor = ticketExtractor;
    }

    public record ExtractRequest(String text) {
    }

    @PostMapping("/ticket")
    public ResponseEntity<?> ticket(@RequestBody ExtractRequest request) {
        if (request == null || request.text() == null || request.text().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "text 不能为空"));
        }
        StructuredResult<TicketExtraction> result = ticketExtractor.extract(request.text());
        return ResponseEntity.ok(result);
    }

    /**
     * 模型给不出合格结果时返回 502：不是调用方的错（不该是 4xx），也不是我们的 bug（不该是 500），
     * 是上游给的东西不能用。带上失败类型，调用方能据此决定要不要稍后再试。
     */
    @ExceptionHandler(StructuredOutputException.class)
    public ResponseEntity<Map<String, Object>> onStructuredFailure(StructuredOutputException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", e.getMessage(),
                "kind", e.kind(),
                "attempts", e.attempts(),
                "failures", List.copyOf(e.failures())));
    }
}
