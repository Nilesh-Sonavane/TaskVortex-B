package com.taskvortex.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.taskvortex.entity.AuditLog;
import com.taskvortex.entity.Department;
import com.taskvortex.entity.User;
import com.taskvortex.repository.AuditLogRepository;
import com.taskvortex.repository.DepartmentRepository;
import com.taskvortex.repository.UserRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;

    // Add this method inside your DepartmentService class
    public Department getById(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Department not found with id: " + id));
    }

    @Transactional
    public Department createDepartment(Department dept, String userEmail) {
        Department saved = departmentRepository.save(dept);

        User performer = userRepository.findByEmail(userEmail).orElse(null);

        // Saving as HTML for consistent UI rendering
        String details = String.format("New Department <b>%s</b> was created.", saved.getName());

        saveLog(performer, "CREATE", "DEPARTMENTS", details);
        return saved;
    }

    @Transactional
    public void deleteDepartment(Long id, String performerEmail) {
        // 1. Fetch department details
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Department not found"));

        // 2. REAL WORLD PROTECTION: Check if employees are still assigned
        // You'll need to add 'existsByDepartmentId' to your UserRepository
        if (userRepository.existsByDepartmentId(id)) {
            throw new RuntimeException("Cannot delete: Employees are still assigned to " + dept.getName());
        }

        // 3. Find who is doing the action
        User performer = userRepository.findByEmail(performerEmail).orElse(null);

        // 4. Delete and Log in HTML
        departmentRepository.deleteById(id);

        String logDetails = String.format("Department <b>%s</b> was permanently deleted.", dept.getName());
        saveLog(performer, "DELETE", "DEPARTMENTS", logDetails);
    }

    @Transactional
    public Department updateDepartment(Long id, Department request, String userEmail) {
        Department existingDept = departmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Department not found"));

        String oldName = existingDept.getName();
        String newName = request.getName();

        // Check if name actually changed
        if (!oldName.equals(newName)) {
            // Validation: Ensure new name isn't taken by another department
            if (departmentRepository.existsByName(newName)) {
                throw new RuntimeException("Department name '" + newName + "' already exists.");
            }

            existingDept.setName(newName);
            Department updated = departmentRepository.save(existingDept);

            // Save HTML Log with a clear change arrow
            User performer = userRepository.findByEmail(userEmail).orElse(null);
            String details = String.format("Department name changed: <b>%s</b> &rarr; <b>%s</b>", oldName, newName);
            saveLog(performer, "UPDATE", "DEPARTMENTS", details);

            return updated;
        }

        return existingDept; // No changes made
    }

    private void saveLog(User user, String action, String entity, String details) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setEntityName(entity);
        log.setDetails(details); // This now contains the <b> tags
        log.setPerformedBy(user);
        log.setTimestamp(LocalDateTime.now());
        auditLogRepository.save(log);
    }

}