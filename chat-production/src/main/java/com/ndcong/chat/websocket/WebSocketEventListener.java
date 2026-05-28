package com.ndcong.chat.websocket;

import com.ndcong.chat.entity.User;
import com.ndcong.chat.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class WebSocketEventListener {

    @Autowired
    private SimpMessageSendingOperations messagingTemplate;

    @Autowired
    private UserRepository userRepository;

    // Bắt sự kiện khi Client kết nối WebSocket thành công (Mở App)
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        // Giả sử Token JWT đã được parse và set tên/ID vào Principal
        if (event.getUser() != null) {
            String userId = event.getUser().getName();
            
            // Cập nhật DB: Trạng thái Online
            userRepository.findById(UUID.fromString(userId)).ifPresent(user -> {
                user.setIsOnline(true);
                userRepository.save(user);
                
                // Broadcast cho mọi người biết user này vừa Online
                messagingTemplate.convertAndSend("/topic/public", user.getFullName() + " vừa online");
            });
        }
    }

    // Bắt sự kiện khi Client ngắt kết nối (Tắt App / Rớt mạng)
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        if (event.getUser() != null) {
            String userId = event.getUser().getName();
            
            // Cập nhật DB: Trạng thái Offline và cập nhật LastSeen
            userRepository.findById(UUID.fromString(userId)).ifPresent(user -> {
                user.setIsOnline(false);
                user.setLastSeen(LocalDateTime.now());
                userRepository.save(user);
                
                // Broadcast cho mọi người biết user này vừa Offline
                messagingTemplate.convertAndSend("/topic/public", user.getFullName() + " đã offline");
            });
        }
    }
}