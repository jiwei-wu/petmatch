package com.petmatch.evaluation;

import com.petmatch.evaluation.MatchingSimulator.Config;
import com.petmatch.evaluation.MatchingSimulator.Metrics;
import com.petmatch.matching.ScoringProperties;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks whether the scoring weights hold up across several simulated worlds, using seeds
 * that were NOT used while exploring (held-out seeds). The goal is robustness, not the single
 * highest number in one world: picking the best score in the world that generated the data
 * would only tune the model to my own assumptions.
 */
class WeightRobustnessTest {

    private static final long[] EXPLORE_SEEDS = {1, 2, 3, 4, 5};
    private static final long[] REPORT_SEEDS = {101, 102, 103, 104, 105};

    /** Each world changes only a few numbers of the default Config. */
    private static Map<String, Consumer<Config>> worlds() {
        Map<String, Consumer<Config>> w = new LinkedHashMap<>();
        w.put("baseline", c -> {});
        w.put("spread out", c -> {
            c.trueWithin500mShare = 0.40;
            c.trueMid500To3kmShare = 0.40;
        });
        w.put("sloppy witnesses", c -> {
            c.colourCorrect = 0.55;
            c.colourRelated = 0.20;
            c.sizeCorrect = 0.50;
            c.sizeAdjacent = 0.30;
            c.collarSeenIfWorn = 0.40;
        });
        w.put("crowded", c -> {
            c.nearDistractors = 30;
            c.farDistractors = 30;
        });
        return w;
    }

    /** Candidate weight sets: distance, time, colour, size, collar (each sums to 100). */
    private static Map<String, double[]> weightSets() {
        Map<String, double[]> m = new LinkedHashMap<>();
        m.put("current 40/20/20/10/10", new double[]{40, 20, 20, 10, 10});
        m.put("equal 20 each", new double[]{20, 20, 20, 20, 20});
        m.put("30/20/30/10/10", new double[]{30, 20, 30, 10, 10});
        m.put("25/25/30/10/10", new double[]{25, 25, 30, 10, 10});
        m.put("30/30/30/5/5", new double[]{30, 30, 30, 5, 5});
        m.put("20/30/40/5/5", new double[]{20, 30, 40, 5, 5});
        return m;
    }

    /** Mean MRR, top-5 and top-1 over the given seeds, in one world, for one weight set. */
    static double[] average(double[] w, Consumer<Config> world, long[] seeds) {
        double mrr = 0, top5 = 0, top1 = 0;
        for (long seed : seeds) {
            Config cfg = new Config();
            world.accept(cfg);
            cfg.seed = seed;
            ScoringProperties p = new ScoringProperties();
            p.setDistanceWeight(w[0]);
            p.setTimeWeight(w[1]);
            p.setColorWeight(w[2]);
            p.setSizeWeight(w[3]);
            p.setCollarWeight(w[4]);
            Metrics m = MatchingSimulator.evaluate(p, cfg);
            mrr += m.mrr();
            top5 += m.top5();
            top1 += m.top1();
        }
        return new double[]{mrr / seeds.length, top5 / seeds.length, top1 / seeds.length};
    }

    static String report(long[] seeds, String title) {
        StringBuilder sb = new StringBuilder();
        sb.append("--- ").append(title).append(" (MRR, top-5 %) ---\n");
        Map<String, Consumer<Config>> worlds = worlds();
        sb.append(String.format("%-24s", ""));
        for (String w : worlds.keySet()) sb.append(String.format("%-19s", w));
        sb.append(String.format("%-10s%-10s%n", "mean MRR", "worst MRR"));
        for (Map.Entry<String, double[]> ws : weightSets().entrySet()) {
            sb.append(String.format("%-24s", ws.getKey()));
            List<Double> mrrs = new ArrayList<>();
            for (Consumer<Config> world : worlds.values()) {
                double[] r = average(ws.getValue(), world, seeds);
                mrrs.add(r[0]);
                sb.append(String.format("%.3f, %5.1f%%      ", r[0], r[1] * 100));
            }
            double mean = mrrs.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double worst = mrrs.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            sb.append(String.format("%.3f     %.3f%n", mean, worst));
        }
        return sb.toString();
    }

    @Test
    void printsRobustnessReport_acrossWorlds_onExploreAndHeldOutSeeds() {
        System.out.println();
        System.out.println("=== Weight robustness across simulated worlds ===");
        System.out.println(report(EXPLORE_SEEDS, "explore seeds 1-5"));
        System.out.println(report(REPORT_SEEDS, "held-out seeds 101-105"));

        // Guard rail: the original weights must stay clearly better than chance in every world
        for (Consumer<Config> world : worlds().values()) {
            double[] r = average(weightSets().get("current 40/20/20/10/10"), world, REPORT_SEEDS);
            assertThat(r[0]).isGreaterThan(0.4);
        }
    }
}