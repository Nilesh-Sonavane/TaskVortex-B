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
import com.taskvortex.entity.TaskStatus;
import com.taskvortex.entity.User;
import com.taskvortex.repository.TaskRepository;
import com.taskvortex.repository.UserRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    private final Path root = Paths.get("").toAbsolutePath().getParent().getParent()
            .resolve("taskvortex-data").resolve("attachments").normalize();

    /**
     * Creates a task and logs it using the actual performer's email.
     */

    @Transactional
    public Task createTask(Task task, List<MultipartFile> files, String userEmail) {
        if (task.getParentTask() != null) {
            throw new RuntimeException("Validation Error: Subtasks cannot have their own subtasks.");
        }
        if (task.getSubtasks() != null) {
            task.getSubtasks().forEach(sub -> {
                sub.setParentTask(task);
                sub.setProject(task.getProject());

                // FORCE inheritance: If subtask has no assignee, use Parent's
                // If Parent also has none (unlikely but possible), it remains null
                if (sub.getAssigneeId() == null) {
                    sub.setAssigneeId(task.getAssigneeId());
                }
                if (sub.getDueDate() == null) {
                    sub.setDueDate(task.getDueDate());
                }

                // Ensure other defaults
                if (sub.getStatus() == null)
                    sub.setStatus(TaskStatus.PENDING);
                if (sub.getPriority() == null)
                    sub.setPriority(task.getPriority());
            });
        }

        handleFileUploads(task, files);
        Task savedTask = taskRepository.save(task);

        String detail = (task.getParentTask() != null) ? "Subtask initialized" : "Main task initialized"; //

        // Use dynamic userEmail to record the correct performer
        auditService.logAction("TASK_CREATED", savedTask.getId(), detail, userEmail); //

        return savedTask;
    }

    /**
     * Updates task details and logs changes using the performer's User object.
     */

    @Transactional
    public Task updateTask(Long id, Task taskDetails, List<MultipartFile> files, String currentEmail) {
        // 1. Identify the task being edited and the performer
        Task existing = taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task not found"));
        User performer = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        StringBuilder logBuilder = new StringBuilder("<ul class='audit-list'>");

        // --- 2. GLOBAL SYNC IDENTIFICATION ---
        // Rule: Parent and all Subtasks must share the same assignee.
        // We find the "Root" parent to propagate changes everywhere.
        Task rootParent = (existing.getParentTask() != null) ? existing.getParentTask() : existing;
        Long newAssigneeId = taskDetails.getAssigneeId();

        // --- 3. HANDLE FILE UPLOADS ---
        List<String> newFileNames = handleFileUploads(existing, files);
        for (String uniqueName : newFileNames) {
            String originalName = uniqueName.contains("_") ? uniqueName.substring(uniqueName.indexOf("_") + 1)
                    : uniqueName;

            logBuilder.append("<li><i class='fa-solid fa-paperclip text-primary me-1'></i> ")
                    .append("<b>Attachment:</b> Added ")
                    .append("<a href='#' ")
                    .append("class='history-attachment-link text-decoration-none' ")
                    .append("data-filename='").append(uniqueName).append("'>")
                    .append("<span class='badge-new'>").append(originalName).append("</span>")
                    .append("</a></li>");
        }

        // --- 4. COMPARE FIELDS & LOG ASSIGNEE CHANGE ---
        autoCompare(logBuilder, "Title", existing.getTitle(), taskDetails.getTitle());
        autoCompare(logBuilder, "Status",
                existing.getStatus().toString().replace("_", " "),
                taskDetails.getStatus().toString().replace("_", " "));

        if (!java.util.Objects.equals(existing.getAssigneeId(), newAssigneeId)) {
            String oldName = getMemberName(existing.getAssigneeId());
            String newName = getMemberName(newAssigneeId);
            autoCompare(logBuilder, "Assignee", oldName, newName);
        }

        // --- 5. ENFORCE GLOBAL ASSIGNEE SYNC (Trickle Down & Bubble Up) ---
        rootParent.setAssigneeId(newAssigneeId);
        if (rootParent.getSubtasks() != null) {
            for (Task sub : rootParent.getSubtasks()) {
                sub.setAssigneeId(newAssigneeId);
            }
        }

        // --- 6. SMART SUBTASK CHECKLIST SYNC (If editing Parent) ---

        if (existing.getParentTask() == null && taskDetails.getSubtasks() != null) {

            // 1. Create a list of current subtask titles to compare
            List<String> oldTitles = existing.getSubtasks().stream()
                    .map(Task::getTitle).toList();
            List<String> newTitles = taskDetails.getSubtasks().stream()
                    .map(Task::getTitle).toList();

            // 2. Only log if the titles or the count actually differ
            boolean trulyChanged = !oldTitles.equals(newTitles);

            if (trulyChanged) {
                existing.getSubtasks().clear();
                for (Task sub : taskDetails.getSubtasks()) {
                    sub.setParentTask(existing);
                    sub.setProject(existing.getProject());
                    sub.setAssigneeId(newAssigneeId);

                    if (sub.getStatus() == null)
                        sub.setStatus(com.taskvortex.entity.TaskStatus.PENDING);
                    if (sub.getPriority() == null)
                        sub.setPriority(existing.getPriority());

                    existing.getSubtasks().add(sub);
                }
                logBuilder.append(
                        "<li><i class='fa-solid fa-list-check text-info me-1'></i> <b>Subtasks:</b> Checklist updated</li>");
            } else {
                // If titles match, just sync the assignees without clearing the list
                // This prevents the "Checklist updated" log from appearing unnecessarily
                for (Task sub : existing.getSubtasks()) {
                    sub.setAssigneeId(newAssigneeId);
                }
            }
        }

        // --- 7. APPLY CORE UPDATES TO THE CURRENT TASK ---
        existing.setTitle(taskDetails.getTitle());
        existing.setDescription(taskDetails.getDescription());
        existing.setStatus(taskDetails.getStatus());
        existing.setPriority(taskDetails.getPriority());
        existing.setDueDate(taskDetails.getDueDate());

        // --- 8. PERSIST AUDIT LOG ---
        if (logBuilder.toString().contains("<li>")) {
            logBuilder.append("</ul>");
            Long targetLogId = (existing.getParentTask() != null) ? existing.getParentTask().getId() : id;
            String finalDetails = (existing.getParentTask() != null)
                    ? "<b>Subtask (" + existing.getTitle() + "):</b> " + logBuilder.toString()
                    : logBuilder.toString();
            auditService.logAction("TASK_EDITED", targetLogId, finalDetails, performer);
        }

        // --- 9. FINAL PERSISTENCE ---
        taskRepository.save(rootParent); // Ensure the sync is saved globally
        return taskRepository.save(existing);
    }

    /**
     * Compares old and new values to generate visual badges in the timeline.
     */
    private void autoCompare(StringBuilder builder, String field, String oldVal, String newVal) {
        // 1. Normalize nulls and trim to avoid logging changes based on extra spaces
        String safeOld = (oldVal == null) ? "" : oldVal.trim();
        String safeNew = (newVal == null) ? "" : newVal.trim();

        // 2. Perform Case-Insensitive comparison
        if (!safeOld.equalsIgnoreCase(safeNew)) {

            // 3. Optional: Shorten very long values (like Descriptions) for the log
            String displayOld = formatForLog(safeOld);
            String displayNew = formatForLog(safeNew);

            builder.append(String.format(
                    "<li>" +
                            "<b>%s:</b> " +
                            "<span class='badge-old'>%s</span> " +
                            "<i class='fa-solid fa-arrow-right mx-1'></i> " +
                            "<span class='badge-new'>%s</span>" +
                            "</li>",
                    field,
                    displayOld.isEmpty() ? "Empty" : displayOld,
                    displayNew.isEmpty() ? "Empty" : displayNew));
        }
    }

    /**
     * Helper to ensure the Audit Log stays readable even if a description is long.
     */
    private String formatForLog(String val) {
        if (val.length() > 50) {
            return val.substring(0, 47) + "...";
        }
        return val;
    }
    // --- Data Mapping & Fetching Methods ---

    public List<TaskResponse> getTasksByManagerId(Long managerId) {
        return taskRepository.findByProjectManagerId(managerId)
                .stream()
                .filter(t -> t.getParentTask() == null)
                .map(this::mapToResponse)
                .toList();
    }

    public TaskResponse getTaskById(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task not found"));
        return mapToResponse(task);
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

        // FIX: Apply capitalization here so it reflects in the UI
        if (task.getAssigneeId() != null) {
            response.setAssigneeId(task.getAssigneeId());
            userRepository.findById(task.getAssigneeId()).ifPresent(user -> {
                String fullName = user.getFirstName() + " " + user.getLastName();
                response.setAssigneeName(capitalizeName(fullName)); // Capitalize the name
            });
        }

        if (task.getParentTask() != null) {
            response.setParentTaskId(task.getParentTask().getId());
        }

        response.setAttachments(task.getAttachments());

        // This recursion ensures every subtask also gets its assigneeName mapped
        if (task.getSubtasks() != null && !task.getSubtasks().isEmpty()) {
            response.setSubtasks(task.getSubtasks().stream()
                    .map(this::mapToResponse)
                    .toList());
        }

        return response;
    }

    private List<String> handleFileUploads(Task task, List<MultipartFile> files) {
        List<String> uploadedNames = new ArrayList<>(); // Track the new names
        if (files != null && !files.isEmpty()) {
            List<String> currentAttachments = task.getAttachments();
            if (currentAttachments == null)
                currentAttachments = new ArrayList<>();

            try {
                if (!Files.exists(root))
                    Files.createDirectories(root);

                for (MultipartFile file : files) {
                    String uniqueName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
                    Files.copy(file.getInputStream(), this.root.resolve(uniqueName),
                            StandardCopyOption.REPLACE_EXISTING);

                    currentAttachments.add(uniqueName);
                    uploadedNames.add(uniqueName); // Store for the audit log
                }
                task.setAttachments(currentAttachments);
            } catch (IOException e) {
                throw new RuntimeException("File storage error: " + e.getMessage());
            }
        }
        return uploadedNames; // Return the list of unique names
    }

    @Transactional
    public void removeAttachment(Long taskId, String filename, String userEmail) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        if (task.getAttachments() != null && task.getAttachments().remove(filename)) {
            taskRepository.save(task);

            String cleanName = filename.contains("_") ? filename.substring(filename.indexOf("_") + 1) : filename;

            String detail = "<ul class='audit-list'><li><i class='fa-solid fa-trash-can text-danger me-1'></i> " +
                    "<b>Attachment:</b> Removed <span class='badge-old'>" + cleanName + "</span></li></ul>";

            Long targetId = (task.getParentTask() != null) ? task.getParentTask().getId() : taskId;
            auditService.logAction("FILE_REMOVED", targetId, detail, userEmail);
        }
    }

    private String getMemberName(Long userId) {
        if (userId == null)
            return "Unassigned";
        return userRepository.findById(userId)
                .map(u -> capitalizeName(u.getFirstName() + " " + u.getLastName()))
                .orElse("Unknown");
    }

    private String capitalizeName(String name) {
        if (name == null || name.isBlank())
            return "";
        String[] words = name.toLowerCase().split("\\s+");
        StringBuilder capitalized = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                capitalized.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1))
                        .append(" ");
            }
        }
        return capitalized.toString().trim();
    }
}