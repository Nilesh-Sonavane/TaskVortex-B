package com.taskvortex.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class JwtResponse {
    private String token;
    private String email;
    private String role;

    // --- ADD THESE NEW FIELDS ---
    private Long id;
    private String firstName;
    private String lastName;
    private String jobTitle;
}