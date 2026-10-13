package com.petmatch.evaluation;

import com.petmatch.matching.MatchScorer;
import com.petmatch.matching.MatchScorer.Side;
import com.petmatch.matching.ScoringProperties;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Generates synthetic lost/sighting data and measures how well MatchScorer ranks the TRUE
 * sighting among look-alike distractors. Pure Java: no Spring, no database.
 *
 * IMPORTANT: the data is generated from assumptions (see Config). It checks that the pipeline
 * is robust to noisy reports; it cannot prove real-world accuracy.
 */
public class MatchingSimulator {

    /** Everything that shapes the simulated world. Change a number here to test another scenario. */
    public static class Config {
        public int lostCats = 2000;
        public int nearDistractors = 10;       // other sightings within nearRadiusMeters
        public int farDistractors = 20;        // other sightings anywhere within 10 km
        public double nearRadiusMeters = 1500;
        public double searchRadiusMeters = 10_000;
        public int windowDays = 30;
        public long seed = 42;

        // How the TRUE sighting is generated (distance from where the cat was last seen)
        public double trueWithin500mShare = 0.70;   // exponential, mean 200 m
        public double trueMid500To3kmShare = 0.25;  // uniform 500 m .. 3 km (rest: 3 .. 10 km)
        public double trueDelayMeanDays = 7;        // exponential, truncated at windowDays

        // Observation noise on the TRUE sighting
        public double colourCorrect = 0.75, colourRelated = 0.15;  // rest: not noticed (null)
        public double sizeCorrect = 0.70, sizeAdjacent = 0.25;     // rest: not noticed (null)
        public double collarSeenIfWorn = 0.60, collarFalseAlarm = 0.05, collarUnknown = 0.10;
    }

    public record Metrics(int trials, double top1, double top3, double top5, double mrr, double avgCandidates) {}

