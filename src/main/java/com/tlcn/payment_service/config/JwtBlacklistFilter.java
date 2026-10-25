package com.tlcn.payment_service.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import lombok.extern.slf4j.Slf4j;

/**
 * JWT Blacklist Filter - Checks if a token has been blacklisted during logout.
 * Stores tokens as SHA-256 hashes in Redis for security.
 */
@Slf4j
public class JwtBlacklistFilter extends OncePerRequestFilter {

    private static final String UNIFIED_BLACKLIST_KEY_PREFIX = "token:blacklist:";
    private static final String LEGACY_BLACKLIST_KEY_PREFIX = "payment:token:blacklist:";

    private final RedisTemplate<String, String> redisTemplate;

    public JwtBlacklistFilter(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String accessToken = authHeader.replace("Bearer ", "").trim();
            String tokenHash = sha256Hex(accessToken);

            String unifiedKey = UNIFIED_BLACKLIST_KEY_PREFIX + tokenHash;
            String legacyKey = LEGACY_BLACKLIST_KEY_PREFIX + tokenHash;

            if (Boolean.TRUE.equals(redisTemplate.hasKey(unifiedKey)) || Boolean.TRUE.equals(redisTemplate.hasKey(legacyKey))) {
                log.warn("Attempt to use blacklisted token: {}...", accessToken.substring(0, Math.min(20, accessToken.length())));

                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json; charset=UTF-8");
                response.getWriter().write(
                    "{\"success\": false, \"message\": \"Token is blacklisted\", \"errorCode\": \"INVALID_TOKEN\", \"data\": null}"
                );
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (Exception ex) {
            // Fail-open: if hashing fails, don't block requests.
            return "";
        }
    }
}
