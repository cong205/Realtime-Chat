package com.ndcong.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "Conversations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ConversationID")
    private UUID id;

    @Column(name = "ConversationName")
    private String conversationName;

    @Column(name = "IsGroup")
    @Builder.Default
    private Boolean isGroup = false;

    @Column(name = "CreatedBy")
    private UUID createdBy;

    @Column(name = "LastMessageID")
    private UUID lastMessageId;

    @CreationTimestamp
    @Column(name = "CreatedAt", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;
}