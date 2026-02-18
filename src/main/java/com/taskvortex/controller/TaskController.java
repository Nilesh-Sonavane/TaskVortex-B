package com.taskvortex.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskvortex.dto.TaskResponse;
import com.taskvortex.entity.AuditLog;
import com.taskvortex.entity.Task;
import com.taskvortex.repository.AuditLogRepository;
import com.taskvortex.repository.TaskRepository;
import com.taskvortex.service.TaskService;

@RestController
@RequestMapping("/api/tasks")
@CrossOrigin(origins = "http://localhost:4200")
public class TaskController {

    @Autowired
    private TaskService taskService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ObjectMapper objectMapper;

    // Define centralized path for attachment storage
    private final Path fileStorageLocation = Paths.get("")
            .toAbsolutePath()
            .getParent()
            .getParent()
            .resolve("taskvortex-data")
            .resolve("attachments")
            .normalize();

    /**
     * Fetch all root-level tasks managed by a specific user.
     */
    @GetMapping("/manager/{managerId}")
    public ResponseEntity<List<TaskResponse>> getTasksByManager(@PathVariable Long managerId) {
        List<TaskResponse> tasks = taskService.getTasksByManagerId(managerId);
        return ResponseEntity.ok(tasks);
    }

    /**
     * Create a new task or subtask with optional file attachments.
     * Captures 'userEmail' to ensure the audit log reflects the correct creator.
     */
    @PostMapping(consumes = { "multipart/form-data" })
    public ResponseEntity<Task> createTask(
            @RequestPart("task") String taskJson,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @RequestPart("userEmail") String userEmail) throws IOException {

        Task task = objectMapper.readValue(taskJson, Task.class);
        return new ResponseEntity<>(taskService.createTask(task, files, userEmail), HttpStatus.CREATED);
    }

    /**
     * Retrieve detailed information for a single task including its subtasks.
     */
    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> getTaskById(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.getTaskById(id));
    }

    /**
     * Fetch the full activity history logs for a specific task.
     */

    @GetMapping("/{id}/history")
    public ResponseEntity<List<AuditLog>> getTaskHistory(@PathVariable Long id) {
        // 1. Find the task first to check for a parent
        Task task = taskRepository.findById(id).orElseThrow();

        // 2. If it's a subtask, we want to see the logs anchored to the parent
        // AND any logs specifically for this subtask.
        if (task.getParentTask() != null) {
            Long parentId = task.getParentTask().getId();
            // Custom query to find logs for BOTH (or just parent if that's where you're
            // storing them)
            List<AuditLog> history = auditLogRepository.findByEntityIdInAndEntityNameOrderByTimestampDesc(
                    List.of(id, parentId), "Task");
            return ResponseEntity.ok(history);
        }

        // Default behavior for root tasks
        return ResponseEntity.ok(auditLogRepository.findByEntityIdAndEntityNameOrderByTimestampDesc(id, "Task"));
    }

    /**
     * Update an existing task.
     * Passes 'userEmail' to the service to fetch the User object for a rich Audit
     * Log.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Task> updateTask(
            @PathVariable Long id,
            @RequestPart("task") String taskJson,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @RequestPart("userEmail") String userEmail) throws IOException {

        Task taskDetails = objectMapper.readValue(taskJson, Task.class);
        Task updatedTask = taskService.updateTask(id, taskDetails, files, userEmail);
        return ResponseEntity.ok(updatedTask);
    }

    /**
     * Serves file attachments for viewing/downloading in the browser.
     */
    @GetMapping("/attachments/{filename}")
    public ResponseEntity<Resource> viewAttachment(@PathVariable String filename) {
        try {
            Path filePath = this.fileStorageLocation.resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() || resource.isReadable()) {
                String contentType = Files.probeContentType(filePath);
                return ResponseEntity.ok()
                        .contentType(MediaType
                                .parseMediaType(contentType != null ? contentType : "application/octet-stream"))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                        .body(resource);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Delete a specific attachment.
     * UPDATED: Now requires 'userEmail' as a query parameter to log who deleted the
     * file.
     */
    @DeleteMapping("/{taskId}/attachments/{filename}")
    public ResponseEntity<Void> deleteAttachment(
            @PathVariable Long taskId,
            @PathVariable String filename,
            @RequestParam("userEmail") String userEmail) { // Capture the email from the URL params

        taskService.removeAttachment(taskId, filename, userEmail);
        return ResponseEntity.noContent().build();
    }
}