package com.petmatch.service;

import com.petmatch.matching.CandidateRow;
import com.petmatch.matching.MatchScorer;
import com.petmatch.matching.ScoringProperties;
import com.petmatch.repository.CandidateRepository;
import com.petmatch.repository.MatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MatchService {

    private static final int MAX_MATCHES_PER_POST = 20;

    private final CandidateRepository candidateRepository;
    private final MatchRepository matchRepository;
    private final MatchScorer scorer;
    private final ScoringProperties properties;

    public MatchService(CandidateRepository candidateRepository, MatchRepository matchRepository,
                        MatchScorer scorer, ScoringProperties properties) {
        this.candidateRepository = candidateRepository;
        this.matchRepository = matchRepository;
        this.scorer = scorer;
        this.properties = properties;
    }

    private record Scored(CandidateRow row, MatchScorer.Result result) {}

    /**
     * Finds candidates for the given post, keeps the ones scoring at least the configured
     * threshold (best first, capped), and stores any pair not stored yet.
     * Returns how many NEW matches were created, so callers can notify only for new ones.
     */
    @Transactional
    public int generateMatchesFor(Long postId) {
        List<CandidateRow> rows = candidateRepository.findRawCandidates(
                postId,
                CandidateService.SEARCH_RADIUS_METERS,
                CandidateService.TIME_WINDOW_DAYS,
                CandidateService.MAX_ROWS_FROM_DB);

        List<Scored> strong = rows.stream()
                .map(row -> new Scored(row, scorer.score(row)))
                .filter(s -> s.result().total() >= properties.getMatchThreshold())
                .sorted(Comparator.comparingDouble((Scored s) -> s.result().total()).reversed())
                .limit(MAX_MATCHES_PER_POST)
                .toList();

        int created = 0;
        for (Scored s : strong) {
            boolean anchorIsLost = "LOST".equals(s.row().getAnchorType());
            Long lostId = anchorIsLost ? postId : s.row().getId();
            Long sightingId = anchorIsLost ? s.row().getId() : postId;
            created += matchRepository.insertIfAbsent(
                    lostId, sightingId, s.result().total(), toJson(s.result().parts()));
        }
        return created;
    }

    /** Keys are our own fixed names and values are plain numbers, so a small hand-built JSON is safe. */
    private static String toJson(Map<String, Double> parts) {
        return parts.entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\":" + e.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }
}