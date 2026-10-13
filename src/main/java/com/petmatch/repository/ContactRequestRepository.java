package com.petmatch.repository;

import com.petmatch.domain.PetPost;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Plain-SQL access for contact requests (no entity is mapped for them yet). */
public interface ContactRequestRepository extends Repository<PetPost, Long> {

    /** How one user relates to a match: who the other person is and which posts are whose. */
    interface ParticipationRow {
        Long getOtherUserId();
        Long getMyPostId();
        Long getOtherPostId();
        String getMatchStatus();
        Double getScore();
    }

    /** The bare facts needed to decide on a request. */
    interface InfoRow {
        Long getMatchId();
        Long getFromUserId();
        Long getToUserId();
        String getStatus();
    }

    /** One line of "my contact requests". contactEmail is filled only once a request is ACCEPTED. */
    interface ListRow {
        Long getId();
        Long getMatchId();
        String getDirection();
        String getStatus();
        String getMessage();
        Double getCreatedEpoch();
        String getContactEmail();
    }

    /** Empty if the match does not exist or this user owns neither of its two posts. */
    @Query(value = """
            SELECT CASE WHEN lp.user_id = :userId THEN sp.user_id ELSE lp.user_id END AS "otherUserId",
                   CASE WHEN lp.user_id = :userId THEN lp.id ELSE sp.id END AS "myPostId",
                   CASE WHEN lp.user_id = :userId THEN sp.id ELSE lp.id END AS "otherPostId",
                   m.status AS "matchStatus",
                   CAST(m.score AS double precision) AS "score"
            FROM matches m
            JOIN pet_posts lp ON lp.id = m.lost_post_id
            JOIN pet_posts sp ON sp.id = m.sighting_post_id
            WHERE m.id = :matchId AND (lp.user_id = :userId OR sp.user_id = :userId)
            """, nativeQuery = true)
    Optional<ParticipationRow> findParticipation(@Param("matchId") Long matchId, @Param("userId") Long userId);

    @Query(value = """
            SELECT COUNT(*) FROM contact_requests
            WHERE match_id = :matchId AND from_user_id = :fromUserId
            """, nativeQuery = true)
    long countFromUser(@Param("matchId") Long matchId, @Param("fromUserId") Long fromUserId);

    /** Returns 1 if created, 0 if this sender already has a request for this match. */
    @Modifying
    @Query(value = """
            INSERT INTO contact_requests (match_id, from_user_id, to_user_id, message)
            VALUES (:matchId, :fromUserId, :toUserId, :message)
            ON CONFLICT (match_id, from_user_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("matchId") Long matchId, @Param("fromUserId") Long fromUserId,
                       @Param("toUserId") Long toUserId, @Param("message") String message);

    @Query(value = """
            SELECT id FROM contact_requests WHERE match_id = :matchId AND from_user_id = :fromUserId
            """, nativeQuery = true)
    Long findIdByMatchAndSender(@Param("matchId") Long matchId, @Param("fromUserId") Long fromUserId);

    @Query(value = """
            SELECT cr.match_id AS "matchId",
                   cr.from_user_id AS "fromUserId",
                   cr.to_user_id AS "toUserId",
                   cr.status AS "status"
            FROM contact_requests cr
            WHERE cr.id = :id
            """, nativeQuery = true)
    Optional<InfoRow> findInfo(@Param("id") Long id);

    /** Only a PENDING request can change; returns 0 if it was already decided. */
    @Modifying
    @Query(value = """
            UPDATE contact_requests SET status = :status WHERE id = :id AND status = 'PENDING'
            """, nativeQuery = true)
    int updateStatusIfPending(@Param("id") Long id, @Param("status") String status);

    /**
     * Requests I sent or received. The other person's email is returned ONLY for ACCEPTED
     * requests; for every other status the column is NULL, so it can never leak early.
     */
    @Query(value = """
            SELECT cr.id AS "id",
                   cr.match_id AS "matchId",
                   CASE WHEN cr.from_user_id = :userId THEN 'SENT' ELSE 'RECEIVED' END AS "direction",
                   cr.status AS "status",
                   cr.message AS "message",
                   CAST(EXTRACT(EPOCH FROM cr.created_at) AS double precision) AS "createdEpoch",
                   CASE WHEN cr.status = 'ACCEPTED' THEN u.email ELSE NULL END AS "contactEmail"
            FROM contact_requests cr
            JOIN users u ON u.id = CASE WHEN cr.from_user_id = :userId THEN cr.to_user_id ELSE cr.from_user_id END
            WHERE cr.from_user_id = :userId OR cr.to_user_id = :userId
            ORDER BY cr.created_at DESC, cr.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<ListRow> findForUser(@Param("userId") Long userId, @Param("limit") int limit);
}