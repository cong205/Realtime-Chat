package com.ndcong.chat.service;

import com.ndcong.chat.dto.message.ChatMessageRequest;
import com.ndcong.chat.entity.Message;
import java.util.UUID;

public interface MessageService {
    Message processAndSaveMessage(ChatMessageRequest request);
}