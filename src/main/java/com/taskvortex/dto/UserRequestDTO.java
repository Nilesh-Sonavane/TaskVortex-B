package com.taskvortex.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRequestDTO {

    @NotBlank(message = "First name cannot be empty")
    @Size(min = 2, max = 50, message = "First name must be between 2 and 50 characters")
    private String firstName;

    @NotBlank(message = "Last name cannot be empty")
    @Size(min = 2, max = 50, message = "Last name must be between 2 and 50 characters")
    private String lastName;

    @Email(message = "Invalid email format")
    private String email;

    private String password;
    private String department;
    private String jobTitle;
    private String role;

    // --- NEW VALIDATION FOR PROFILE FIELDS ---

    // Allows optional phone, but if provided, must be 7-15 digits/spaces/dashes
    @Pattern(regexp = "^(\\+?[0-9\\s\\-]{7,15})?$", message = "Invalid phone number format")
    private String phone;

    @Size(max = 500, message = "Bio cannot exceed 500 characters")
    private String bio;

    @Size(max = 100, message = "Location cannot exceed 100 characters")
    private String location;
}