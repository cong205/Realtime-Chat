package com.ndcong.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "Messages")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "MessageID")
    private UUID id;

    @Column(name = "ConversationID", nullable = false)
    private UUID conversationId;

    @Column(name = "SenderID", nullable = false)
    private UUID senderId;

    @Column(name = "MessageType")
    private String messageType = "text";

    @Column(name = "Content")
    private String content;

    @CreationTimestamp
    @Column(name = "SentAt", updatable = false)
    private LocalDateTime sentAt;
}