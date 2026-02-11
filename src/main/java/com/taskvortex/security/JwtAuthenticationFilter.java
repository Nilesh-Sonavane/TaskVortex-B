package com.taskvortex.security;

import java.io.IOException;
import java.util.Collections;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String authHeader = request.getHeader("Authorization");
            String token = null;
            String username = null;

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
                username = jwtUtils.getUserNameFromJwtToken(token);
            }

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // 1. Validate Token First
                if (jwtUtils.validateJwtToken(token)) {

                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                    // 2. Extract Role & Add "ROLE_" Prefix
                    // This ensures "ADMIN" becomes "ROLE_ADMIN" for Spring Security
                    String role = jwtUtils.getRoleFromJwtToken(token);
                    if (role == null)
                        role = "EMPLOYEE";

                    // Force Uppercase and Add Prefix
                    String roleAuthority = role.startsWith("ROLE_") ? role : "ROLE_" + role.toUpperCase();

                    SimpleGrantedAuthority authority = new SimpleGrantedAuthority(roleAuthority);

                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            Collections.singleton(authority));

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // 3. Set Context
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            System.err.println("Cannot set user authentication: " + e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}