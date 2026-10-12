package com.petmatch.repository;

import com.petmatch.domain.PetPost;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Writes to the matches table with plain SQL. The domain type here is only a required
 * placeholder for Spring Data; no entity is mapped for matches yet.
 */
public interface MatchRepository extends Repository<PetPost, Long> {

    /**
     * Inserts a match if this (lost, sighting) pair is not stored yet.
     * Returns 1 if a new row was created, 0 if it already existed.
     * The UNIQUE(lost_post_id, sighting_post_id) constraint makes this safe to run repeatedly
     * and safe even if two requests race each other.
     */
    @Modifying
    @Query(value = """
            INSERT INTO matches (lost_post_id, sighting_post_id, score, score_breakdown)
            VALUES (:lostId, :sightingId, :score, CAST(:breakdown AS jsonb))
            ON CONFLICT (lost_post_id, sighting_post_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("lostId") Long lostId,
            @Param("sightingId") Long sightingId,
            @Param("score") double score,
            @Param("breakdown") String breakdown);
}