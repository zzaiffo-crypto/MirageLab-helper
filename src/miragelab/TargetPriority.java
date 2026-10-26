package miragelab;

import java.util.Comparator;
import java.util.List;

/** Deterministic target ranking. The selected criterion is always primary. */
final class TargetPriority {
    private TargetPriority() {}

    /** hpCurrent is absolute/current HP; hpPercent is normalized 0..100. */
    record Candidate(int id, int distance, Double hpCurrent, Double hpPercent, boolean hostile) {
        // Compatibility constructor for older regression tests that only knew HP percent.
        Candidate(int id, int distance, Double hpPercent, boolean hostile) {
            this(id, distance, null, hpPercent, hostile);
        }
    }

    static Integer choose(HelperConfig.AttackPriority p, List<Candidate> xs) {
        if (xs == null || xs.isEmpty()) return null;
        Comparator<Candidate> hostileTie = Comparator.comparingInt((Candidate c) -> c.hostile ? 0 : 1);
        Comparator<Candidate> percentTie = Comparator.comparingDouble(c -> validPercent(c.hpPercent) ? c.hpPercent : 101d);
        Comparator<Candidate> absoluteTie = Comparator.comparingDouble(c -> validAbsolute(c.hpCurrent) ? c.hpCurrent : Double.POSITIVE_INFINITY);
        Comparator<Candidate> cmp = switch (p) {
            case FARTHEST -> Comparator.comparingInt((Candidate c) -> c.distance).reversed()
                    .thenComparing(hostileTie).thenComparing(percentTie).thenComparingInt(c -> c.id);
            case LOWEST_HP_ABSOLUTE -> Comparator.comparingInt((Candidate c) -> validAbsolute(c.hpCurrent) ? 0 : 1)
                    .thenComparingDouble(c -> validAbsolute(c.hpCurrent) ? c.hpCurrent : Double.POSITIVE_INFINITY)
                    .thenComparing(hostileTie).thenComparing(percentTie).thenComparingInt(c -> c.distance).thenComparingInt(c -> c.id);
            case LOWEST_HP_PERCENT -> Comparator.comparingInt((Candidate c) -> validPercent(c.hpPercent) ? 0 : 1)
                    .thenComparingDouble(c -> validPercent(c.hpPercent) ? c.hpPercent : 101d)
                    .thenComparing(hostileTie).thenComparing(absoluteTie).thenComparingInt(c -> c.distance).thenComparingInt(c -> c.id);
            case NEAREST -> Comparator.comparingInt((Candidate c) -> c.distance)
                    .thenComparing(hostileTie).thenComparing(percentTie).thenComparingInt(c -> c.id);
        };
        return xs.stream().min(cmp).map(Candidate::id).orElse(null);
    }

    static boolean validAbsolute(Double hp) { return hp != null && Double.isFinite(hp) && hp > 0.0; }
    static boolean validPercent(Double hp) { return hp != null && Double.isFinite(hp) && hp > 0.1 && hp <= 100.0; }
}
