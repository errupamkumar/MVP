package com.srmecotech.plantride.notification;

import com.srmecotech.plantride.common.security.AuthenticatedUser;
import com.srmecotech.plantride.notification.dto.NotificationDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@Validated
@Tag(name = "7. Notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "My notifications, newest first")
    public List<NotificationDto> list(@AuthenticationPrincipal AuthenticatedUser user,
                                      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return notificationService.forUser(user.id(), limit);
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark all my notifications read")
    public Map<String, Integer> readAll(@AuthenticationPrincipal AuthenticatedUser user) {
        return Map.of("updated", notificationService.markAllRead(user.id()));
    }
}
