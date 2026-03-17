package com.taskvortex.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.taskvortex.dto.ChangePasswordDTO;
import com.taskvortex.dto.UserRequestDTO;
import com.taskvortex.dto.UserResponseDTO;
import com.taskvortex.entity.Department;
import com.taskvortex.entity.User;
import com.taskvortex.entity.UserRole;
import com.taskvortex.repository.DepartmentRepository;
import com.taskvortex.repository.UserRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    // 1. ADD THIS INJECTION
    private final FileStorageService fileStorageService;

    // --- 1. CREATE USER ---
    @Transactional
    public User createUser(UserRequestDTO dto, String performerEmail) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already exists");
        }

        Department dept = departmentRepository.findByName(dto.getDepartment())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found"));

        User user = User.builder()
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .email(dto.getEmail())
                .department(dept)
                .jobTitle(dto.getJobTitle())
                .role(UserRole.valueOf(dto.getRole().toUpperCase()))
                .password(passwordEncoder.encode(dto.getPassword()))
                .build();

        User savedUser = userRepository.save(user);

        String fullName = capitalizeName(savedUser.getFirstName() + " " + savedUser.getLastName());
        StringBuilder logBuilder = new StringBuilder("<ul class='audit-list'>");
        logBuilder.append("<li><i class='fa-solid fa-user-plus text-success me-1'></i> ")
                .append("<b>Action:</b> New user profile registered for <b>")
                .append(fullName).append("</b></li>")
                .append("<li><b>Role:</b> <span class='badge-new'>")
                .append(savedUser.getRole()).append("</span></li>")
                .append("<li><b>Department:</b> ")
                .append(dept.getName()).append("</li>")
                .append("</ul>");

        auditService.logAction("USERS", "USER_REGISTRATION", savedUser.getId(), logBuilder.toString(), performerEmail);
        return savedUser;
    }

    // --- NEW: GET USER BY ID ---
    public UserResponseDTO getUserById(Long id) {
        return userRepository.findById(id)
                .map(this::mapToDTO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    // --- NEW: UPDATE USER ---
    @Transactional
    public User updateUser(Long id, UserRequestDTO dto, String performerEmail) {
        User existing = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        StringBuilder logBuilder = new StringBuilder("<ul class='audit-list'>");

        autoCompareUsers(logBuilder, "First Name", existing.getFirstName(), dto.getFirstName());
        autoCompareUsers(logBuilder, "Last Name", existing.getLastName(), dto.getLastName());

        UserRole newRole = UserRole.valueOf(dto.getRole().toUpperCase());
        autoCompareUsers(logBuilder, "Role", existing.getRole().name(), newRole.name());

        if (!existing.getDepartment().getName().equals(dto.getDepartment())) {
            Department newDept = departmentRepository.findByName(dto.getDepartment())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found"));
            autoCompareUsers(logBuilder, "Department", existing.getDepartment().getName(), newDept.getName());
            existing.setDepartment(newDept);
        }

        autoCompareUsers(logBuilder, "Job Title", existing.getJobTitle(), dto.getJobTitle());

        existing.setFirstName(dto.getFirstName());
        existing.setLastName(dto.getLastName());
        existing.setRole(newRole);
        existing.setJobTitle(dto.getJobTitle());

        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            existing.setPassword(passwordEncoder.encode(dto.getPassword()));
            logBuilder.append(
                    "<li><i class='fa-solid fa-key text-warning me-1'></i> <b>Security:</b> Password was reset</li>");
        }

        if (logBuilder.toString().contains("<li>")) {
            logBuilder.append("</ul>");
            String targetHeader = "<b>[Profile Update: " + existing.getEmail() + "]</b> ";
            auditService.logAction("USERS", "USER_UPDATED", id, targetHeader + logBuilder.toString(), performerEmail);
        }

        return userRepository.save(existing);
    }

    @Transactional
    public void toggleUserStatus(Long id, String performerEmail) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        User performer = userRepository.findByEmail(performerEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Performer not found"));

        user.setActive(!user.isActive());
        userRepository.save(user);

        String status = user.isActive() ? "Activated" : "Deactivated";
        String color = user.isActive() ? "text-success" : "text-danger";

        String logDetails = String.format(
                "<ul class='audit-list'><li><i class='fa-solid fa-power-off %s me-1'></i> <b>Status Change:</b> User <b>%s</b> was %s</li></ul>",
                color, user.getEmail(), status);

        auditService.logAction("USERS", "USER_STATUS_TOGGLE", id, logDetails, performer);
    }

    // --- HELPER: Auto Compare ---
    private void autoCompareUsers(StringBuilder builder, String field, String oldVal, String newVal) {
        String safeOld = (oldVal == null) ? "" : oldVal.trim();
        String safeNew = (newVal == null) ? "" : newVal.trim();

        if (!safeOld.equalsIgnoreCase(safeNew)) {
            builder.append(String.format(
                    "<li><b>%s:</b> <span class='badge-old'>%s</span> <i class='fa-solid fa-arrow-right mx-1'></i> <span class='badge-new'>%s</span></li>",
                    field, safeOld.isEmpty() ? "Empty" : safeOld, safeNew.isEmpty() ? "Empty" : safeNew));
        }
    }

    public List<UserResponseDTO> getAllUsers() {
        return userRepository.findAll().stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    public List<UserResponseDTO> findUsersByRole(String role) {
        try {
            UserRole enumRole = UserRole.valueOf(role.toUpperCase());
            return userRepository.findByRole(enumRole).stream().map(this::mapToDTO).collect(Collectors.toList());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role: " + role);
        }
    }

    // 2. ADD THIS METHOD: Update Personal Profile
    @Transactional
    public User updateMyProfile(String email, UserRequestDTO dto) {
        User existing = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        StringBuilder logBuilder = new StringBuilder("<ul class='audit-list'>");

        autoCompareUsers(logBuilder, "First Name", existing.getFirstName(), dto.getFirstName());
        autoCompareUsers(logBuilder, "Last Name", existing.getLastName(), dto.getLastName());
        autoCompareUsers(logBuilder, "Phone", existing.getPhone(), dto.getPhone());
        autoCompareUsers(logBuilder, "Location", existing.getLocation(), dto.getLocation());

        String oldBio = existing.getBio() == null ? "" : existing.getBio();
        String newBio = dto.getBio() == null ? "" : dto.getBio();
        if (!oldBio.equals(newBio)) {
            logBuilder.append("<li><b>Bio:</b> Profile bio was updated.</li>");
        }

        existing.setFirstName(dto.getFirstName());
        existing.setLastName(dto.getLastName());
        existing.setPhone(dto.getPhone());
        existing.setBio(dto.getBio());
        existing.setLocation(dto.getLocation());

        if (logBuilder.toString().contains("<li>")) {
            logBuilder.append("</ul>");
            auditService.logAction("PROFILE", "PROFILE_UPDATED", existing.getId(),
                    "<b>[Self Update]</b> " + logBuilder.toString(), email);
        }

        return userRepository.save(existing);
    }

    // 3. ADD THIS METHOD: Upload Avatar Image
    // --- 8. UPLOAD AVATAR ---
    @Transactional
    public String updateProfileImage(String email, MultipartFile file) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // NEW: Check for existing image and delete it from the server
        if (user.getProfileUrl() != null) {
            fileStorageService.deleteProfileFile(user.getProfileUrl());
        }

        // Proceed to save the new image
        String fileName = fileStorageService.storeProfileFile(file);
        String fileUrl = "/profiles/" + fileName;

        user.setProfileUrl(fileUrl);
        userRepository.save(user);

        auditService.logAction("PROFILE", "AVATAR_UPDATED", user.getId(), "User updated their profile picture.", email);

        return fileUrl;
    }

    // --- 9. CHANGE PASSWORD ---
    @Transactional
    public void changePassword(String email, ChangePasswordDTO dto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // 1. Verify the current password
        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
            // Throw a 400 Bad Request if the current password doesn't match
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Incorrect current password");
        }

        // 2. Encode and set the new password
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);

        // 3. Log the security event
        auditService.logAction("PROFILE", "PASSWORD_CHANGED", user.getId(),
                "<li><i class='fa-solid fa-shield-halved text-warning me-1'></i> <b>Security:</b> User updated their account password.</li>",
                email);
    }

    // 4. UPDATE DTO MAPPING: Add new fields
    private UserResponseDTO mapToDTO(User user) {
        return UserResponseDTO.builder()
                .id(user.getId())
                .firstName(capitalizeName(user.getFirstName()))
                .lastName(capitalizeName(user.getLastName()))
                .email(user.getEmail())
                .department(user.getDepartment() != null ? user.getDepartment().getName() : "N/A")
                .jobTitle(user.getJobTitle())
                .role(user.getRole().name())
                .active(user.isActive())
                .phone(user.getPhone())
                .bio(user.getBio())
                .location(user.getLocation())
                .profileUrl(user.getProfileUrl())
                .build();
    }

    private String capitalizeName(String name) {
        if (name == null || name.isBlank())
            return "";
        String[] words = name.toLowerCase().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    // Fetch user by email directly from the security token
    public UserResponseDTO getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(this::mapToDTO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}