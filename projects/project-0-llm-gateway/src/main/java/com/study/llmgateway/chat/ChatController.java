package com.study.llmgateway.chat;

import com.study.llmgateway.chat.dto.ChatRequest;
import com.study.llmgateway.chat.dto.ChatResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    /** 清空一个会话，调试多轮时很常用。 */
    @DeleteMapping("/{sessionId}")
    public void reset(@PathVariable String sessionId) {
        chatService.reset(sessionId);
    }

    // TODO #03 流式：新增 GET /chat/stream，返回 SseEmitter 或 Flux<ServerSentEvent>
    //      别忘了注册 onCompletion / onTimeout 回调关掉上游流，否则客户端断开后还在烧 token
}
