package com.taskvortex.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.taskvortex.dto.JwtResponse;
import com.taskvortex.dto.LoginRequest;
import com.taskvortex.entity.User;
import com.taskvortex.repository.UserRepository;
import com.taskvortex.security.JwtUtils;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest) {

        try {
            // 1. Authenticate
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));

            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 2. Fetch User Details from Database
            User user = userRepository.findByEmail(loginRequest.getEmail())
                    .orElseThrow(() -> new RuntimeException("Error: User not found."));

            // 3. Generate Token
            String jwt = jwtUtils.generateTokenFromUsername(user.getEmail(), user.getRole().name());

            // 4. Return Response with ALL details
            // The order here MUST match the order of fields in JwtResponse.java
            return ResponseEntity.ok(new JwtResponse(
                    jwt,
                    user.getEmail(),
                    user.getRole().name(),
                    user.getId(), // <--- ID
                    user.getFirstName(), // <--- First Name
                    user.getLastName(), // <--- Last Name
                    user.getJobTitle() // <--- Job Title
            ));

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid email or password");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An error occurred");
        }
    }
}