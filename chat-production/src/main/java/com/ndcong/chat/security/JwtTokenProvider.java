package com.ndcong.chat.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtTokenProvider {

    // Khóa bí mật dùng để ký JWT (cần dài ít nhất 256-bit / 32 ký tự để đảm bảo an toàn)
    @Value("${app.jwtSecret:MySuperSecretKeyForChatApplicationWhichIsVeryLongAndSecure123456}")
    private String jwtSecret;

    // Thời gian sống của Token (vd: 24 giờ = 86400000 ms)
    @Value("${app.jwtExpirationMs:86400000}")
    private int jwtExpirationMs;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    // 1. Tạo JWT khi đăng nhập thành công
    public String generateToken(Authentication authentication) {
        // Lấy thông tin user hiện tại (sau khi cast)
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .setSubject(userPrincipal.getId().toString()) // Payload: Lưu UserID
                .setIssuedAt(new Date())
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256) // Chữ ký HMAC-SHA256
                .compact();
    }

    // 2. Lấy UserID từ JWT
    public String getUserIdFromJWT(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

        return claims.getSubject();
    }

    // 3. Xác thực tính hợp lệ của Token (chống sửa đổi)
    public boolean validateToken(String authToken) {
        try {
            Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(authToken);
            return true;
        } catch (SecurityException | MalformedJwtException | ExpiredJwtException | UnsupportedJwtException | IllegalArgumentException ex) {
            System.err.println("Token không hợp lệ hoặc đã hết hạn: " + ex.getMessage());
        }
        return false;
    }
}