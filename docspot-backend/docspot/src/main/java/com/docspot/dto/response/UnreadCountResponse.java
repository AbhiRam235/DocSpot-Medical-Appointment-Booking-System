package com.docspot.dto.response;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UnreadCountResponse {
    private long count;
}
