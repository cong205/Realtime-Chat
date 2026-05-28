package com.ndcong.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "MessageAttachments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MessageAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "AttachmentID")
    private UUID id;

    @Column(name = "MessageID", nullable = false)
    private UUID messageId;

    @Column(name = "FileName")
    private String fileName;

    @Column(name = "FileUrl")
    private String fileUrl;

    @Column(name = "FileType") // image, video, file
    private String fileType;

    @Column(name = "MimeType") // image/jpeg, application/pdf...
    private String mimeType;

    @Column(name = "FileSize")
    private Long fileSize;

    @CreationTimestamp
    @Column(name = "UploadedAt", updatable = false)
    private LocalDateTime uploadedAt;
}