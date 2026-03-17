package com.taskvortex.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {

    private final Path baseStorageLocation;
    private final Path profilesLocation;
    private final Path attachmentsLocation;

    public FileStorageService() {
        // 1. Define the base directory
        this.baseStorageLocation = Paths.get("")
                .toAbsolutePath()
                .getParent()
                .getParent()
                .resolve("taskvortex-data")
                .normalize();

        // 2. Define the sub-directories
        this.profilesLocation = this.baseStorageLocation.resolve("profiles");
        this.attachmentsLocation = this.baseStorageLocation.resolve("attachments");

        try {
            // 3. Create both directories if they don't exist
            Files.createDirectories(this.profilesLocation);
            Files.createDirectories(this.attachmentsLocation);
            System.out.println("Data directories initialized at: " + this.baseStorageLocation.toString());
        } catch (Exception ex) {
            throw new RuntimeException("Could not create the data directories.", ex);
        }
    }

    // Method specifically for Profile Images
    public String storeProfileFile(MultipartFile file) {
        return saveFileToDisk(file, this.profilesLocation);
    }

    // Method specifically for Task/Project Attachments
    public String storeAttachmentFile(MultipartFile file) {
        return saveFileToDisk(file, this.attachmentsLocation);
    }

    // --- NEW: DELETE OLD PROFILE IMAGE ---
    public void deleteProfileFile(String fileUrl) {
        if (fileUrl == null || fileUrl.trim().isEmpty()) {
            return; // No old image to delete
        }

        try {
            // Extract the filename from the URL (e.g., "/profiles/uuid_photo.png" ->
            // "uuid_photo.png")
            String fileName = fileUrl.substring(fileUrl.lastIndexOf('/') + 1);

            // Resolve the exact path on the server
            Path filePath = this.profilesLocation.resolve(fileName).normalize();

            // Security check: Make sure we don't accidentally delete files outside the
            // profiles folder
            if (filePath.getParent().equals(this.profilesLocation)) {
                Files.deleteIfExists(filePath);
                System.out.println("Deleted old profile image: " + fileName);
            }
        } catch (IOException ex) {
            // We catch the error but don't crash the app, so the new upload can still
            // proceed
            System.err.println("Could not delete old profile image: " + ex.getMessage());
        }
    }

    // Core private method that does the actual saving
    private String saveFileToDisk(MultipartFile file, Path targetLocation) {
        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
        String fileName = UUID.randomUUID().toString() + "_" + originalFileName;

        try {
            Path targetPath = targetLocation.resolve(fileName);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            return fileName;
        } catch (IOException ex) {
            throw new RuntimeException("Could not store file " + fileName, ex);
        }
    }
}