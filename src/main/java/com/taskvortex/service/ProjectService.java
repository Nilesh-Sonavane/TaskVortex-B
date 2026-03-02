package com.taskvortex.service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taskvortex.dto.MemberDTO;
import com.taskvortex.dto.ProjectRequest;
import com.taskvortex.dto.ProjectResponse;
import com.taskvortex.entity.AuditLog;
import com.taskvortex.entity.Project;
import com.taskvortex.entity.User;
import com.taskvortex.entity.UserRole;
import com.taskvortex.repository.AuditLogRepository;
import com.taskvortex.repository.ProjectRepository;
import com.taskvortex.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProjectService {

        private final ProjectRepository projectRepository;
        private final UserRepository userRepository;
        private final AuditLogRepository auditLogRepository;

        // --- 1. RETRIEVAL METHODS ---
        public List<ProjectResponse> getAllProjects() {
                return projectRepository.findAll().stream()
                                .map(this::mapToResponse)
                                .collect(Collectors.toList());
        }

        public ProjectResponse getProjectById(Long id) {
                Project project = projectRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("Project not found with id: " + id));
                return mapToResponse(project);
        }

        // --- 2. CREATE PROJECT (Attributes action to performerId) ---
        @Transactional
        public Project createProject(ProjectRequest request, Long performerId) {
                if (projectRepository.existsByProjectKey(request.getKey())) {
                        throw new IllegalArgumentException("Project Key '" + request.getKey() + "' already exists.");
                }

                User performer = userRepository.findById(performerId)
                                .orElseThrow(() -> new IllegalArgumentException("Action performer not found"));

                User manager = userRepository.findById(request.getManagerId())
                                .orElseThrow(() -> new IllegalArgumentException("Assigned manager not found"));

                Project project = new Project();
                project.setName(request.getName());
                project.setProjectKey(request.getKey().toUpperCase());
                project.setDescription(request.getDescription());
                project.setStartDate(request.getStartDate());
                project.setManager(manager);
                project.setDepartment(manager.getDepartment());
                project.setStatus("ACTIVE");

                if (request.getMemberIds() != null && !request.getMemberIds().isEmpty()) {
                        List<User> membersList = userRepository.findAllById(request.getMemberIds());
                        project.setMembers(new HashSet<>(membersList));
                }

                Project savedProject = projectRepository.save(project);

                String createDetail = String.format("Project <b>%s</b> was created and assigned to manager <b>%s</b>.",
                                savedProject.getName(), manager.getFirstName());

                saveLog(performer, "CREATE", "PROJECTS", savedProject.getId(), createDetail);

                return savedProject;
        }

        // --- 3. UPDATE PROJECT ---
        // --- 3. UPDATE PROJECT (Fixed Member Reset) ---
        @Transactional
        public ProjectResponse updateProject(Long id, ProjectRequest request, Long performerId) {
                Project project = projectRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("Project not found"));

                User performer = userRepository.findById(performerId)
                                .orElseThrow(() -> new IllegalArgumentException("Action performer not found"));

                StringBuilder htmlChanges = new StringBuilder("<ul class='list-unstyled mb-0'>");
                boolean isChanged = false;

                // Name Change
                if (!project.getName().equals(request.getName())) {
                        htmlChanges.append(
                                        String.format("<li>Name: <b>%s</b> &rarr; <b>%s</b></li>", project.getName(),
                                                        request.getName()));
                        project.setName(request.getName());
                        isChanged = true;
                }

                // Manager Change
                if (request.getManagerId() != null && !project.getManager().getId().equals(request.getManagerId())) {
                        User newManager = userRepository.findById(request.getManagerId()).orElse(null);
                        htmlChanges.append(String.format("<li>Manager: <b>%s</b> &rarr; <b>%s</b></li>",
                                        project.getManager().getFirstName(), newManager.getFirstName()));
                        project.setManager(newManager);

                        // Optional: Update department to match new manager's department
                        if (newManager != null) {
                                project.setDepartment(newManager.getDepartment());
                        }
                        isChanged = true;
                }

                // --- THE FIX: Only clear members if a new list is provided ---
                if (request.getMemberIds() != null) {
                        // Fetch current member IDs to see if something actually changed
                        List<Long> currentIds = project.getMembers().stream()
                                        .map(User::getId)
                                        .toList();

                        // Check if the new list is different from the old list
                        if (!new HashSet<>(currentIds).equals(new HashSet<>(request.getMemberIds()))) {
                                List<User> updatedMembers = userRepository.findAllById(request.getMemberIds());
                                project.getMembers().clear(); // Now safe to clear because we have the new data
                                project.getMembers().addAll(updatedMembers);

                                htmlChanges.append(
                                                String.format("<li>Team members updated (Total: <b>%d</b>)</li>",
                                                                updatedMembers.size()));
                                isChanged = true;
                        }
                }

                project.setDescription(request.getDescription());
                project.setStartDate(request.getStartDate());

                Project updatedProject = projectRepository.save(project);

                if (isChanged) {
                        htmlChanges.append("</ul>");
                        saveLog(performer, "UPDATE", "PROJECTS", id, htmlChanges.toString());
                }

                return mapToResponse(updatedProject);
        }

        // --- 4. STATUS & DELETE ---
        @Transactional
        public Project updateStatus(Long id, String status, Long performerId) {
                Project project = projectRepository.findById(id)
                                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
                User performer = userRepository.findById(performerId)
                                .orElseThrow(() -> new IllegalArgumentException("Performer not found"));

                String oldStatus = project.getStatus();
                project.setStatus(status);
                Project saved = projectRepository.save(project);

                String logHtml = String.format("Status changed: <b>%s</b> &rarr; <b>%s</b>", oldStatus, status);
                saveLog(performer, "STATUS_CHANGE", "PROJECTS", id, logHtml);

                return saved;
        }

        @Transactional
        public void deleteProject(Long id, Long performerId) {
                Project project = projectRepository.findById(id)
                                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
                User performer = userRepository.findById(performerId)
                                .orElseThrow(() -> new IllegalArgumentException("Performer not found"));

                saveLog(performer, "DELETE", "PROJECTS", id, "Project <b>" + project.getName() + "</b> was deleted.");
                projectRepository.deleteById(id);
        }

        public List<ProjectResponse> getAccessibleProjects(String email) {
                User user = userRepository.findByEmail(email)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                List<Project> projects;

                if (user.getRole() == UserRole.ADMIN) {
                        // Admins see everything
                        projects = projectRepository.findAll();
                } else if (user.getRole() == UserRole.MANAGER) {
                        // Managers see projects they manage
                        projects = projectRepository.findByManagerId(user.getId());
                } else {
                        // Employees see projects where they are members
                        // Note: Using your existing repository method for member check
                        projects = projectRepository.findAllByManagerIdOrMembersId(user.getId(), user.getId());
                }

                return projects.stream()
                                .map(this::mapToResponse) // Corrected from mapToDTO
                                .toList();
        }

        // --- 5. HELPERS ---
        private void saveLog(User user, String action, String entity, Long entityId, String details) {
                AuditLog log = new AuditLog();
                log.setAction(action);
                log.setEntityName(entity);
                log.setDetails(details);
                log.setPerformedBy(user);
                log.setTimestamp(LocalDateTime.now());
                auditLogRepository.save(log);
        }

        private ProjectResponse mapToResponse(Project project) {
                return ProjectResponse.builder()
                                .id(project.getId())
                                .name(project.getName())
                                .projectKey(project.getProjectKey())
                                .description(project.getDescription())
                                .status(project.getStatus())
                                .progress(project.getProgress())
                                .startDate(project.getStartDate())
                                .managerId(project.getManager() != null ? project.getManager().getId() : null)
                                .managerName(project.getManager() != null
                                                ? project.getManager().getFirstName() + " "
                                                                + project.getManager().getLastName()
                                                : "Unknown")
                                .departmentName(project.getDepartment() != null ? project.getDepartment().getName()
                                                : "General")
                                .members(project.getMembers().stream()
                                                .map(m -> MemberDTO.builder().id(m.getId())
                                                                .name(m.getFirstName() + " " + m.getLastName())
                                                                .email(m.getEmail()).build())
                                                .collect(Collectors.toSet()))
                                .build();
        }

        public List<ProjectResponse> getProjectsByManagerId(Long managerId) {
                return projectRepository.findByManagerId(managerId).stream().map(this::mapToResponse).toList();
        }

        public List<ProjectResponse> getProjectsByUserId(Long userId) {
                return projectRepository.findAllByManagerIdOrMembersId(userId, userId).stream().map(this::mapToResponse)
                                .toList();
        }
}