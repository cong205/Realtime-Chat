package com.ndcong.chat.service;

import com.ndcong.chat.entity.RefreshToken;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenService {
    RefreshToken createRefreshToken(UUID userId);
    Optional<RefreshToken> findByToken(String token);
    void deleteByUserId(UUID userId);
    RefreshToken verifyExpiration(RefreshToken token);
}
