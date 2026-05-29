package com.ndcong.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "RefreshTokens")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "RefreshTokenID")
    private UUID id;

    @Column(name = "Token", nullable = false, unique = true)
    private String token;

    @Column(name = "UserID", nullable = false)
    private UUID userId;

    @Column(name = "ExpiryDate")
    private LocalDateTime expiryDate;

    @CreationTimestamp
    @Column(name = "CreatedAt", updatable = false)
    private LocalDateTime createdAt;
}
