package com.petmatch.service;

import com.petmatch.dto.CandidateResponse;
import com.petmatch.matching.CandidateRow;
import com.petmatch.matching.MatchScorer;
import com.petmatch.repository.CandidateRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;

@Service
public class CandidateService {

    // Hard-filter settings, shared with MatchService (scoring parameters live in ScoringProperties)
    static final double SEARCH_RADIUS_METERS = 10_000;
    static final int TIME_WINDOW_DAYS = 30;
    static final int MAX_ROWS_FROM_DB = 200;

    private static final int TOP_N = 5;

    private final CandidateRepository candidateRepository;
    private final MatchScorer scorer;

    public CandidateService(CandidateRepository candidateRepository, MatchScorer scorer) {
        this.candidateRepository = candidateRepository;
        this.scorer = scorer;
    }

    public List<CandidateResponse> findCandidates(Long postId, Long requesterId) {
        Long ownerId = candidateRepository.findOwnerId(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));
        if (!ownerId.equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the post owner can view candidates");
        }

        List<CandidateRow> rows = candidateRepository.findRawCandidates(
                postId, SEARCH_RADIUS_METERS, TIME_WINDOW_DAYS, MAX_ROWS_FROM_DB);

        return rows.stream()
                .map(this::toScoredResponse)
                .sorted(Comparator.comparingDouble(CandidateResponse::score).reversed())
                .limit(TOP_N)
                .toList();
    }

    private CandidateResponse toScoredResponse(CandidateRow row) {
        MatchScorer.Result result = scorer.score(row);
        return new CandidateResponse(
                row.getId(),
                row.getType(),
                row.getPublicArea(),
                row.getColor(),
                row.getSize(),
                row.getHasCollar(),
                MatchScorer.toTime(row.getEventEpoch()),
                bucket(row.getDistanceMeters()),
                result.total(),
                result.parts());
    }

    /** Privacy: only a coarse distance band is ever exposed. */
    static String bucket(double meters) {
        if (meters < 500) return "under 500 m";
        if (meters < 1_000) return "500 m - 1 km";
        if (meters < 5_000) return "1 - 5 km";
        return "5 - 10 km";
    }
}