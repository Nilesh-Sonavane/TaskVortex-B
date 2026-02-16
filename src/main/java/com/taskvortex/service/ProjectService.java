package com.taskvortex.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taskvortex.dto.MemberDTO;
import com.taskvortex.dto.ProjectRequest;
import com.taskvortex.dto.ProjectResponse;
import com.taskvortex.entity.Department;
import com.taskvortex.entity.Project;
import com.taskvortex.entity.User;
import com.taskvortex.repository.DepartmentRepository; // <--- Import this
import com.taskvortex.repository.ProjectRepository;
import com.taskvortex.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository; // <--- Inject this

    // --- 1. CREATE Project ---
    @Transactional
    public Project createProject(ProjectRequest request) {
        if (projectRepository.existsByProjectKey(request.getKey())) {
            throw new IllegalArgumentException("Project Key '" + request.getKey() + "' already exists.");
        }

        User manager = userRepository.findById(request.getManagerId())
                .orElseThrow(() -> new IllegalArgumentException("Manager not found"));

        Project project = new Project();
        project.setName(request.getName());
        project.setProjectKey(request.getKey().toUpperCase());
        project.setDescription(request.getDescription());
        project.setStartDate(request.getStartDate());
        project.setTargetEndDate(request.getEndDate());
        project.setManager(manager);

        // Default to manager's department if created via simple form
        project.setDepartment(manager.getDepartment());

        if (request.getMemberIds() != null && !request.getMemberIds().isEmpty()) {
            List<User> membersList = userRepository.findAllById(request.getMemberIds());
            Set<User> membersSet = new HashSet<>(membersList);
            project.setMembers(membersSet);
        }

        return projectRepository.save(project);
    }

    // --- 2. GET ALL Projects ---
    public List<ProjectResponse> getAllProjects() {
        List<Project> projects = projectRepository.findAll();
        // Use the helper method to keep code clean
        return projects.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    // --- 3. GET SINGLE Project (For Edit Page) ---
    public ProjectResponse getProjectById(Long id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Project not found"));
        return mapToResponse(project);
    }

    // --- 4. UPDATE Project (For Save Changes) ---
    @Transactional
    public ProjectResponse updateProject(Long id, ProjectRequest request) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        // Update Basic Fields
        project.setName(request.getName());
        project.setDescription(request.getDescription());
        project.setStartDate(request.getStartDate());
        project.setTargetEndDate(request.getEndDate());

        if (request.getStatus() != null) {
            project.setStatus(request.getStatus());
        }

        // Update Manager
        if (request.getManagerId() != null) {
            User manager = userRepository.findById(request.getManagerId())
                    .orElseThrow(() -> new IllegalArgumentException("Manager not found"));
            project.setManager(manager);
        }

        // Update Department
        if (request.getDepartmentId() != null) {
            Department dept = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new IllegalArgumentException("Department not found"));
            project.setDepartment(dept);
        }

        Project updatedProject = projectRepository.save(project);
        return mapToResponse(updatedProject);
    }

    // --- 5. ARCHIVE / RESTORE ---
    @Transactional
    public Project updateStatus(Long id, String status) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        project.setStatus(status);
        return projectRepository.save(project);
    }

    // --- 6. DELETE ---
    public void deleteProject(Long id) {
        if (!projectRepository.existsById(id)) {
            throw new IllegalArgumentException("Project not found");
        }
        projectRepository.deleteById(id);
    }

    public List<ProjectResponse> getProjectsByManagerId(Long managerId) {
        return projectRepository.findByManagerId(managerId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // --- HELPER: Map Entity to DTO ---
    private ProjectResponse mapToResponse(Project project) {
        return ProjectResponse.builder()
                // --- THESE ARE MISSING IN YOUR CODE ---
                .id(project.getId())
                .name(project.getName())
                .projectKey(project.getProjectKey())
                .description(project.getDescription())
                .status(project.getStatus())
                .progress(project.getProgress())
                .startDate(project.getStartDate())
                .targetEndDate(project.getTargetEndDate())

                // Handle Manager (Check for null to avoid crash)
                .managerId(project.getManager() != null ? project.getManager().getId() : null)
                .managerName(project.getManager() != null
                        ? project.getManager().getFirstName() + " " + project.getManager().getLastName()
                        : "Unknown")

                // Handle Department
                .departmentId(project.getDepartment() != null ? project.getDepartment().getId() : null)
                .departmentName(project.getDepartment() != null
                        ? project.getDepartment().getName()
                        : "General")

                // --- THESE SEEM TO BE WORKING ALREADY ---
                .membersCount(project.getMembers() != null ? project.getMembers().size() : 0)
                .members(project.getMembers().stream().map(member -> MemberDTO.builder()
                        .id(member.getId())
                        .name(member.getFirstName() + " " + member.getLastName())
                        .email(member.getEmail())
                        .build())
                        .collect(Collectors.toSet()))
                .build();
    }
}