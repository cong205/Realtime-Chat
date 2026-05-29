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
    @Autowired
    private com.ndcong.chat.repository.UserRepository userRepository;

    // 1. Lấy danh sách các phòng chat của User hiện tại (Để vẽ cột bên trái)
    @GetMapping
    public ResponseEntity<?> getMyConversations(@AuthenticationPrincipal UserPrincipal currentUser) {
        // Trả về các conversation mà user này là thành viên (Sắp xếp theo UpdatedAt giảm dần)
        List<Conversation> conversations = conversationRepository.findByMembers_UserIdOrderByUpdatedAtDesc(currentUser.getId());
        // Map to DTOs and for 1-1 conversations, show the other user's name as conversationName
        List<java.util.Map<String,Object>> out = new java.util.ArrayList<>();
        for(Conversation c : conversations){
            java.util.Map<String,Object> m = new java.util.HashMap<>();
            m.put("id", c.getId());
            m.put("conversationName", c.getConversationName());
            m.put("isGroup", c.getIsGroup());
            m.put("createdBy", c.getCreatedBy());
            m.put("createdAt", c.getCreatedAt());
            m.put("updatedAt", c.getUpdatedAt());
            // If 1-1 conversation, try to resolve other participant's username
            if (c.getIsGroup() == null || !c.getIsGroup()){
                try{
                    java.util.List<ConversationMember> members = memberRepository.findByConversationId(c.getId());
                    java.util.Optional<ConversationMember> other = members.stream().filter(cm -> !cm.getUserId().equals(currentUser.getId())).findFirst();
                    if (other.isPresent()){
                        java.util.Optional<com.ndcong.chat.entity.User> uo = userRepository.findById(other.get().getUserId());
                        if (uo.isPresent()){
                            m.put("conversationName", uo.get().getUsername());
                            m.put("avatarUrl", uo.get().getAvatarUrl());
                        }
                    }
                }catch(Exception ignored){}
            }
            out.add(m);
        }
        return ResponseEntity.ok(out);
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

        // If client provided memberIds, handle specially for 1-1 (avoid duplicate convs)
        java.util.List<String> memberIds = null;
        if (body.containsKey("memberIds")){
            try{ memberIds = (java.util.List<String>) body.get("memberIds"); }catch(Exception ignored){}
        }

        if (isGroup && (memberIds == null || memberIds.size() < 2)){
            return ResponseEntity.badRequest().body("Groups must include at least 3 people (select at least 2 friends)");
        }

        if (!isGroup && memberIds != null && memberIds.size()==1 && currentUser!=null){
            // Try to find existing 1-1 conversation between currentUser and the provided member
            java.util.UUID friendId = java.util.UUID.fromString(memberIds.get(0));
            java.util.List<Conversation> found = conversationRepository.findOneToOneConversation(currentUser.getId(), friendId);
            if (found != null && !found.isEmpty()){
                // return the first existing conversation
                Conversation existing = found.get(0);
                // set conversationName to the friend's username for 1-1 UI
                try{
                    java.util.Optional<com.ndcong.chat.entity.User> fu = userRepository.findById(friendId);
                    if (fu.isPresent()){
                        existing.setConversationName(fu.get().getUsername());
                        existing.setIsGroup(false);
                        conversationRepository.save(existing);
                    }
                }catch(Exception ignored){}
                return ResponseEntity.ok(existing);
            }
        }

        // Otherwise create a new conversation (group or direct)
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

        // Add provided members
        if (memberIds != null){
            for(String mid : memberIds){
                try{
                    java.util.UUID uid = java.util.UUID.fromString(mid);
                    ConversationMember cm2 = ConversationMember.builder().conversationId(saved.getId()).userId(uid).role("MEMBER").build();
                    memberRepository.save(cm2);
                }catch(Exception ignored){}
            }
        }

        return ResponseEntity.ok(saved);
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<?> getConversation(@PathVariable UUID conversationId) {
        return conversationRepository.findById(conversationId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{conversationId}/members")
    public ResponseEntity<?> getConversationMembers(@PathVariable UUID conversationId){
        java.util.List<ConversationMember> members = memberRepository.findByConversationId(conversationId);
        java.util.List<java.util.Map<String,Object>> out = new java.util.ArrayList<>();
        for(ConversationMember cm : members){
            try{
                java.util.Optional<com.ndcong.chat.entity.User> u = userRepository.findById(cm.getUserId());
                if (u.isPresent()){
                    java.util.Map<String,Object> m = new java.util.HashMap<>();
                    m.put("id", u.get().getId());
                    m.put("username", u.get().getUsername());
                    m.put("fullName", u.get().getFullName());
                    m.put("avatarUrl", u.get().getAvatarUrl());
                    out.add(m);
                }
            }catch(Exception ignored){}
        }
        return ResponseEntity.ok(out);
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