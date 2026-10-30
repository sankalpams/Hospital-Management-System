package com.hms.security;

import com.hms.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class JwtService {
    private final AppProperties properties;
    private final SecretKey key;
    public JwtService(AppProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.security().jwtSecret()));
    }
    public String createAccessToken(UUID id, String username, String role, Collection<String> permissions) {
        Instant now = Instant.now();
        return Jwts.builder().subject(id.toString()).claim("username", username).claim("role", role)
            .claim("permissions", permissions).issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(properties.security().accessTokenMinutes() * 60L)))
            .signWith(key).compact();
    }
    @SuppressWarnings("unchecked")
    public HmsPrincipal parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        List<String> permissions = claims.get("permissions", List.class);
        List<GrantedAuthority> authorities = permissions.stream().map(SimpleGrantedAuthority::new).map(a -> (GrantedAuthority) a).toList();
        return new HmsPrincipal(UUID.fromString(claims.getSubject()), claims.get("username", String.class),
            claims.get("role", String.class), authorities);
    }
}
