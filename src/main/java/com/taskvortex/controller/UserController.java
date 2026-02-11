package com.taskvortex.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin; // Import this
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.taskvortex.dto.UserRequestDTO;
import com.taskvortex.dto.UserResponseDTO;
import com.taskvortex.entity.User;
import com.taskvortex.service.UserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200") // 1. Allow Angular
public class UserController {

    private final UserService userService;

    @PostMapping("/add")
    // 2. FIX: Change hasAuthority -> hasRole
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<User> addNewUser(@RequestBody UserRequestDTO userDTO) {
        User savedUser = userService.createUser(userDTO);
        return ResponseEntity.ok(savedUser);
    }

    @GetMapping
    // 3. FIX: Change hasAnyAuthority -> hasAnyRole
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<UserResponseDTO>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }
}