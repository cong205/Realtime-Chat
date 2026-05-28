package com.ndcong.chat.dto.message;

import lombok.Data;
import java.util.UUID;

@Data
public class ChatMessageRequest {
    private UUID conversationId;
    private UUID senderId;
    private String content;
    private String messageType; // text, image, file
}