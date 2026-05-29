package com.ndcong.chat.service.impl;

import com.ndcong.chat.dto.message.ChatMessageRequest;
import com.ndcong.chat.entity.Message;
import com.ndcong.chat.repository.MessageRepository;
import com.ndcong.chat.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import com.ndcong.chat.repository.ConversationMemberRepository;
import com.ndcong.chat.repository.NotificationRepository;
import com.ndcong.chat.repository.UserRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class MessageServiceImpl implements MessageService {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ConversationMemberRepository memberRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

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
                
        Message saved = messageRepository.save(msg);

        // After saving the message, create notifications for other members of the conversation
        try{
            java.util.List<com.ndcong.chat.entity.ConversationMember> members = memberRepository.findByConversationId(saved.getConversationId());
            String preview = saved.getContent() != null ? (saved.getContent().length() > 80 ? saved.getContent().substring(0,77) + "..." : saved.getContent()) : "[file]";
            // Find sender's username for payload
            String senderName = String.valueOf(saved.getSenderId());
            try{ java.util.Optional<com.ndcong.chat.entity.User> su = userRepository.findById(saved.getSenderId()); if (su.isPresent()) senderName = su.get().getUsername(); }catch(Exception ignored){}

            for(com.ndcong.chat.entity.ConversationMember cm : members){
                if (cm.getUserId() == null) continue;
                if (cm.getUserId().equals(saved.getSenderId())) continue; // don't notify sender
                com.ndcong.chat.entity.Notification n = com.ndcong.chat.entity.Notification.builder()
                        .userId(cm.getUserId())
                        .type("MESSAGE")
                        .payload(String.format("{\"conversationId\":\"%s\",\"fromId\":\"%s\",\"from\":\"%s\",\"preview\":\"%s\"}", saved.getConversationId(), saved.getSenderId(), senderName, preview.replace("\"","'")))
                        .build();
                com.ndcong.chat.entity.Notification savedN = notificationRepository.save(n);
                try{
                    java.util.Optional<com.ndcong.chat.entity.User> ru = userRepository.findById(cm.getUserId());
                    if (ru.isPresent()){
                        String targetUsername = ru.get().getUsername();
                        messagingTemplate.convertAndSendToUser(targetUsername, "/queue/notifications", savedN);
                    }
                }catch(Exception ignore){}
            }
        }catch(Exception e){ /* don't break message send on notification errors */ }

        return saved;
    }
}