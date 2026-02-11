package com.taskvortex.entity;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // <--- IMPORT THIS

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true, length = 10)
    private String projectKey;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String status = "ACTIVE";

    private Integer progress = 0;

    private LocalDate startDate;
    private LocalDate targetEndDate;

    // --- RELATIONSHIPS (FIXED FOR INFINITE LOOP) ---

    // 1. Manager
    @ManyToOne
    @JoinColumn(name = "manager_id", nullable = false)
    // Ignore 'department' inside the user to prevent User -> Dept -> User loop
    // Ignore 'projects' inside the user to prevent Project -> Manager -> Project
    // loop
    @JsonIgnoreProperties({ "department", "projects", "hibernateLazyInitializer", "handler" })
    private User manager;

    // 2. Department
    @ManyToOne
    @JoinColumn(name = "department_id")
    // Ignore 'projects' list inside Department to prevent Project -> Dept ->
    // Project loop
    @JsonIgnoreProperties({ "projects", "users", "hibernateLazyInitializer", "handler" })
    private Department department;

    // 3. Team Members
    @ManyToMany
    @JoinTable(name = "project_members", joinColumns = @JoinColumn(name = "project_id"), inverseJoinColumns = @JoinColumn(name = "user_id"))
    // Ignore complex relationships inside members to keep JSON small
    @JsonIgnoreProperties({ "department", "projects", "hibernateLazyInitializer", "handler" })
    private Set<User> members = new HashSet<>();
}