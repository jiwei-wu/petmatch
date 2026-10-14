package com.petmatch.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.petmatch.dto.NotificationResponse;
import com.petmatch.exception.AuthException;
import com.petmatch.matching.MatchScorer;
import com.petmatch.matching.NotificationRow;
import com.petmatch.repository.NotificationRepository;
import com.petmatch.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Tag(name = "Notifications", description = "In-app notifications about your own posts")
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private static final int LATEST_LIMIT = 50;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationController(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Operation(summary = "My notifications", description = "Latest notifications for the logged-in user. They never contain the other person's identity.")
    @GetMapping
    public ResponseEntity<List<NotificationResponse>> myNotifications(Authentication authentication) {
        Long userId = currentUserId(authentication);
        List<NotificationResponse> body = notificationRepository.findLatestForUser(userId, LATEST_LIMIT)
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(body);
    }

    @Operation(summary = "Mark a notification as read", description = "A notification that is not yours is reported as not found.")
    @PostMapping("/{id}/read")
    @Transactional
    public ResponseEntity<Void> markRead(@PathVariable Long id, Authentication authentication) {
        Long userId = currentUserId(authentication);
        // 404 both when it does not exist and when it belongs to someone else, so ids cannot be probed
        if (notificationRepository.markRead(id, userId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
        }
        return ResponseEntity.noContent().build();
    }

    private NotificationResponse toResponse(NotificationRow row) {
        return new NotificationResponse(
                row.getId(),
                row.getType(),
                row.getMatchId(),
                row.getMyPostId(),
                row.getScore(),
                MatchScorer.toTime(row.getCreatedEpoch()),
                Boolean.TRUE.equals(row.getRead()));
    }

    private Long currentUserId(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new AuthException("User not found"))
                .getId();
    }
}