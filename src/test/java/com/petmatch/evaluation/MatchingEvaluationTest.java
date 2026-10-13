package com.petmatch.evaluation;

import com.petmatch.evaluation.MatchingSimulator.Config;
import com.petmatch.evaluation.MatchingSimulator.Metrics;
import com.petmatch.matching.ScoringProperties;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prints an evaluation report on simulated data, and guards against the scoring model
 * becoming worse than chance. Not a proof of real-world accuracy (see MatchingSimulator).
 */
class MatchingEvaluationTest {

    @Test
    void printsEvaluationReport_andModelBeatsChanceByAWideMargin() {
        Config cfg = new Config();
        Metrics full = MatchingSimulator.evaluate(new ScoringProperties(), cfg);

        double chanceTop1 = 1.0 / full.avgCandidates();
        double chanceTop5 = 5.0 / full.avgCandidates();

        System.out.println();
        System.out.println("=== PetMatch matching evaluation (simulated data) ===");
        System.out.printf("%d lost cats, about %.0f candidates each; differences under ~2 points are noise%n",
                full.trials(), full.avgCandidates());
        System.out.printf("%-26s top-1 %5.1f%%   top-3 %5.1f%%   top-5 %5.1f%%   MRR %.3f%n",
                "random guess", chanceTop1 * 100, 3 * chanceTop1 * 100, chanceTop5 * 100, 0.0);

        Metrics nearest = MatchingSimulator.evaluateNearestFirstBaseline(cfg);
        print("nearest sighting first", nearest);

        print("full model", full);
        row("  without distance", cfg, p -> p.setDistanceWeight(0));
        row("  without time", cfg, p -> p.setTimeWeight(0));
        row("  without colour", cfg, p -> p.setColorWeight(0));
        row("  without size", cfg, p -> p.setSizeWeight(0));
        row("  without collar", cfg, p -> p.setCollarWeight(0));
        row("  appearance only", cfg, p -> {
            p.setDistanceWeight(0);
            p.setTimeWeight(0);
        });
        row("  equal weights", cfg, p -> {
            p.setDistanceWeight(20);
            p.setTimeWeight(20);
            p.setColorWeight(20);
            p.setSizeWeight(20);
            p.setCollarWeight(20);
        });
        System.out.println();

        // Guard rails (far below the current numbers; they only catch a badly broken model)
        assertThat(full.top1()).isGreaterThan(chanceTop1 * 10);
        assertThat(full.top5()).isGreaterThan(chanceTop5 * 3);
        assertThat(full.mrr()).isGreaterThan(0.5);
    }

    private static void row(String name, Config cfg, Consumer<ScoringProperties> tweak) {
        ScoringProperties props = new ScoringProperties();
        tweak.accept(props);
        print(name, MatchingSimulator.evaluate(props, cfg));
    }

    private static void print(String name, Metrics m) {
        System.out.printf("%-26s top-1 %5.1f%%   top-3 %5.1f%%   top-5 %5.1f%%   MRR %.3f%n",
                name, m.top1() * 100, m.top3() * 100, m.top5() * 100, m.mrr());
    }
}