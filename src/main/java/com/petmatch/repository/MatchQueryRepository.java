package com.petmatch.repository;

import com.petmatch.domain.PetPost;
import com.petmatch.matching.MatchRow;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Reads and updates matches from a participant's point of view (plain SQL, no entity mapped yet). */
public interface MatchQueryRepository extends Repository<PetPost, Long> {

    /** All non-dismissed matches where one of the two posts belongs to this user, best first. */
    @Query(value = """
            SELECT m.id AS "matchId",
                   m.status AS "status",
                   CAST(m.score AS double precision) AS "score",
                   CAST(m.score_breakdown->>'distance' AS double precision) AS "partDistance",
                   CAST(m.score_breakdown->>'time' AS double precision) AS "partTime",
                   CAST(m.score_breakdown->>'color' AS double precision) AS "partColor",
                   CAST(m.score_breakdown->>'size' AS double precision) AS "partSize",
                   CAST(m.score_breakdown->>'collar' AS double precision) AS "partCollar",
                   CAST(EXTRACT(EPOCH FROM m.created_at) AS double precision) AS "createdEpoch",
                   mine.id AS "myPostId",
                   mine.type AS "myPostType",
                   other.id AS "otherPostId",
                   other.public_area AS "otherPublicArea",
                   other.color AS "otherColor",
                   other.size AS "otherSize",
                   other.has_collar AS "otherHasCollar",
                   CAST(EXTRACT(EPOCH FROM other.event_time) AS double precision) AS "otherEpoch"
            FROM matches m
            JOIN pet_posts lp ON lp.id = m.lost_post_id
            JOIN pet_posts sp ON sp.id = m.sighting_post_id
            JOIN pet_posts mine ON mine.id = CASE WHEN lp.user_id = :userId THEN lp.id ELSE sp.id END
            JOIN pet_posts other ON other.id = CASE WHEN lp.user_id = :userId THEN sp.id ELSE lp.id END
            WHERE (lp.user_id = :userId OR sp.user_id = :userId)
              AND m.status <> 'DISMISSED'
            ORDER BY m.score DESC, m.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<MatchRow> findForUser(@Param("userId") Long userId, @Param("limit") int limit);

    /** The match's status, but only if this user owns one of its two posts (otherwise empty). */
    @Query(value = """
            SELECT m.status
            FROM matches m
            JOIN pet_posts lp ON lp.id = m.lost_post_id
            JOIN pet_posts sp ON sp.id = m.sighting_post_id
            WHERE m.id = :matchId AND (lp.user_id = :userId OR sp.user_id = :userId)
            """, nativeQuery = true)
    Optional<String> findStatusForParticipant(@Param("matchId") Long matchId, @Param("userId") Long userId);

    /** Only a SUGGESTED match can change; returns 0 if it was already decided. */
    @Modifying
    @Query(value = """
            UPDATE matches SET status = :status
            WHERE id = :matchId AND status = 'SUGGESTED'
            """, nativeQuery = true)
    int updateStatusIfSuggested(@Param("matchId") Long matchId, @Param("status") String status);
}