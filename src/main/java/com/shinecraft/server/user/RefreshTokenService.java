package com.shinecraft.server.user;

import com.shinecraft.server.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 48;

    private final RefreshTokenRepository refreshTokenRepository;
    private final long expirationDays;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${app.jwt.refresh-expiration-days:30}") long expirationDays) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.expirationDays = expirationDays;
    }

    @Transactional
    public String create(User user) {
        String rawToken;
        String tokenHash;
        do {
            rawToken = randomToken();
            tokenHash = hash(rawToken);
        } while (refreshTokenRepository.existsByTokenHash(tokenHash));

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(expirationDays));
        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    @Transactional
    public RotatedRefreshToken rotate(String rawRefreshToken) {
        LocalDateTime now = LocalDateTime.now();
        RefreshToken currentToken = requireUsableToken(rawRefreshToken, now);
        String nextRawToken = create(currentToken.getUser());
        currentToken.setRevokedAt(now);
        currentToken.setReplacedByTokenHash(hash(nextRawToken));
        return new RotatedRefreshToken(currentToken.getUser(), nextRawToken);
    }

    @Transactional
    public void revoke(String rawRefreshToken) {
        LocalDateTime now = LocalDateTime.now();
        refreshTokenRepository
                .findByTokenHashForUpdate(hash(rawRefreshToken))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> token.setRevokedAt(now));
    }

    private RefreshToken requireUsableToken(String rawRefreshToken, LocalDateTime now) {
        RefreshToken refreshToken = refreshTokenRepository
                .findByTokenHashForUpdate(hash(rawRefreshToken))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token is invalid"));
        if (refreshToken.isRevoked()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token has been revoked");
        }
        if (refreshToken.isExpired(now)) {
            refreshToken.setRevokedAt(now);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token has expired");
        }
        if (!refreshToken.getUser().isActive()) {
            refreshToken.setRevokedAt(now);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "User account is inactive");
        }
        return refreshToken;
    }

    private String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is required", exception);
        }
    }

    public record RotatedRefreshToken(User user, String refreshToken) {}
}
