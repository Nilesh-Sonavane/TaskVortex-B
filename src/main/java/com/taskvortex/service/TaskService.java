package com.taskvortex.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.taskvortex.dto.TaskResponse;
import com.taskvortex.entity.Task;
import com.taskvortex.repository.TaskRepository;
import com.taskvortex.repository.UserRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    /**
     * DYNAMIC PATH GENERATION
     * Targets: C:\Private\Spring Boot\TaskVortex\taskvortex-data\attachments
     * This climbs up two levels from the execution directory:
     * 1. taskvortex -> task-vortex-backend
     * 2. task-vortex-backend -> TaskVortex (Main Root)
     */
    private final Path root = Paths.get("")
            .toAbsolutePath()
            .getParent() // Up to 'task-vortex-backend'
            .getParent() // Up to 'TaskVortex' (Main Root)
            .resolve("taskvortex-data")
            .resolve("attachments")
            .normalize();

    public Task createTask(Task task, List<MultipartFile> files) {
        // Set the parent Task for each Subtask to maintain JPA relationship
        if (task.getSubtasks() != null) {
            task.getSubtasks().forEach(subtask -> subtask.setTask(task));
        }

        // Handle File Uploads
        handleFileUploads(task, files);

        return taskRepository.save(task);
    }

    public List<TaskResponse> getTasksByManagerId(Long managerId) {
        return taskRepository.findByProjectManagerId(managerId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private TaskResponse mapToResponse(Task task) {
        TaskResponse response = new TaskResponse();
        response.setId(task.getId());
        response.setTitle(task.getTitle());
        response.setDescription(task.getDescription());
        response.setStatus(task.getStatus() != null ? task.getStatus().name() : "PENDING");
        response.setPriority(task.getPriority() != null ? task.getPriority().name() : "MEDIUM");
        response.setDueDate(task.getDueDate() != null ? task.getDueDate().toString() : "");

        if (task.getProject() != null) {
            response.setProject(task.getProject().getName());
            response.setProjectId(task.getProject().getId());
        }

        if (task.getAssigneeId() != null) {
            response.setAssigneeId(task.getAssigneeId());
            userRepository.findById(task.getAssigneeId()).ifPresent(user -> {
                response.setAssigneeName(user.getFirstName() + " " + user.getLastName());
            });
        }

        response.setSubtasks(task.getSubtasks());
        response.setAttachments(task.getAttachments());

        return response;
    }

    public TaskResponse getTaskById(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task not found"));
        return mapToResponse(task);
    }

    @Transactional
    public Task updateTask(Long id, Task taskDetails, List<MultipartFile> files) {
        Task existingTask = taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task not found with id: " + id));

        // Update basic fields
        existingTask.setTitle(taskDetails.getTitle());
        existingTask.setDescription(taskDetails.getDescription());
        existingTask.setDueDate(taskDetails.getDueDate());
        existingTask.setPriority(taskDetails.getPriority());
        existingTask.setAssigneeId(taskDetails.getAssigneeId());
        existingTask.setProject(taskDetails.getProject());

        // Update Subtasks: Clear and re-add to maintain relationship
        if (taskDetails.getSubtasks() != null) {
            existingTask.getSubtasks().clear();
            taskDetails.getSubtasks().forEach(sub -> {
                sub.setTask(existingTask);
                existingTask.getSubtasks().add(sub);
            });
        }

        // Handle File Uploads
        handleFileUploads(existingTask, files);

        return taskRepository.save(existingTask);
    }

    /**
     * Helper method to handle file storage logic
     */
    private void handleFileUploads(Task task, List<MultipartFile> files) {
        if (files != null && !files.isEmpty()) {
            List<String> currentAttachments = task.getAttachments();
            if (currentAttachments == null) {
                currentAttachments = new ArrayList<>();
            }

            try {
                // Ensure the directory exists at the dynamic root
                if (!Files.exists(root)) {
                    Files.createDirectories(root);
                }

                for (MultipartFile file : files) {
                    // Generate Unique Name
                    String uniqueName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();

                    // Copy file to target location
                    Files.copy(file.getInputStream(), this.root.resolve(uniqueName),
                            StandardCopyOption.REPLACE_EXISTING);

                    currentAttachments.add(uniqueName);
                }
                task.setAttachments(currentAttachments);
            } catch (IOException e) {
                throw new RuntimeException("File storage error: " + e.getMessage());
            }
        }
    }

    @Transactional
    public void removeAttachment(Long taskId, String filename) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        if (task.getAttachments() != null) {
            task.getAttachments().remove(filename);
            taskRepository.save(task);
        }

        try {
            // Delete the physical file
            Files.deleteIfExists(this.root.resolve(filename));
        } catch (IOException e) {
            // Log error but don't fail the transaction
            System.err.println("Failed to delete physical file: " + filename);
        }
    }
}