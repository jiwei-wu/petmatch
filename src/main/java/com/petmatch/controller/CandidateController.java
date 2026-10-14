package com.petmatch.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.petmatch.dto.CandidateResponse;
import com.petmatch.exception.AuthException;
import com.petmatch.repository.UserRepository;
import com.petmatch.service.CandidateService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Matching", description = "Preview of likely matches for one of your posts")
@RestController
@RequestMapping("/api/matching")
public class CandidateController {

    private final CandidateService candidateService;
    private final UserRepository userRepository;

    public CandidateController(CandidateService candidateService, UserRepository userRepository) {
        this.candidateService = candidateService;
        this.userRepository = userRepository;
    }

    @Operation(summary = "Top candidates for my post", description = "Owner only. Up to 5 best candidates with an explainable score breakdown. Distance is shown only as a coarse band. A post that is not yours is reported as not found.")
    @GetMapping("/posts/{id}/candidates")
    public ResponseEntity<List<CandidateResponse>> candidates(
            @PathVariable Long id, Authentication authentication) {
        Long userId = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new AuthException("User not found"))
                .getId();
        return ResponseEntity.ok(candidateService.findCandidates(id, userId));
    }
}