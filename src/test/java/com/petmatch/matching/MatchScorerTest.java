package com.petmatch.matching;

import com.petmatch.matching.MatchScorer.Side;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MatchScorerTest {

    private final MatchScorer scorer = new MatchScorer(new ScoringProperties());
    private final OffsetDateTime t0 = OffsetDateTime.parse("2026-10-01T10:00:00Z");

    @Test
    void closeDistanceGetsFullScoreAndFarDistanceDecays() {
        assertThat(scorer.distanceScore(300, 0)).isEqualTo(1.0);
        assertThat(scorer.distanceScore(1000, 0)).isCloseTo(0.5, within(0.001));   // 500 m beyond tolerance: halved once
        assertThat(scorer.distanceScore(1500, 0)).isCloseTo(0.25, within(0.001));
    }

    @Test
    void toleranceGrowsWithElapsedDaysButIsCapped() {
        assertThat(scorer.distanceScore(750, 7)).isEqualTo(1.0);   // tolerance is 500 + 250 after 7 days
        assertThat(scorer.distanceScore(750, 0)).isLessThan(1.0);
        // capped at 2000 m: the tolerance never grows without limit
        assertThat(scorer.distanceScore(2500, 365)).isCloseTo(0.5, within(0.001));
    }

    @Test
    void timeHalvesEverySevenDays() {
        assertThat(scorer.timeScore(0)).isEqualTo(1.0);
        assertThat(scorer.timeScore(7)).isCloseTo(0.5, within(0.001));
        assertThat(scorer.timeScore(14)).isCloseTo(0.25, within(0.001));
    }

    @Test
    void colorUsesSynonymsSimilarityAndNeutralForUnknown() {
        assertThat(scorer.colorScore("Gray", "grey")).isEqualTo(1.0);
        assertThat(scorer.colorScore("ginger", "orange")).isEqualTo(1.0);
        assertThat(scorer.colorScore("orange", "cream")).isEqualTo(0.6);
        assertThat(scorer.colorScore("cream", "orange")).isEqualTo(0.6);  // order does not matter
        assertThat(scorer.colorScore("black", "white")).isEqualTo(0.1);
        assertThat(scorer.colorScore(null, "black")).isEqualTo(0.5);
    }

    @Test
    void sizeIsOrdinal() {
        assertThat(scorer.sizeScore("small", "small")).isEqualTo(1.0);
        assertThat(scorer.sizeScore("small", "medium")).isEqualTo(0.5);
        assertThat(scorer.sizeScore("small", "large")).isEqualTo(0.0);
        assertThat(scorer.sizeScore(null, "large")).isEqualTo(0.5);
    }

    @Test
    void collarIsAsymmetric() {
        assertThat(scorer.collarScore(true, true)).isEqualTo(1.0);
        assertThat(scorer.collarScore(true, false)).isEqualTo(0.4);
        assertThat(scorer.collarScore(false, true)).isEqualTo(0.1);
        assertThat(scorer.collarScore(false, false)).isEqualTo(0.6);
        assertThat(scorer.collarScore(null, true)).isEqualTo(0.5);
    }

    @Test
    void perfectMatchScores100AndBreakdownAddsUp() {
        Side lost = new Side("orange", "medium", true, t0);
        Side seen = new Side("orange", "medium", true, t0);
        var r = scorer.score(lost, seen, 100);
        assertThat(r.total()).isEqualTo(100.0);
        assertThat(r.parts().values().stream().mapToDouble(Double::doubleValue).sum())
                .isCloseTo(100.0, within(0.01));
    }

    @Test
    void closerAndRecentAndSimilarBeatsFarOldAndDifferent() {
        Side lost = new Side("orange", "medium", true, t0);
        var good = scorer.score(lost, new Side("ginger", "medium", true, t0.plusDays(1)), 300);
        var bad = scorer.score(lost, new Side("black", "large", false, t0.plusDays(20)), 6000);
        assertThat(good.total()).isGreaterThan(bad.total() + 40);
    }

    @Test
    void changingPropertiesChangesResultWithoutTouchingScorer() {
        var props = new ScoringProperties();
        props.setDistanceWeight(0);
        props.setTimeWeight(0);
        props.setSizeWeight(0);
        props.setCollarWeight(0);
        props.setColorWeight(100);
        var colorOnly = new MatchScorer(props);
        var r = colorOnly.score(new Side("orange", null, null, t0), new Side("orange", null, null, t0), 9000);
        assertThat(r.total()).isEqualTo(100.0);
    }
}