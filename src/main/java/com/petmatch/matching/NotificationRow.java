package com.petmatch.matching;

/** One row of the "my notifications" query. Spring Data fills it from the SQL column aliases. */
public interface NotificationRow {
    Long getId();
    String getType();
    Long getMatchId();
    Long getMyPostId();
    Double getScore();
    Double getCreatedEpoch();
    Boolean getRead();
}