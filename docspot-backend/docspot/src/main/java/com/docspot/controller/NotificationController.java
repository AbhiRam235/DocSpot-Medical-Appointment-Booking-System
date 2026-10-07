package com.docspot.controller;

import com.docspot.dto.response.ApiResponse;
import com.docspot.dto.response.NotificationResponse;
import com.docspot.dto.response.UnreadCountResponse;
import com.docspot.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT')")
@Tag(name = "Notifications", description = "In-app notification inbox — available to DOCTOR and PATIENT")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(
            summary     = "Get own notifications",
            description = "Returns paginated notifications for the logged-in user, newest first."
    )
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> getMyNotifications(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(
                ApiResponse.success("Notifications fetched.",
                        notificationService.getMyNotifications(userDetails.getUsername(), pageable))
        );
    }

    @PutMapping("/{notificationId}/read")
    @Operation(
            summary     = "Mark one notification as read",
            description = "Ownership enforced — users can only mark their own notifications."
    )
    public ResponseEntity<ApiResponse<String>> markOneAsRead(
            @PathVariable Long notificationId,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        notificationService.markOneAsRead(notificationId, userDetails.getUsername()))
        );
    }

    @PutMapping("/read-all")
    @Operation(summary = "Mark all notifications as read")
    public ResponseEntity<ApiResponse<String>> markAllAsRead(
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        notificationService.markAllAsRead(userDetails.getUsername()))
        );
    }

    @GetMapping("/unread-count")
    @Operation(
            summary     = "Get unread notification count",
            description = "Returns { count: N } — use this to drive the bell icon badge on the frontend."
    )
    public ResponseEntity<ApiResponse<UnreadCountResponse>> getUnreadCount(
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(
                ApiResponse.success("Unread count fetched.",
                        notificationService.getUnreadCount(userDetails.getUsername()))
        );
    }
}
