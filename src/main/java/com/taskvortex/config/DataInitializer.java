package com.taskvortex.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.taskvortex.entity.Department;
import com.taskvortex.entity.User;
import com.taskvortex.entity.UserRole;
import com.taskvortex.repository.DepartmentRepository;
import com.taskvortex.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {

        String deptName = "Administration";

        // Try to find it, or create it if missing
        Department adminDept = departmentRepository.findByName(deptName)
                .orElseGet(() -> {
                    Department newDept = new Department();
                    newDept.setName(deptName);
                    return departmentRepository.save(newDept); // Save and return
                });

        // Check if the database is empty
        if (userRepository.count() == 0) {
            User admin = new User();
            admin.setFirstName("System");
            admin.setLastName("Administrator");
            admin.setEmail("admin@taskvortex.com");
            admin.setDepartment(adminDept);
            admin.setJobTitle("Super Admin");
            admin.setRole(UserRole.ADMIN);

            // Secure the initial password
            admin.setPassword(passwordEncoder.encode("Admin@123"));

            userRepository.save(admin);
            System.out.println(">>> Default Admin User created: admin@taskvortex.com / Admin@123");
        }
    }
}