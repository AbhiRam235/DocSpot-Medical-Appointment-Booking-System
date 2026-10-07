package com.docspot.service;

import com.docspot.exception.BadRequestException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Set;
import java.util.UUID;

/**
 * Stores doctor documents on the local filesystem under app.upload.dir.
 * No S3 / cloud storage needed for a resume project.
 *
 * Directory layout:
 *   uploads/docs/{doctorId}/{uuid}_{originalFileName}
 */
@Service
@Slf4j
public class FileStorageService {

    @Value("${app.upload.dir}")
    private String uploadDir;

    private static final long   MAX_FILE_SIZE_MB = 5;
    private static final long   MAX_FILE_SIZE    = MAX_FILE_SIZE_MB * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/jpg",
            "image/png"
    );

    /** Create root upload directory on startup if it doesn't exist. */
    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Paths.get(uploadDir));
            log.info("Upload directory ready: {}", uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory: " + uploadDir, e);
        }
    }

    /**
     * Stores a file under uploads/docs/{doctorId}/
     * Returns the relative path that gets saved in the DB.
     */
    public String store(MultipartFile file, Long doctorId) {
        validateFile(file);

        try {
            // Create per-doctor subdirectory
            Path doctorDir = Paths.get(uploadDir, String.valueOf(doctorId));
            Files.createDirectories(doctorDir);

            // Build unique filename: uuid_originalname (keeps extension + human-readable name)
            String uniqueName = UUID.randomUUID() + "_" + sanitize(file.getOriginalFilename());
            Path targetPath   = doctorDir.resolve(uniqueName);

            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            // Return relative path stored in DB
            String relativePath = doctorId + "/" + uniqueName;
            log.info("File stored: {}", relativePath);
            return relativePath;

        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + e.getMessage(), e);
        }
    }

    /** Deletes a file by its relative path. Silently ignores missing files. */
    public void delete(String relativePath) {
        try {
            Path file = Paths.get(uploadDir, relativePath);
            Files.deleteIfExists(file);
            log.info("File deleted: {}", relativePath);
        } catch (IOException e) {
            log.warn("Could not delete file {}: {}", relativePath, e.getMessage());
        }
    }

    /** Returns absolute Path for serving a file (used if you add a file download endpoint). */
    public Path resolve(String relativePath) {
        return Paths.get(uploadDir).resolve(relativePath).normalize();
    }

    // ─── Validation ───────────────────────────────────────────────────────────

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File cannot be empty.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException(
                    "File size exceeds the maximum allowed limit of " + MAX_FILE_SIZE_MB + "MB.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new BadRequestException(
                    "Invalid file type. Only PDF, JPG, and PNG are accepted.");
        }
    }

    private String sanitize(String filename) {
        if (filename == null) return "file";
        // Strip path separators and whitespace
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