    private static final String[] COLOURS = {"black", "tabby", "white", "orange", "grey", "tortoiseshell", "calico", "cream", "brown"};
    private static final double[] COLOUR_P = {0.25, 0.25, 0.10, 0.12, 0.10, 0.06, 0.05, 0.04, 0.03};
    private static final String[] SIZES = {"small", "medium", "large"};
    private static final double[] SIZE_P = {0.20, 0.60, 0.20};
    private static final double COLLAR_P = 0.45;

    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC);

    private record Cat(String colour, String size, boolean collar) {}

    public static Metrics evaluate(ScoringProperties props, Config cfg) {
        MatchScorer scorer = new MatchScorer(props);
        Random rnd = new Random(cfg.seed);

        int hit1 = 0, hit3 = 0, hit5 = 0;
        double mrrSum = 0;
        long candidateTotal = 0;

        for (int i = 0; i < cfg.lostCats; i++) {
            Cat cat = randomCat(rnd);
            // The owner reports the cat accurately (they know their own pet)
            Side lost = new Side(cat.colour(), cat.size(), cat.collar(), T0);

            // True sighting: same cat, noisy observation, distance and delay from the loss
            double trueDist = trueDistance(rnd, cfg);
            double trueDelay = Math.min(cfg.windowDays, -cfg.trueDelayMeanDays * Math.log(1 - rnd.nextDouble()));
            Side trueSide = observe(cat, T0.plusMinutes((long) (trueDelay * 1440)), rnd, cfg, props);
            double trueScore = scorer.score(lost, trueSide, trueDist).total();

            // Distractors: other cats seen around the same time and area
            List<Double> otherScores = new ArrayList<>();
            for (int d = 0; d < cfg.nearDistractors + cfg.farDistractors; d++) {
                boolean near = d < cfg.nearDistractors;
                double dist = discDistance(rnd, near ? cfg.nearRadiusMeters : cfg.searchRadiusMeters);
                double delay = rnd.nextDouble() * cfg.windowDays;
                Cat other = randomCat(rnd);
                Side seen = observe(other, T0.plusMinutes((long) (delay * 1440)), rnd, cfg, props);
                otherScores.add(scorer.score(lost, seen, dist).total());
            }

            // Pessimistic ranking: a distractor with an equal score counts as ranked above the truth
            int rank = 1;
            for (double s : otherScores) {
                if (s >= trueScore) rank++;
            }
            if (rank <= 1) hit1++;
            if (rank <= 3) hit3++;
            if (rank <= 5) hit5++;
            mrrSum += 1.0 / rank;
            candidateTotal += otherScores.size() + 1;
        }

        int n = cfg.lostCats;
        return new Metrics(n, hit1 / (double) n, hit3 / (double) n, hit5 / (double) n,
                mrrSum / n, candidateTotal / (double) n);
    }

    /**
     * Naive baseline: rank candidates only by distance, nearest first (what a simple
     * "show me the closest sighting" feature would do). Ties are not an issue here.
     */
    public static Metrics evaluateNearestFirstBaseline(Config cfg) {
        Random rnd = new Random(cfg.seed);
        int hit1 = 0, hit3 = 0, hit5 = 0;
        double mrrSum = 0;
        for (int i = 0; i < cfg.lostCats; i++) {
            double trueDist = trueDistance(rnd, cfg);
            int rank = 1;
            for (int d = 0; d < cfg.nearDistractors + cfg.farDistractors; d++) {
                boolean near = d < cfg.nearDistractors;
                double dist = discDistance(rnd, near ? cfg.nearRadiusMeters : cfg.searchRadiusMeters);
                if (dist <= trueDist) rank++;
            }
            if (rank <= 1) hit1++;
            if (rank <= 3) hit3++;
            if (rank <= 5) hit5++;
            mrrSum += 1.0 / rank;
        }
        int n = cfg.lostCats;
        return new Metrics(n, hit1 / (double) n, hit3 / (double) n, hit5 / (double) n,
                mrrSum / n, cfg.nearDistractors + cfg.farDistractors + 1);
    }

    // ---------- world model ----------

    private static Cat randomCat(Random rnd) {
        return new Cat(pick(COLOURS, COLOUR_P, rnd), pick(SIZES, SIZE_P, rnd), rnd.nextDouble() < COLLAR_P);
    }

    /** Another person's report of a cat they saw: colour, size and collar may be wrong or missing. */
    private static Side observe(Cat cat, OffsetDateTime when, Random rnd, Config cfg, ScoringProperties props) {
        String colour;
        double c = rnd.nextDouble();
        if (c < cfg.colourCorrect) {
            colour = cat.colour();
        } else if (c < cfg.colourCorrect + cfg.colourRelated) {
            colour = relatedColour(cat.colour(), props, rnd);
        } else {
            colour = null;
        }

        String size;
        double s = rnd.nextDouble();
        if (s < cfg.sizeCorrect) {
            size = cat.size();
        } else if (s < cfg.sizeCorrect + cfg.sizeAdjacent) {
            size = adjacentSize(cat.size(), rnd);
        } else {
            size = null;
        }

        Boolean collar;
        if (rnd.nextDouble() < cfg.collarUnknown) {
            collar = null;
        } else if (cat.collar()) {
            collar = rnd.nextDouble() < cfg.collarSeenIfWorn;
        } else {
            collar = rnd.nextDouble() < cfg.collarFalseAlarm;
        }
        return new Side(colour, size, collar, when);
    }

    private static String relatedColour(String colour, ScoringProperties props, Random rnd) {
        List<String> related = new ArrayList<>();
        for (Map.Entry<String, Double> e : props.getColorSimilarity().entrySet()) {
            String[] pair = e.getKey().split("\\|");
            if (pair[0].equals(colour)) related.add(pair[1]);
            else if (pair[1].equals(colour)) related.add(pair[0]);
        }
        return related.isEmpty() ? colour : related.get(rnd.nextInt(related.size()));
    }

    private static String adjacentSize(String size, Random rnd) {
        return switch (size) {
            case "small" -> "medium";
            case "large" -> "medium";
            default -> rnd.nextBoolean() ? "small" : "large";
        };
    }

    private static double trueDistance(Random rnd, Config cfg) {
        double u = rnd.nextDouble();
        if (u < cfg.trueWithin500mShare) {
            return Math.min(500, -200 * Math.log(1 - rnd.nextDouble()));
        }
        if (u < cfg.trueWithin500mShare + cfg.trueMid500To3kmShare) {
            return 500 + rnd.nextDouble() * 2500;
        }
        return 3000 + rnd.nextDouble() * (cfg.searchRadiusMeters - 3000);
    }

    /** Uniform over a disc, so most points are far from the centre (like real neighbourhoods). */
    private static double discDistance(Random rnd, double radius) {
        return radius * Math.sqrt(rnd.nextDouble());
    }

    private static String pick(String[] values, double[] probs, Random rnd) {
        double u = rnd.nextDouble();
        double acc = 0;
        for (int i = 0; i < values.length; i++) {
            acc += probs[i];
            if (u < acc) return values[i];
        }
        return values[values.length - 1];
    }
}