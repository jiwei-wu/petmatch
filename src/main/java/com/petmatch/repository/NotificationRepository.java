package com.petmatch.repository;

import com.petmatch.domain.PetPost;
import com.petmatch.matching.NotificationRow;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Plain-SQL access to the notifications table (no entity is mapped for it yet). */
public interface NotificationRepository extends Repository<PetPost, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO notifications (user_id, type, payload)
            VALUES (:userId, :type, CAST(:payload AS jsonb))
            """, nativeQuery = true)
    int insert(@Param("userId") Long userId, @Param("type") String type, @Param("payload") String payload);

    @Query(value = """
            SELECT n.id AS "id",
                   n.type AS "type",
                   CAST(n.payload->>'matchId' AS bigint) AS "matchId",
                   CAST(n.payload->>'myPostId' AS bigint) AS "myPostId",
                   CAST(n.payload->>'score' AS double precision) AS "score",
                   CAST(EXTRACT(EPOCH FROM n.created_at) AS double precision) AS "createdEpoch",
                   (n.read_at IS NOT NULL) AS "read"
            FROM notifications n
            WHERE n.user_id = :userId
            ORDER BY n.created_at DESC, n.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<NotificationRow> findLatestForUser(@Param("userId") Long userId, @Param("limit") int limit);

    /**
     * Marks a notification as read, but only if it belongs to this user.
     * Returns 1 if it is theirs (even if it was already read), 0 otherwise.
     */
    @Modifying
    @Query(value = """
            UPDATE notifications SET read_at = COALESCE(read_at, now())
            WHERE id = :id AND user_id = :userId
            """, nativeQuery = true)
    int markRead(@Param("id") Long id, @Param("userId") Long userId);
}