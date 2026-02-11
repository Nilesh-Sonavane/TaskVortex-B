package com.taskvortex.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.taskvortex.dto.UserRequestDTO;
import com.taskvortex.dto.UserResponseDTO;
import com.taskvortex.entity.Department;
import com.taskvortex.entity.User;
import com.taskvortex.entity.UserRole;
import com.taskvortex.repository.DepartmentRepository;
import com.taskvortex.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository; // Added
    private final PasswordEncoder passwordEncoder;

    public User createUser(UserRequestDTO dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already exists");
        }

        // Fetch Department entity from DB
        Department dept = departmentRepository.findByName(dto.getDepartment())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found"));

        User user = User.builder()
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .email(dto.getEmail())
                .department(dept) // Set the Entity, not a String
                .jobTitle(dto.getJobTitle())
                .role(UserRole.valueOf(dto.getRole().toUpperCase()))
                .password(passwordEncoder.encode(dto.getPassword()))
                .build();

        return userRepository.save(user);
    }

    public List<UserResponseDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(user -> UserResponseDTO.builder()
                        .id(user.getId())
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .email(user.getEmail())
                        .department(user.getDepartment() != null ? user.getDepartment().getName() : "N/A")
                        .jobTitle(user.getJobTitle())
                        .role(user.getRole().name())
                        .build())
                .collect(Collectors.toList());
    }
}