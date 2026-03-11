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
import org.springframework.web.server.ResponseStatusException;

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

    private final Path fileStorageLocation = Paths.get("").toAbsolutePath().getParent().getParent()
            .resolve("taskvortex-data").resolve("attachments").normalize();

    @GetMapping("/manager/{managerId}")
    public ResponseEntity<List<TaskResponse>> getTasksByManager(@PathVariable Long managerId) {
        return ResponseEntity.ok(taskService.getTasksByManagerId(managerId));
    }

    @PostMapping(consumes = { "multipart/form-data" })
    public ResponseEntity<Task> createTask(
            @RequestPart("task") String taskJson,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @RequestPart("userEmail") String userEmail) throws IOException {
        Task task = objectMapper.readValue(taskJson, Task.class);
        return new ResponseEntity<>(taskService.createTask(task, files, userEmail), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> getTaskById(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.getTaskById(id));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<AuditLog>> getTaskHistory(@PathVariable Long id) {
        // We check if the task exists first for validation
        taskRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));

        // Since entityId is removed, we can only fetch logs by the Entity Name "TASKS"
        // This will return the history of ALL tasks, newest first
        return ResponseEntity.ok(auditLogRepository.findByEntityNameOrderByTimestampDesc("TASKS"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Task> updateTask(
            @PathVariable Long id,
            @RequestPart("task") String taskJson,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @RequestPart("userEmail") String userEmail) throws IOException {
        Task taskDetails = objectMapper.readValue(taskJson, Task.class);
        return ResponseEntity.ok(taskService.updateTask(id, taskDetails, files, userEmail));
    }

    @GetMapping("/attachments/{filename}")
    public ResponseEntity<Resource> viewAttachment(@PathVariable String filename) {
        try {
            Path filePath = this.fileStorageLocation.resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists()) {
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

    @DeleteMapping("/{taskId}/attachments/{filename}")
    public ResponseEntity<Void> deleteAttachment(@PathVariable Long taskId, @PathVariable String filename,
            @RequestParam("userEmail") String userEmail) {
        taskService.removeAttachment(taskId, filename, userEmail);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/assignee/{userId}")
    public ResponseEntity<List<TaskResponse>> getTasksByAssignee(@PathVariable Long userId) {
        return ResponseEntity.ok(taskService.getTasksByAssignee(userId));
    }

    @GetMapping("/user/{userId}/count")
    public ResponseEntity<Long> getTaskCount(@PathVariable Long userId) {
        return ResponseEntity.ok(taskService.getActiveTaskCount(userId));
    }

    @GetMapping("/board/filter")
    public ResponseEntity<List<TaskResponse>> getBoardTasks(
            @RequestParam(required = false) List<Long> projectIds,
            @RequestParam(required = false) List<Long> assigneeIds,
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) List<String> departments,
            @RequestParam(required = false) String searchTerm) {

        return ResponseEntity
                .ok(taskService.getFilteredBoardTasks(projectIds, assigneeIds, statuses, departments, searchTerm));
    }
}