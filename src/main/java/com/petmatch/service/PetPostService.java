package com.petmatch.service;

import com.petmatch.domain.PetPost;
import com.petmatch.dto.CreatePetPostRequest;
import com.petmatch.dto.PetPostResponse;
import com.petmatch.exception.AuthException;
import com.petmatch.repository.PetPostRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Locale;

@Service
public class PetPostService {

    private static final int DEFAULT_EXPIRY_DAYS = 60;

    private final PetPostRepository petPostRepository;

    public PetPostService(PetPostRepository petPostRepository) {
        this.petPostRepository = petPostRepository;
    }

    public PetPostResponse createPost(Long userId, CreatePetPostRequest request) {
        PetPost post = new PetPost(
                userId,
                PetPost.Type.valueOf(request.getType()),
                request.getSpecies(),
                request.getColor(),
                request.getSize(),
                request.getHasCollar(),
                request.getDescription(),
                null, // microchipHash:芯片功能后面再做,先留空
                request.getEventTime(),
                request.getLatitude(),
                request.getLongitude(),
                request.getPublicArea(),
                OffsetDateTime.now().plusDays(DEFAULT_EXPIRY_DAYS)
        );

        PetPost saved = petPostRepository.save(post);
        return toResponse(saved);
    }

    public Page<PetPostResponse> listOpenPosts(String type, String species, Pageable pageable) {
        PetPost.Type typeFilter = parseType(type);
        String speciesFilter = (species == null || species.isBlank()) ? null : species.trim();
        PetPost.Status open = PetPost.Status.OPEN;

        Page<PetPost> page;
        if (typeFilter != null && speciesFilter != null) {
            page = petPostRepository.findByTypeAndStatusAndSpeciesIgnoreCase(typeFilter, open, speciesFilter, pageable);
        } else if (typeFilter != null) {
            page = petPostRepository.findByTypeAndStatus(typeFilter, open, pageable);
        } else if (speciesFilter != null) {
            page = petPostRepository.findByStatusAndSpeciesIgnoreCase(open, speciesFilter, pageable);
        } else {
            page = petPostRepository.findByStatus(open, pageable);
        }
        return page.map(this::toResponse);
    }

    public PetPostResponse getPost(Long postId) {
        PetPost post = petPostRepository.findById(postId)
                .orElseThrow(() -> new AuthException("帖子不存在"));
        return toResponse(post);
    }

    public void deletePost(Long postId, Long requesterUserId) {
        PetPost post = petPostRepository.findById(postId)
                .orElseThrow(() -> new AuthException("帖子不存在"));

        if (!post.getUserId().equals(requesterUserId)) {
            throw new AuthException("无权操作他人的帖子");
        }

        petPostRepository.delete(post);
    }

    private PetPost.Type parseType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return PetPost.Type.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new AuthException("类型必须是 LOST 或 SIGHTING");
        }
    }

    private PetPostResponse toResponse(PetPost post) {
        return new PetPostResponse(
                post.getId(),
                post.getType().name(),
                post.getSpecies(),
                post.getColor(),
                post.getSize(),
                post.getHasCollar(),
                post.getDescription(),
                post.getEventTime(),
                post.getPublicArea(),
                post.getStatus().name(),
                post.getCreatedAt()
        );
    }
}