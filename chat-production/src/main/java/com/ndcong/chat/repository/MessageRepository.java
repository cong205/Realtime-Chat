package com.ndcong.chat.repository;

import com.ndcong.chat.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, UUID> {
    
    // Gọi Stored Procedure nạp lịch sử tin nhắn
    @Query(value = "EXEC sp_LoadMessages @ConversationID = :conversationId, @Page = :page, @PageSize = :pageSize", nativeQuery = true)
    List<Message> loadMessages(@Param("conversationId") UUID conversationId, 
                               @Param("page") int page, 
                               @Param("pageSize") int pageSize);
}