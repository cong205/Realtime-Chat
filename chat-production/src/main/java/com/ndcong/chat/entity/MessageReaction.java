package com.ndcong.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "MessageReactions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MessageReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ReactionID")
    private UUID id;

    @Column(name = "MessageID", nullable = false)
    private UUID messageId;

    @Column(name = "UserID", nullable = false)
    private UUID userId;

    @Column(name = "Reaction")
    private String reaction;

    @CreationTimestamp
    @Column(name = "CreatedAt", updatable = false)
    private LocalDateTime createdAt;
}
