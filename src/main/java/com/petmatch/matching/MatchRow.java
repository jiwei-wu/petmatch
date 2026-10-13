package com.petmatch.matching;

/** One row of the "my matches" query. Spring Data fills it from the SQL column aliases. */
public interface MatchRow {
    Long getMatchId();
    String getStatus();
    Double getScore();

    // Score breakdown, read out of the stored JSON
    Double getPartDistance();
    Double getPartTime();
    Double getPartColor();
    Double getPartSize();
    Double getPartCollar();

    Double getCreatedEpoch();

    // My own post
    Long getMyPostId();
    String getMyPostType();

    // The other side's post: public fields only
    Long getOtherPostId();
    String getOtherPublicArea();
    String getOtherColor();
    String getOtherSize();
    Boolean getOtherHasCollar();
    Double getOtherEpoch();
}