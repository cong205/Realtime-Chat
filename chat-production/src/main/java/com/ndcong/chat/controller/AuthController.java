package com.ndcong.chat.controller;

import com.ndcong.chat.dto.auth.LoginRequest;
import com.ndcong.chat.dto.auth.AuthResponse;
import com.ndcong.chat.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest) {
        
        // 1. Spring Security tự động kiểm tra username và hash password trong database
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                )
        );

        // 2. Set quyền truy cập
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 3. Phát sinh JWT
        String jwt = tokenProvider.generateToken(authentication);
        
        // 4. Trả về cho Client để lưu vào localStorage hoặc HttpOnly Cookie
        return ResponseEntity.ok(new AuthResponse(jwt, "Bearer"));
    }
}