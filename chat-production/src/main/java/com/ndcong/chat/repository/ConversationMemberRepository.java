package com.ndcong.chat.repository;

import com.ndcong.chat.entity.ConversationMember;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, UUID> {
}
