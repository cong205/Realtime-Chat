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

    // Find 1-1 conversation that contains exactly two users (userA and userB) and is not a group
    @Query(value = "SELECT c.* FROM Conversations c WHERE ISNULL(c.IsGroup, 0) = 0 AND c.ConversationID IN (" +
            " SELECT cm.ConversationID FROM ConversationMembers cm WHERE cm.UserID IN (:a, :b) GROUP BY cm.ConversationID HAVING COUNT(DISTINCT cm.UserID) = 2" +
            ")", nativeQuery = true)
    List<Conversation> findOneToOneConversation(@Param("a") UUID a, @Param("b") UUID b);
}