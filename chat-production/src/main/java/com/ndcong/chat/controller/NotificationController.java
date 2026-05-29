package com.ndcong.chat.controller;

import com.ndcong.chat.entity.Notification;
import com.ndcong.chat.repository.NotificationRepository;
import com.ndcong.chat.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationRepository notificationRepository;

    @GetMapping
    public ResponseEntity<?> list(@org.springframework.security.core.annotation.AuthenticationPrincipal UserPrincipal currentUser) {
        List<Notification> list = notificationRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId());
        return ResponseEntity.ok(list);
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<?> markRead(@PathVariable UUID id) {
        return notificationRepository.findById(id).map(n -> {
            n.setIsRead(true);
            notificationRepository.save(n);
            return ResponseEntity.ok(n);
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
