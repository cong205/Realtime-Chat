package com.ndcong.chat.security;

import com.ndcong.chat.entity.User;
import com.ndcong.chat.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy user: " + username));
        return new UserPrincipal(user.getId(), user.getUsername(), user.getPasswordHash());
    }

    // Hàm này dùng cho JwtAuthenticationFilter để giải mã Token
    public UserDetails loadUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy user ID: " + id));
        return new UserPrincipal(user.getId(), user.getUsername(), user.getPasswordHash());
    }
}