package com.ndcong.chat.repository;

import com.ndcong.chat.entity.MessageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface MessageStatusRepository extends JpaRepository<MessageStatus, UUID> {
    List<MessageStatus> findByMessageId(UUID messageId);
}
