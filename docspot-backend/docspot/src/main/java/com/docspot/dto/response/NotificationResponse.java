package com.docspot.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationResponse {
    private Long          notificationId;
    private String        title;
    private String        message;
    private boolean       read;
    private LocalDateTime createdAt;
}
