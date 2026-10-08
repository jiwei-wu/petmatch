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
                null, // microchipHash：今天先不做芯片功能，留空
                OffsetDateTime.parse(request.getEventTime()),
                request.getLatitude(),
                request.getLongitude(),
                request.getPublicArea(),
                OffsetDateTime.now().plusDays(DEFAULT_EXPIRY_DAYS)
        );

        PetPost saved = petPostRepository.save(post);
        return toResponse(saved);
    }

    public Page<PetPostResponse> listOpenPosts(Pageable pageable) {
        return petPostRepository.findByStatus(PetPost.Status.OPEN, pageable)
                .map(this::toResponse);
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