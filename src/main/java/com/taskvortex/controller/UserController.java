package com.taskvortex.controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.taskvortex.dto.ChangePasswordDTO;
import com.taskvortex.dto.UserRequestDTO;
import com.taskvortex.dto.UserResponseDTO;
import com.taskvortex.entity.User;
import com.taskvortex.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class UserController {

    private final UserService userService;

    @PostMapping("/add")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<User> addNewUser(@RequestBody UserRequestDTO userDTO, Principal principal) {
        return ResponseEntity.ok(userService.createUser(userDTO, principal.getName()));
    }

    // --- NEW: GET USER BY ID ---
    @GetMapping("/{id}")
    // @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<UserResponseDTO> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    // --- NEW: UPDATE USER ---
    @PutMapping("/update/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody UserRequestDTO userDTO,
            Principal principal) {
        return ResponseEntity.ok(userService.updateUser(id, userDTO, principal.getName()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER' , 'EMPLOYEE')")
    public ResponseEntity<List<UserResponseDTO>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/role/{role}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<UserResponseDTO>> getUsersByRole(@PathVariable String role) {
        return ResponseEntity.ok(userService.findUsersByRole(role));
    }

    @PatchMapping("/toggle-status/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> toggleUserStatus(@PathVariable Long id, Principal principal) {
        // principal.getName() provides the admin email for the audit log
        userService.toggleUserStatus(id, principal.getName());
        return ResponseEntity.ok().build();
    }

    // 1. ADD THIS ENDPOINT: Update Profile Data
    @PutMapping("/profile")
    public ResponseEntity<User> updateProfile(@Valid @RequestBody UserRequestDTO updatedData, Principal principal) {
        User savedUser = userService.updateMyProfile(principal.getName(), updatedData);
        return ResponseEntity.ok(savedUser);
    }

    // 2. ADD THIS ENDPOINT: Upload Image
    @PostMapping("/profile/image")
    public ResponseEntity<Map<String, String>> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            Principal principal) {

        String fileUrl = userService.updateProfileImage(principal.getName(), file);
        return ResponseEntity.ok(Map.of("profileUrl", fileUrl));
    }

    // 3. ADD THIS ENDPOINT: Change Password
    @PutMapping("/profile/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto, Principal principal) {
        userService.changePassword(principal.getName(), dto);
        return ResponseEntity.ok().build();
    }

    // --- NEW: GET CURRENT LOGGED-IN USER ---
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()") // Anyone who is logged in can access this
    public ResponseEntity<UserResponseDTO> getCurrentUser(Principal principal) {
        // principal.getName() safely gets the email from the JWT token
        return ResponseEntity.ok(userService.getUserByEmail(principal.getName()));
    }
}