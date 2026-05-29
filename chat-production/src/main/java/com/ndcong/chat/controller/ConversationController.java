package com.ndcong.chat.controller;

import com.ndcong.chat.entity.Conversation;
import com.ndcong.chat.entity.Message;
import com.ndcong.chat.entity.ConversationMember;
import com.ndcong.chat.repository.ConversationRepository;
import com.ndcong.chat.repository.MessageRepository;
import com.ndcong.chat.repository.ConversationMemberRepository;
import com.ndcong.chat.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ConversationMemberRepository memberRepository;

    // 1. Lấy danh sách các phòng chat của User hiện tại (Để vẽ cột bên trái)
    @GetMapping
    public ResponseEntity<?> getMyConversations(@AuthenticationPrincipal UserPrincipal currentUser) {
        // Trả về các conversation mà user này là thành viên (Sắp xếp theo UpdatedAt giảm dần)
        // Lưu ý: Cần viết thêm câu Query trong ConversationRepository
        List<Conversation> conversations = conversationRepository.findByMembers_UserIdOrderByUpdatedAtDesc(currentUser.getId());
        return ResponseEntity.ok(conversations);
    }

    // 2. Lấy lịch sử tin nhắn của một phòng chat cụ thể (Phân trang - Pagination)
    // Chức năng này cực kỳ quan trọng để load tin nhắn cũ khi cuộn chuột lên trên
    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<?> getChatHistory(
            @PathVariable UUID conversationId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
            
        // Gọi Stored Procedure sp_LoadMessages mà chúng ta đã định nghĩa ở Phase 1
        List<Message> history = messageRepository.loadMessages(conversationId, page, pageSize);
        return ResponseEntity.ok(history);
    }

    // Tạo conversation mới
    @PostMapping
    public ResponseEntity<?> createConversation(@RequestBody java.util.Map<String, Object> body,
                                                @org.springframework.security.core.annotation.AuthenticationPrincipal UserPrincipal currentUser) {
        String name = body.getOrDefault("conversationName", "").toString();
        boolean isGroup = Boolean.parseBoolean(String.valueOf(body.getOrDefault("isGroup", "false")));
        Conversation c = Conversation.builder()
                .conversationName(name)
                .isGroup(isGroup)
                .createdBy(currentUser != null ? currentUser.getId() : null)
                .build();
        Conversation saved = conversationRepository.save(c);

        // Add creator as member
        if (currentUser != null) {
            ConversationMember cm = ConversationMember.builder()
                    .conversationId(saved.getId())
                    .userId(currentUser.getId())
                    .role("OWNER")
                    .build();
            memberRepository.save(cm);
        }
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<?> getConversation(@PathVariable UUID conversationId) {
        return conversationRepository.findById(conversationId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{conversationId}/members")
    public ResponseEntity<?> addMember(@PathVariable UUID conversationId, @RequestBody java.util.Map<String, String> body) {
        String userIdStr = body.get("userId");
        if (userIdStr == null) return ResponseEntity.badRequest().body("userId required");
        java.util.UUID userId = java.util.UUID.fromString(userIdStr);
        ConversationMember cm = ConversationMember.builder()
                .conversationId(conversationId)
                .userId(userId)
                .role(body.getOrDefault("role", "MEMBER"))
                .build();
        memberRepository.save(cm);
        return ResponseEntity.ok(cm);
    }

    @DeleteMapping("/{conversationId}/members/{memberId}")
    public ResponseEntity<?> removeMember(@PathVariable UUID conversationId, @PathVariable UUID memberId) {
        java.util.Optional<ConversationMember> opt = memberRepository.findById(memberId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        ConversationMember cm = opt.get();
        if (!cm.getConversationId().equals(conversationId)) return ResponseEntity.badRequest().body("Member does not belong to conversation");
        memberRepository.deleteById(memberId);
        return ResponseEntity.ok().build();
    }
}