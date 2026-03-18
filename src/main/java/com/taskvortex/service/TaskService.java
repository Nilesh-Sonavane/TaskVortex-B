package com.taskvortex.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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

    @Transactional
    public Task createTask(Task task, List<MultipartFile> files, String userEmail) {
        if (task.getParentTask() != null) {
            throw new RuntimeException("Validation Error: Subtasks cannot have their own subtasks.");
        }

        if (task.getSubtasks() != null) {
            task.getSubtasks().forEach(sub -> {
                sub.setParentTask(task);
                sub.setProject(task.getProject());
                if (sub.getAssigneeId() == null)
                    sub.setAssigneeId(task.getAssigneeId());
                if (sub.getDueDate() == null)
                    sub.setDueDate(task.getDueDate());
                if (sub.getStatus() == null)
                    sub.setStatus(TaskStatus.NOT_STARTED);
                if (sub.getPriority() == null)
                    sub.setPriority(task.getPriority());
            });
        }

        handleFileUploads(task, files);
        Task savedTask = taskRepository.save(task);

        String detail = "<b>" + task.getTitle() + "</b> task initialized";
        auditService.logAction("TASKS", "TASK_CREATED", savedTask.getId(), detail, userEmail);

        return savedTask;
    }

    @Transactional
    public Task updateTask(Long id, Task taskDetails, List<MultipartFile> files, String currentEmail) {
        Task existing = taskRepository.findById(id).orElseThrow(() -> new RuntimeException("Task not found"));
        User performer = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        StringBuilder logBuilder = new StringBuilder("<ul class='audit-list'>");

        // --- GLOBAL SYNC ---
        Task rootParent = (existing.getParentTask() != null) ? existing.getParentTask() : existing;
        Long newAssigneeId = taskDetails.getAssigneeId();

        // --- FILE UPLOADS ---
        List<String> newFileNames = handleFileUploads(existing, files);
        if (!newFileNames.isEmpty()) {
            logBuilder.append("<li><i class='fa-solid fa-paperclip text-primary me-1'></i> <b>Files Added:</b> ");
            for (String uniqueName : newFileNames) {
                String originalName = uniqueName.contains("_") ? uniqueName.substring(uniqueName.lastIndexOf("_") + 1)
                        : uniqueName;
                logBuilder.append("<a href='javascript:void(0)' class='history-attachment-link' data-filename='")
                        .append(uniqueName).append("'>").append(originalName).append("</a> ");
            }
            logBuilder.append("</li>");
        }

        // --- TRACK ALL FIELDS ---
        autoCompare(logBuilder, "Title", existing.getTitle(), taskDetails.getTitle());
        autoCompare(logBuilder, "Description",
                existing.getDescription() == null ? "" : existing.getDescription(),
                taskDetails.getDescription() == null ? "" : taskDetails.getDescription());
        autoCompare(logBuilder, "Status",
                existing.getStatus().toString().replace("_", " "),
                taskDetails.getStatus().toString().replace("_", " "));
        autoCompare(logBuilder, "Priority", existing.getPriority().toString(), taskDetails.getPriority().toString());
        autoCompare(logBuilder, "Due Date",
                existing.getDueDate() != null ? existing.getDueDate().toString() : "None",
                taskDetails.getDueDate() != null ? taskDetails.getDueDate().toString() : "None");
        autoCompare(logBuilder, "Task Points",
                existing.getTaskPoints() == null ? "None" : String.valueOf(existing.getTaskPoints()),
                taskDetails.getTaskPoints() == null ? "None" : String.valueOf(taskDetails.getTaskPoints()));
        autoCompare(logBuilder, "Working Hours",
                existing.getWorkingHours() == null ? "None" : String.valueOf(existing.getWorkingHours()),
                taskDetails.getWorkingHours() == null ? "None" : String.valueOf(taskDetails.getWorkingHours()));

        if (!java.util.Objects.equals(existing.getAssigneeId(), newAssigneeId)) {
            String oldName = getMemberName(existing.getAssigneeId());
            String newName = getMemberName(newAssigneeId);
            autoCompare(logBuilder, "Assignee", oldName, newName);
        }

        // --- ASSIGNEE SYNC ---
        rootParent.setAssigneeId(newAssigneeId);
        if (rootParent.getSubtasks() != null) {
            rootParent.getSubtasks().forEach(sub -> sub.setAssigneeId(newAssigneeId));
        }

        // --- SUBTASK CHECKLIST LOGIC ---
        if (existing.getParentTask() == null && taskDetails.getSubtasks() != null) {
            List<String> oldTitles = existing.getSubtasks().stream().map(Task::getTitle).toList();
            List<String> newTitles = taskDetails.getSubtasks().stream().map(Task::getTitle).toList();

            List<String> added = newTitles.stream().filter(t -> !oldTitles.contains(t)).toList();
            List<String> removed = oldTitles.stream().filter(t -> !newTitles.contains(t)).toList();

            if (!added.isEmpty() || !removed.isEmpty()) {
                existing.getSubtasks().clear();
                for (Task sub : taskDetails.getSubtasks()) {
                    sub.setParentTask(existing);
                    sub.setProject(existing.getProject());
                    sub.setAssigneeId(newAssigneeId);
                    if (sub.getStatus() == null)
                        sub.setStatus(TaskStatus.NOT_STARTED);
                    if (sub.getPriority() == null)
                        sub.setPriority(existing.getPriority());
                    existing.getSubtasks().add(sub);
                }
                logBuilder
                        .append("<li><i class='fa-solid fa-list-check text-info me-1'></i> <b>Subtasks changed:</b> ");
                if (!added.isEmpty())
                    logBuilder.append("<span class='text-success'>added " + added + "</span> ");
                if (!removed.isEmpty())
                    logBuilder.append("<span class='text-danger'>removed " + removed + "</span>");
                logBuilder.append("</li>");
            } else {
                existing.getSubtasks().forEach(sub -> sub.setAssigneeId(newAssigneeId));
            }
        }

        // --- APPLY UPDATES ---
        existing.setTitle(taskDetails.getTitle());
        existing.setDescription(taskDetails.getDescription());
        existing.setStatus(taskDetails.getStatus());
        existing.setPriority(taskDetails.getPriority());
        existing.setDueDate(taskDetails.getDueDate());
        existing.setTaskPoints(taskDetails.getTaskPoints());
        existing.setWorkingHours(taskDetails.getWorkingHours());

        // --- PERSIST AUDIT LOG ---
        if (logBuilder.toString().contains("<li>")) {
            logBuilder.append("</ul>");
            Long targetLogId = (existing.getParentTask() != null) ? existing.getParentTask().getId() : id;
            String prefix = (existing.getParentTask() != null) ? "<b>[Subtask: " + existing.getTitle() + "]</b> " : "";
            auditService.logAction("TASKS", "TASK_EDITED", targetLogId, prefix + logBuilder.toString(), performer);
        }

        taskRepository.save(rootParent);
        return taskRepository.save(existing);
    }

    private void autoCompare(StringBuilder builder, String field, String oldVal, String newVal) {
        String safeOld = (oldVal == null) ? "" : oldVal.trim();
        String safeNew = (newVal == null) ? "" : newVal.trim();

        if (!safeOld.equalsIgnoreCase(safeNew)) {
            String displayOld = safeOld.length() > 50 ? safeOld.substring(0, 47) + "..." : safeOld;
            String displayNew = safeNew.length() > 50 ? safeNew.substring(0, 47) + "..." : safeNew;

            builder.append(String.format(
                    "<li><b>%s:</b> <span class='badge-old'>%s</span> <i class='fa-solid fa-arrow-right mx-1'></i> <span class='badge-new'>%s</span></li>",
                    field, displayOld.isEmpty() ? "Empty" : displayOld, displayNew.isEmpty() ? "Empty" : displayNew));
        }
    }

    @Transactional
    public void removeAttachment(Long taskId, String filename, String userEmail) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new RuntimeException("Task not found"));
        if (task.getAttachments() != null && task.getAttachments().remove(filename)) {
            taskRepository.save(task);
            String cleanName = filename.contains("_") ? filename.substring(filename.lastIndexOf("_") + 1) : filename;
            String detail = "<ul class='audit-list'><li><i class='fa-solid fa-trash-can text-danger me-1'></i> <b>Attachment:</b> Removed <span class='badge-old'>"
                    + cleanName + "</span></li></ul>";
            Long targetId = (task.getParentTask() != null) ? task.getParentTask().getId() : taskId;
            auditService.logAction("TASKS", "FILE_REMOVED", targetId, detail, userEmail);
        }
    }

    @Transactional
    public List<TaskResponse> getFilteredBoardTasks(List<Long> projectIds, List<Long> assigneeIds,
            List<String> statusStrings, List<String> departments, String searchTerm) {

        // Convert status strings from UI to Enums; empty list becomes NULL for the
        // Repository
        List<TaskStatus> statuses = (statusStrings != null && !statusStrings.isEmpty())
                ? statusStrings.stream().map(TaskStatus::valueOf).toList()
                : null;

        List<Long> pIds = (projectIds != null && !projectIds.isEmpty()) ? projectIds : null;
        List<Long> aIds = (assigneeIds != null && !assigneeIds.isEmpty()) ? assigneeIds : null;
        String search = (searchTerm != null && !searchTerm.isBlank()) ? searchTerm.toLowerCase() : null;
        List<String> depts = (departments != null && !departments.isEmpty()) ? departments : null;

        return taskRepository.findBoardTasks(pIds, aIds, statuses, depts, search)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<TaskResponse> getTasksByManagerId(Long managerId) {
        return taskRepository.findByProjectManagerId(managerId).stream().filter(t -> t.getParentTask() == null)
                .map(this::mapToResponse).toList();
    }

    public TaskResponse getTaskById(Long id) {
        return mapToResponse(taskRepository.findById(id).orElseThrow(() -> new RuntimeException("Task not found")));
    }

    public List<TaskResponse> getAllTasks() {
        List<Task> tasks = taskRepository.findAll();

        // Use your existing mapToResponse method instead of new TaskResponse(task)
        return tasks.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private TaskResponse mapToResponse(Task task) {
        TaskResponse response = new TaskResponse();
        response.setId(task.getId());
        response.setTitle(task.getTitle());
        response.setDescription(task.getDescription());
        response.setStatus(task.getStatus() != null ? task.getStatus().name() : "PENDING");
        response.setTaskPoints(task.getTaskPoints());
        response.setWorkingHours(task.getWorkingHours());
        if (task.getProject() != null) {
            response.setProject(task.getProject().getName());
            response.setProjectId(task.getProject().getId());
            response.setProjectKey(task.getProject().getProjectKey());
            // DYNAMIC DEPT ASSIGNMENT: Pulling from the Project
            String dept = (task.getProject().getDepartment() != null)
                    ? task.getProject().getDepartment().getName()
                    : "General";
            response.setDept(dept);
        }
        response.setPriority(task.getPriority() != null ? task.getPriority().name() : "MEDIUM");
        response.setDueDate(task.getDueDate() != null ? task.getDueDate().toString() : "");
        response.setCreatedBy(
                task.getProject().getManager().getFirstName() + " " + task.getProject().getManager().getLastName());
        response.setCreatorEmail(task.getProject().getManager().getEmail());
        response.setProject(task.getProject().getName());
        response.setProjectId(task.getProject().getId());
        response.setAttachments(task.getAttachments());
        if (task.getAssigneeId() != null) {
            response.setAssigneeId(task.getAssigneeId());
            userRepository.findById(task.getAssigneeId()).ifPresent(user -> {
                response.setAssigneeName(capitalizeName(user.getFirstName() + " " + user.getLastName()));
                response.setAssigneeEmail(user.getEmail());
            });
        }
        if (task.getParentTask() != null)
            response.setParentTaskId(task.getParentTask().getId());
        if (task.getSubtasks() != null)
            response.setSubtasks(task.getSubtasks().stream().map(this::mapToResponse).toList());
        return response;
    }

    private List<String> handleFileUploads(Task task, List<MultipartFile> files) {
        List<String> uploadedNames = new ArrayList<>();

        if (files != null && !files.isEmpty()) {
            List<String> currentAttachments = task.getAttachments();
            if (currentAttachments == null) {
                currentAttachments = new ArrayList<>();
            }

            try {
                // Ensure directory exists on your Dell machine
                if (!Files.exists(root)) {
                    Files.createDirectories(root);
                }

                for (MultipartFile file : files) {
                    // Simplified: UUID + Underscore + Original Name
                    String uniqueName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();

                    Files.copy(file.getInputStream(), this.root.resolve(uniqueName),
                            StandardCopyOption.REPLACE_EXISTING);

                    currentAttachments.add(uniqueName);
                    uploadedNames.add(uniqueName);
                }
                task.setAttachments(currentAttachments);
            } catch (IOException e) {
                throw new RuntimeException("File storage error: " + e.getMessage());
            }
        }
        return uploadedNames;
    }

    public List<TaskResponse> getTasksByAssignee(Long userId) {
        return taskRepository.findByAssigneeId(userId).stream().map(this::mapToResponse).toList();
    }

    private String getMemberName(Long userId) {
        if (userId == null)
            return "Unassigned";
        return userRepository.findById(userId).map(u -> capitalizeName(u.getFirstName() + " " + u.getLastName()))
                .orElse("Unknown");
    }

    private String capitalizeName(String name) {
        if (name == null || name.isBlank())
            return "";
        String[] words = name.toLowerCase().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words)
            if (!w.isEmpty())
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
        return sb.toString().trim();
    }

    public long getActiveTaskCount(Long userId) {
        return taskRepository.countActiveTasksByUserId(userId);
    }
}