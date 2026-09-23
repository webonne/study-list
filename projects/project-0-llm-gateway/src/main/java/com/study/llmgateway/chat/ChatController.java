package com.study.llmgateway.chat;

import com.study.llmgateway.chat.dto.ChatRequest;
import com.study.llmgateway.chat.dto.ChatResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String sessionId = (request.sessionId() == null || request.sessionId().isBlank())
                ? UUID.randomUUID().toString()
                : request.sessionId();
        return chatService.chat(sessionId, request.message());
    }

    /**
     * 同一套多轮历史，改成 SSE 逐段返回。
     * 事件：{@code delta} 是增量文本，{@code done} 带 finishReason、首字延迟和总耗时。
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody ChatRequest request) {
        String sessionId = (request.sessionId() == null || request.sessionId().isBlank())
                ? UUID.randomUUID().toString()
                : request.sessionId();
        return chatService.chatStream(sessionId, request.message());
    }

    /** 清空一个会话，调试多轮时很常用。 */
    @DeleteMapping("/{sessionId}")
    public void reset(@PathVariable String sessionId) {
        chatService.reset(sessionId);
    }
}
