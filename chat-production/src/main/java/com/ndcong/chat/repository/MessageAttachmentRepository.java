package com.ndcong.chat.repository;

import com.ndcong.chat.entity.MessageAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface MessageAttachmentRepository extends JpaRepository<MessageAttachment, UUID> {
}