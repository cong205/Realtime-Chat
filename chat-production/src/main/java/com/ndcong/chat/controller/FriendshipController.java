package com.ndcong.chat.controller;

import com.ndcong.chat.entity.Friendship;
import com.ndcong.chat.repository.FriendshipRepository;
import com.ndcong.chat.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/friends")
public class FriendshipController {

    @Autowired
    private FriendshipRepository friendshipRepository;
    @Autowired
    private com.ndcong.chat.repository.NotificationRepository notificationRepository;
    @Autowired
    private com.ndcong.chat.repository.UserRepository userRepository;
    @Autowired
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    @PostMapping("/request")
    public ResponseEntity<?> requestFriend(@RequestBody java.util.Map<String, String> body,
                                           @org.springframework.security.core.annotation.AuthenticationPrincipal UserPrincipal currentUser) {
        String responder = body.get("responderId");
        if (responder == null) return ResponseEntity.badRequest().body("responderId required");
        Friendship f = Friendship.builder()
                .requesterId(currentUser != null ? currentUser.getId() : null)
                .responderId(UUID.fromString(responder))
                .status("PENDING")
                .build();
        Friendship saved = friendshipRepository.save(f);
        // create a notification for the responder so they can accept the friend request
        try{
            java.util.UUID responderId = UUID.fromString(responder);
            String requesterName = currentUser != null ? currentUser.getUsername() : "Someone";
            // Try to enrich with requester full name if available
            try{ java.util.Optional<com.ndcong.chat.entity.User> ru = userRepository.findById(currentUser.getId()); if (ru.isPresent() && ru.get().getFullName()!=null) requesterName = ru.get().getFullName(); }catch(Exception ignored){}
                com.ndcong.chat.entity.Notification n = com.ndcong.chat.entity.Notification.builder()
                    .userId(responderId)
                    .type("FRIEND_REQUEST")
                    .payload(String.format("{\"friendshipId\":\"%s\",\"fromId\":\"%s\",\"from\":\"%s\"}", saved.getId(), currentUser!=null?currentUser.getId():"", requesterName))
                    .build();
            com.ndcong.chat.entity.Notification savedN = notificationRepository.save(n);
            // Try to send real-time notification to the responder if they're connected (use username as principal)
            try{
                java.util.Optional<com.ndcong.chat.entity.User> ru2 = userRepository.findById(responderId);
                if (ru2.isPresent()){
                    String responderUsername = ru2.get().getUsername();
                    messagingTemplate.convertAndSendToUser(responderUsername, "/queue/notifications", savedN);
                }
            }catch(Exception ignore){}
        }catch(Exception ex){ /* don't break friend request if notification fails */ }
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<?> accept(@PathVariable UUID id) {
        return friendshipRepository.findById(id).map(f -> {
            f.setStatus("ACCEPTED");
            friendshipRepository.save(f);
            return ResponseEntity.ok(f);
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable UUID id) {
        return friendshipRepository.findById(id).map(f -> {
            f.setStatus("REJECTED");
            friendshipRepository.save(f);
            return ResponseEntity.ok(f);
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<?> list(@org.springframework.security.core.annotation.AuthenticationPrincipal UserPrincipal currentUser) {
        List<Friendship> list = friendshipRepository.findByRequesterIdOrResponderId(currentUser.getId(), currentUser.getId());
        return ResponseEntity.ok(list);
    }
}
