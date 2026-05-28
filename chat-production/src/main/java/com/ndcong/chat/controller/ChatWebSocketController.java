package com.ndcong.chat.controller;

import com.ndcong.chat.dto.message.ChatMessageRequest;
import com.ndcong.chat.entity.Message;
import com.ndcong.chat.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import java.util.UUID;

@Controller
public class ChatWebSocketController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private MessageService messageService;

    // Client gửi tin vào: /app/chat/{conversationId}
    @MessageMapping("/chat/{conversationId}")
    public void sendMessage(
            @DestinationVariable UUID conversationId,
            @Payload ChatMessageRequest chatRequest) {
        
        // 1. Lưu tin nhắn xuống DB
        chatRequest.setConversationId(conversationId);
        Message savedMessage = messageService.processAndSaveMessage(chatRequest);

        // 2. Broadcast tin nhắn tới những user đang theo dõi conversation này
        // Kênh đích: /topic/conversation/{conversationId}
        messagingTemplate.convertAndSend("/topic/conversation/" + conversationId, savedMessage);
    }
}