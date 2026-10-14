package com.petmatch.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.petmatch.dto.MatchResponse;
import com.petmatch.exception.AuthException;
import com.petmatch.matching.MatchRow;
import com.petmatch.matching.MatchScorer;
import com.petmatch.repository.MatchQueryRepository;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "Matches", description = "Persisted matches between a lost report and a sighting")
@RestController
@RequestMapping("/api/matches")
public class MatchController {

    private static final int LIST_LIMIT = 50;

    private final MatchQueryRepository matchQueryRepository;
    private final UserRepository userRepository;

    public MatchController(MatchQueryRepository matchQueryRepository, UserRepository userRepository) {
        this.matchQueryRepository = matchQueryRepository;
        this.userRepository = userRepository;
    }

    @Operation(summary = "My matches", description = "Matches that involve one of your posts, with score, breakdown and only the public fields of the other post.")
    @GetMapping
    public ResponseEntity<List<MatchResponse>> myMatches(Authentication authentication) {
        Long userId = currentUserId(authentication);
        List<MatchResponse> body = matchQueryRepository.findForUser(userId, LIST_LIMIT)
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(body);
    }

    @Operation(summary = "Confirm a match", description = "Marks a suggested match as confirmed. A match that is not yours is reported as not found.")
    @PostMapping("/{id}/confirm")
    @Transactional
    public ResponseEntity<Void> confirm(@PathVariable Long id, Authentication authentication) {
        return changeStatus(id, authentication, "CONFIRMED");
    }

    @Operation(summary = "Dismiss a match", description = "Marks a suggested match as dismissed. A match that is not yours is reported as not found.")
    @PostMapping("/{id}/dismiss")
    @Transactional
    public ResponseEntity<Void> dismiss(@PathVariable Long id, Authentication authentication) {
        return changeStatus(id, authentication, "DISMISSED");
    }

    private ResponseEntity<Void> changeStatus(Long matchId, Authentication authentication, String target) {
        Long userId = currentUserId(authentication);

        // 404 both when the match does not exist and when it is not yours, so ids cannot be probed
        String current = matchQueryRepository.findStatusForParticipant(matchId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found"));

        if (current.equals(target)) {
            return ResponseEntity.noContent().build();      // repeating the same decision is harmless
        }
        if (matchQueryRepository.updateStatusIfSuggested(matchId, target) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This match was already decided");
        }
        return ResponseEntity.noContent().build();
    }

    private MatchResponse toResponse(MatchRow row) {
        Map<String, Double> breakdown = new LinkedHashMap<>();
        putIfPresent(breakdown, "distance", row.getPartDistance());
        putIfPresent(breakdown, "time", row.getPartTime());
        putIfPresent(breakdown, "color", row.getPartColor());
        putIfPresent(breakdown, "size", row.getPartSize());
        putIfPresent(breakdown, "collar", row.getPartCollar());

        return new MatchResponse(
                row.getMatchId(),
                row.getStatus(),
                row.getScore(),
                breakdown,
                MatchScorer.toTime(row.getCreatedEpoch()),
                row.getMyPostId(),
                row.getMyPostType(),
                row.getOtherPostId(),
                row.getOtherPublicArea(),
                row.getOtherColor(),
                row.getOtherSize(),
                row.getOtherHasCollar(),
                MatchScorer.toTime(row.getOtherEpoch()));
    }

    private static void putIfPresent(Map<String, Double> map, String key, Double value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private Long currentUserId(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new AuthException("User not found"))
                .getId();
    }
}