package com.ndcong.chat.controller;

import com.ndcong.chat.dto.auth.LoginRequest;
import com.ndcong.chat.dto.auth.AuthResponse;
import com.ndcong.chat.entity.RefreshToken;
import com.ndcong.chat.repository.UserRepository;
import com.ndcong.chat.service.RefreshTokenService;
import com.ndcong.chat.entity.User;
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

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private UserRepository userRepository;

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

        // 4. Tạo refresh token và trả về cùng access token
        com.ndcong.chat.security.UserPrincipal principal = (com.ndcong.chat.security.UserPrincipal) authentication.getPrincipal();
        java.util.UUID userId = principal.getId();
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(userId);

        java.util.Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("accessToken", jwt);
        resp.put("tokenType", "Bearer");
        resp.put("refreshToken", refreshToken.getToken());
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody java.util.Map<String, String> body) {
        String token = body.get("refreshToken");
        if (token == null) return ResponseEntity.badRequest().body("refreshToken is required");
        java.util.Optional<RefreshToken> opt = refreshTokenService.findByToken(token);
        if (opt.isEmpty()) return ResponseEntity.badRequest().body("Invalid refresh token");
        RefreshToken refreshToken = refreshTokenService.verifyExpiration(opt.get());
        String newAccessToken = tokenProvider.generateTokenFromUserId(refreshToken.getUserId().toString());
        java.util.Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("accessToken", newAccessToken);
        resp.put("tokenType", "Bearer");
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody java.util.Map<String, String> body) {
        String token = body.get("refreshToken");
        if (token != null) {
            java.util.Optional<RefreshToken> opt = refreshTokenService.findByToken(token);
            opt.ifPresent(t -> refreshTokenService.deleteByUserId(t.getUserId()));
        }
        return ResponseEntity.ok().build();
    }
}