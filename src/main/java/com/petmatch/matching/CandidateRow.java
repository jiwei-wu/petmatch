package com.petmatch.matching;

/**
 * One row returned by the native candidate query: the candidate post's public fields,
 * its distance from the anchor post, and the anchor post's own scoring fields.
 * Spring Data fills this interface from the column aliases in the SQL.
 */
public interface CandidateRow {
    Long getId();
    String getType();
    String getPublicArea();
    String getColor();
    String getSize();
    Boolean getHasCollar();
    Double getEventEpoch();       // event time as epoch seconds (avoids driver-specific timestamp types)
    Double getDistanceMeters();   // used for scoring only, never returned to the client as-is

    String getAnchorType();
    String getAnchorColor();
    String getAnchorSize();
    Boolean getAnchorHasCollar();
    Double getAnchorEpoch();
}