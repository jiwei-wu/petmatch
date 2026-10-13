package com.petmatch.controller;

import com.petmatch.dto.ContactRequestResponse;
import com.petmatch.dto.CreateContactRequest;
import com.petmatch.exception.AuthException;
import com.petmatch.repository.UserRepository;
import com.petmatch.service.ContactService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ContactRequestController {

    private final ContactService contactService;
    private final UserRepository userRepository;

    public ContactRequestController(ContactService contactService, UserRepository userRepository) {
        this.contactService = contactService;
        this.userRepository = userRepository;
    }

    @PostMapping("/matches/{matchId}/contact-requests")
    public ResponseEntity<ContactRequestResponse> create(
            @PathVariable Long matchId,
            @Valid @RequestBody CreateContactRequest request,
            Authentication authentication) {
        Long userId = currentUserId(authentication);
        ContactRequestResponse created = contactService.create(matchId, userId, request.message());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/contact-requests")
    public ResponseEntity<List<ContactRequestResponse>> mine(Authentication authentication) {
        return ResponseEntity.ok(contactService.listMine(currentUserId(authentication)));
    }

    @PostMapping("/contact-requests/{id}/accept")
    public ResponseEntity<Void> accept(@PathVariable Long id, Authentication authentication) {
        contactService.decide(id, currentUserId(authentication), true);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/contact-requests/{id}/decline")
    public ResponseEntity<Void> decline(@PathVariable Long id, Authentication authentication) {
        contactService.decide(id, currentUserId(authentication), false);
        return ResponseEntity.noContent().build();
    }

    private Long currentUserId(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new AuthException("User not found"))
                .getId();
    }
}