package com.petmatch.controller;

import com.petmatch.dto.CreatePetPostRequest;
import com.petmatch.dto.PetPostResponse;
import com.petmatch.exception.AuthException;
import com.petmatch.repository.UserRepository;
import com.petmatch.service.PetPostService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
public class PetPostController {

    private final PetPostService petPostService;
    private final UserRepository userRepository;

    public PetPostController(PetPostService petPostService, UserRepository userRepository) {
        this.petPostService = petPostService;
        this.userRepository = userRepository;
    }

    private Long currentUserId(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException("用户不存在"))
                .getId();
    }

    @PostMapping
    public ResponseEntity<PetPostResponse> createPost(
            @Valid @RequestBody CreatePetPostRequest request,
            Authentication authentication) {
        Long userId = currentUserId(authentication);
        PetPostResponse response = petPostService.createPost(userId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<PetPostResponse>> listPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(petPostService.listOpenPosts(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PetPostResponse> getPost(@PathVariable Long id) {
        return ResponseEntity.ok(petPostService.getPost(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(@PathVariable Long id, Authentication authentication) {
        Long userId = currentUserId(authentication);
        petPostService.deletePost(id, userId);
        return ResponseEntity.noContent().build();
    }
}