package com.taskvortex.security;

import java.security.Key;
import java.util.Date;
import java.util.HashMap; // Import Map
import java.util.Map; // Import Map

import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims; // Import Claims
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtils {

    private final String jwtSecret = "your_very_long_and_very_secure_secret_key_here_12345";
    private final int jwtExpirationMs = 86400000; // 24 hours

    private Key key() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    // --- 1. UPDATE: Generate Token WITH Role ---
    public String generateTokenFromUsername(String username, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role); // <--- THIS SAVES THE ROLE IN THE TOKEN

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    // --- 2. NEW: Extract Role from Token ---
    public String getRoleFromJwtToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key())
                .build()
                .parseClaimsJws(token)
                .getBody();

        return claims.get("role", String.class); // Reads the "role" we saved
    }

    public String getUserNameFromJwtToken(String token) {
        return Jwts.parserBuilder().setSigningKey(key()).build()
                .parseClaimsJws(token).getBody().getSubject();
    }

    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parserBuilder().setSigningKey(key()).build().parse(authToken);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }
}