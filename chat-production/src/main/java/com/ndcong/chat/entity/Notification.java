package com.ndcong.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "Notifications")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "NotificationID")
    private UUID id;

    @Column(name = "UserID", nullable = false)
    private UUID userId;

    @Column(name = "Type")
    private String type;

    @Column(name = "Payload")
    private String payload;

    @Column(name = "IsRead")
    @Builder.Default
    private Boolean isRead = false;

    @CreationTimestamp
    @Column(name = "CreatedAt", updatable = false)
    private LocalDateTime createdAt;
}
