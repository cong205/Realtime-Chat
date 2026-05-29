package com.ndcong.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "Friendships")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Friendship {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "FriendshipID")
    private UUID id;

    @Column(name = "RequesterID", nullable = false)
    private UUID requesterId;

    @Column(name = "ResponderID", nullable = false)
    private UUID responderId;

    @Column(name = "Status")
    private String status; // PENDING, ACCEPTED, REJECTED

    @CreationTimestamp
    @Column(name = "CreatedAt", updatable = false)
    private LocalDateTime createdAt;
}
