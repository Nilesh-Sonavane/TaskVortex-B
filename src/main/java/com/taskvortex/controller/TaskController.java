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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskvortex.dto.TaskResponse;
import com.taskvortex.entity.Task;
import com.taskvortex.service.TaskService;

@RestController
@RequestMapping("/api/tasks")
@CrossOrigin(origins = "http://localhost:4200")
public class TaskController {

    @Autowired
    private TaskService taskService;

    @Autowired
    private ObjectMapper objectMapper;

    private final Path fileStorageLocation = Paths.get("")
            .toAbsolutePath()
            .getParent() // Up to 'task-vortex-backend'
            .getParent() // Up to 'TaskVortex' (Main Root)
            .resolve("taskvortex-data")
            .resolve("attachments")
            .normalize();

    @GetMapping("/manager/{managerId}")
    public ResponseEntity<List<TaskResponse>> getTasksByManager(@PathVariable Long managerId) {
        List<TaskResponse> tasks = taskService.getTasksByManagerId(managerId);
        return ResponseEntity.ok(tasks);
    }

    @PostMapping(consumes = { "multipart/form-data" })
    public ResponseEntity<Task> createTask(
            @RequestPart("task") Task task,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return new ResponseEntity<>(taskService.createTask(task, files), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> getTaskById(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.getTaskById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Task> updateTask(
            @PathVariable Long id,
            @RequestPart("task") String taskJson,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) throws IOException {

        Task taskDetails = objectMapper.readValue(taskJson, Task.class);
        Task updatedTask = taskService.updateTask(id, taskDetails, files);
        return ResponseEntity.ok(updatedTask);
    }

    /**
     * View Attachment: Serves files from the dynamic directory in TaskVortex root.
     */
    @GetMapping("/attachments/{filename}")
    public ResponseEntity<Resource> viewAttachment(@PathVariable String filename) {
        try {
            // Use the centralized fileStorageLocation defined above
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

    @DeleteMapping("/{taskId}/attachments/{filename}")
    public ResponseEntity<Void> deleteAttachment(@PathVariable Long taskId, @PathVariable String filename) {
        taskService.removeAttachment(taskId, filename);
        return ResponseEntity.noContent().build();
    }
}