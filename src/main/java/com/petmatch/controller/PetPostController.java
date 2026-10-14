package com.petmatch.controller;

import com.petmatch.dto.CreatePetPostRequest;
import com.petmatch.dto.PetPostResponse;
import com.petmatch.dto.UpdatePetPostRequest;
import com.petmatch.exception.AuthException;
import com.petmatch.repository.UserRepository;
import com.petmatch.service.PetPostService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import com.petmatch.matching.PetPostCreatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
public class PetPostController {

    private static final int MAX_PAGE_SIZE = 50;

    private final PetPostService petPostService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public PetPostController(PetPostService petPostService, UserRepository userRepository,
                             ApplicationEventPublisher eventPublisher) {
        this.petPostService = petPostService;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    private Long currentUserId(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException("User not found"))
                .getId();
    }

    @PostMapping
    public ResponseEntity<PetPostResponse> createPost(
            @Valid @RequestBody CreatePetPostRequest request,
            Authentication authentication) {
        Long userId = currentUserId(authentication);
        PetPostResponse response = petPostService.createPost(userId, request);
        // The post is already saved here, so the background matcher can see it
        eventPublisher.publishEvent(new PetPostCreatedEvent(response.getId()));
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<PetPostResponse>> listPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String species) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(petPostService.listOpenPosts(type, species, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PetPostResponse> getPost(@PathVariable Long id) {
        return ResponseEntity.ok(petPostService.getPost(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PetPostResponse> updatePost(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePetPostRequest request,
            Authentication authentication) {
        Long userId = currentUserId(authentication);
        return ResponseEntity.ok(petPostService.updatePost(id, userId, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(@PathVariable Long id, Authentication authentication) {
        Long userId = currentUserId(authentication);
        petPostService.deletePost(id, userId);
        return ResponseEntity.noContent().build();
    }
}