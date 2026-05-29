package com.ndcong.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.UpdateTimestamp;
import java.util.UUID;

@Entity
@Table(name = "MessageStatuses")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MessageStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "MessageStatusID")
    private UUID id;

    @Column(name = "MessageID", nullable = false)
    private UUID messageId;

    @Column(name = "UserID", nullable = false)
    private UUID userId;

    @Column(name = "Status")
    private String status; // DELIVERED, READ

    @UpdateTimestamp
    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;
}
