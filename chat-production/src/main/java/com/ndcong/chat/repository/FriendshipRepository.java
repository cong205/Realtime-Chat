package com.ndcong.chat.repository;

import com.ndcong.chat.entity.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {
    List<Friendship> findByRequesterIdOrResponderId(UUID requesterId, UUID responderId);
}
