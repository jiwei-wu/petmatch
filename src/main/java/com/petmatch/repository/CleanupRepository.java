package com.petmatch.repository;

import com.petmatch.domain.PetPost;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Bulk maintenance statements, done in the database in one go instead of row by row. */
public interface CleanupRepository extends Repository<PetPost, Long> {

    @Modifying
    @Query(value = """
            UPDATE pet_posts SET status = 'EXPIRED'
            WHERE status = 'OPEN' AND expires_at < now()
            """, nativeQuery = true)
    int expireOverdueOpenPosts();

    /** Matches and contact requests of these posts go with them (ON DELETE CASCADE, migration V4). */
    @Modifying
    @Query(value = """
            DELETE FROM pet_posts
            WHERE status = 'EXPIRED' AND expires_at < now() - (:days * INTERVAL '1 day')
            """, nativeQuery = true)
    int deleteLongExpiredPosts(@Param("days") int days);

    @Modifying
    @Query(value = """
            DELETE FROM notifications
            WHERE created_at < now() - (:days * INTERVAL '1 day')
            """, nativeQuery = true)
    int deleteOldNotifications(@Param("days") int days);
}