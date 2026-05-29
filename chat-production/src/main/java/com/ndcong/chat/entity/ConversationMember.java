package com.ndcong.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ConversationMembers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ConversationMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ConversationMemberID")
    private UUID id;

    @Column(name = "ConversationID", nullable = false)
    private UUID conversationId;

    @Column(name = "UserID", nullable = false)
    private UUID userId;

    @Column(name = "Role")
    private String role;

    @CreationTimestamp
    @Column(name = "JoinedAt", updatable = false)
    private LocalDateTime joinedAt;
}
