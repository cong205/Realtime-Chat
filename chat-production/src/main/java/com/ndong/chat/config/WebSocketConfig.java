package com.ndcong.chat.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // /topic dùng cho chat group, /queue dùng cho chat 1-1
        registry.enableSimpleBroker("/topic", "/queue");
        
        // Các tin nhắn gửi từ client lên server sẽ bắt đầu bằng /app
        registry.setApplicationDestinationPrefixes("/app");
        
        // Định tuyến tin nhắn tới user cụ thể (Chat 1-1)
        registry.setUserDestinationPrefix("/user");
    }
}