package com.ndcong.chat.service.impl;

import com.ndcong.chat.dto.message.ChatMessageRequest;
import com.ndcong.chat.entity.Message;
import com.ndcong.chat.repository.MessageRepository;
import com.ndcong.chat.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class MessageServiceImpl implements MessageService {

    @Autowired
    private MessageRepository messageRepository;

    @Override
    public Message processAndSaveMessage(ChatMessageRequest request) {
        // Trong thực tế sẽ gọi procedure, ở đây demo lưu Entity để map về WebSocket Controller
        Message msg = Message.builder()
                .conversationId(request.getConversationId())
                .senderId(request.getSenderId())
                .content(request.getContent())
                .messageType(request.getMessageType() != null ? request.getMessageType() : "text")
                .sentAt(LocalDateTime.now())
                .build();
                
        return messageRepository.save(msg);
    }
}