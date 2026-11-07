package com.example.tracker.security;

import com.example.tracker.model.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Service
public class JwtService {
    private final SecretKey key;
    private final long expirationMs;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-ms}") long expirationMs) {
        // hmacShaKeyFor rejects keys shorter than 256 bits, so a weak secret fails at startup.
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(String username, Role role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim("roles", List.of(role.name()))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    /** Verifies signature and expiry. Throws {@link JwtException} for any invalid token. */
    public VerifiedToken verify(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);
        return new VerifiedToken(claims.getSubject(), roles == null ? List.of() : roles,
                claims.getExpiration().toInstant());
    }

    public record VerifiedToken(String username, List<String> roles, Instant expiresAt) {
        /**
         * The principal is built from the signed claims, with no database lookup per request. Board-level
         * permissions are resolved separately (and cached) by BoardAccessService.
         */
        public Authentication toAuthentication() {
            return new UsernamePasswordAuthenticationToken(username, null,
                    roles.stream().map(SimpleGrantedAuthority::new).toList());
        }
    }
}
