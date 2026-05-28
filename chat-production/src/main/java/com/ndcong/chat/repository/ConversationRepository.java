package com.ndcong.chat.repository;

import com.ndcong.chat.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    
    // Gọi thẳng câu lệnh SQL Server để lấy danh sách phòng chat của User
    @Query(value = "SELECT c.* FROM Conversations c INNER JOIN ConversationMembers cm ON c.ConversationID = cm.ConversationID WHERE cm.UserID = :userId ORDER BY c.UpdatedAt DESC", nativeQuery = true)
    List<Conversation> findByMembers_UserIdOrderByUpdatedAtDesc(@Param("userId") UUID userId);
}