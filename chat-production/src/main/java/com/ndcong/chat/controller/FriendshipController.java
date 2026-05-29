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
