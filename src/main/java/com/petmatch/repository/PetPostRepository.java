package com.petmatch.repository;

import com.petmatch.domain.PetPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PetPostRepository extends JpaRepository<PetPost, Long> {

    Page<PetPost> findByStatus(PetPost.Status status, Pageable pageable);

    Page<PetPost> findByTypeAndStatus(PetPost.Type type, PetPost.Status status, Pageable pageable);
}