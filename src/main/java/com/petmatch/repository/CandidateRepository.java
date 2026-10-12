package com.petmatch.repository;

import com.petmatch.domain.PetPost;
import com.petmatch.matching.CandidateRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CandidateRepository extends JpaRepository<PetPost, Long> {

    /** Used for the owner-only check. */
    @Query("select p.userId from PetPost p where p.id = :postId")
    Optional<Long> findOwnerId(@Param("postId") Long postId);

    /**
     * Hard filters, all done in the database:
     * - candidate is the opposite type (LOST vs SIGHTING) and the same species (case-insensitive)
     * - both posts are OPEN and belong to different users
     * - within radiusMeters (PostGIS ST_DWithin, uses the GiST index)
     * - the sighting is not earlier than the lost event, and within windowDays of it
     * Nearest first, capped at maxRows. Scoring happens afterwards in Java (MatchScorer).
     */
    @Query(value = """
            SELECT c.id AS "id",
                   c.type AS "type",
                   c.public_area AS "publicArea",
                   c.color AS "color",
                   c.size AS "size",
                   c.has_collar AS "hasCollar",
                   CAST(EXTRACT(EPOCH FROM c.event_time) AS double precision) AS "eventEpoch",
                   ST_Distance(a.exact_location, c.exact_location) AS "distanceMeters",
                   a.type AS "anchorType",
                   a.color AS "anchorColor",
                   a.size AS "anchorSize",
                   a.has_collar AS "anchorHasCollar",
                   CAST(EXTRACT(EPOCH FROM a.event_time) AS double precision) AS "anchorEpoch"
            FROM pet_posts a
            JOIN pet_posts c
              ON c.id <> a.id
             AND c.type <> a.type
             AND LOWER(c.species) = LOWER(a.species)
             AND c.status = 'OPEN'
             AND c.user_id <> a.user_id
             AND ST_DWithin(a.exact_location, c.exact_location, :radiusMeters)
             AND (
                  (a.type = 'LOST'
                   AND c.event_time >= a.event_time
                   AND c.event_time <= a.event_time + (:windowDays * INTERVAL '1 day'))
               OR (a.type = 'SIGHTING'
                   AND a.event_time >= c.event_time
                   AND a.event_time <= c.event_time + (:windowDays * INTERVAL '1 day'))
             )
            WHERE a.id = :postId
              AND a.status = 'OPEN'
            ORDER BY ST_Distance(a.exact_location, c.exact_location)
            LIMIT :maxRows
            """, nativeQuery = true)
    List<CandidateRow> findRawCandidates(
            @Param("postId") Long postId,
            @Param("radiusMeters") double radiusMeters,
            @Param("windowDays") int windowDays,
            @Param("maxRows") int maxRows);
}