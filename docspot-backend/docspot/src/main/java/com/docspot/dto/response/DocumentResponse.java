package com.docspot.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DocumentResponse {
    private Long   documentId;
    private String documentType;
    private String originalFileName;
    private String filePath;
    private LocalDateTime uploadedAt;
}