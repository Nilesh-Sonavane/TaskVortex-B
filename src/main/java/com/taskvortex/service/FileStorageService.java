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

    // Navigate up two levels to reach the "TaskVortex" main folder
    private final Path fileStorageLocation = Paths.get("")
            .toAbsolutePath()
            .getParent() // Up to 'task-vortex-backend'
            .getParent() // Up to 'TaskVortex' root
            .resolve("taskvortex-data")
            .resolve("attachments")
            .normalize();

    public FileStorageService() {
        try {
            // Creates TaskVortex/taskvortex-data/attachments
            Files.createDirectories(this.fileStorageLocation);
            System.out.println("Uploads will be saved to: " + fileStorageLocation.toString());
        } catch (Exception ex) {
            throw new RuntimeException("Could not create the data directory.", ex);
        }
    }

    public String storeFile(MultipartFile file) {
        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
        String fileName = UUID.randomUUID().toString() + "_" + originalFileName;

        try {
            Path targetLocation = this.fileStorageLocation.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            return fileName;
        } catch (IOException ex) {
            throw new RuntimeException("Could not store file " + fileName, ex);
        }
    }
}