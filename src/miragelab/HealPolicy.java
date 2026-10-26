package miragelab;

final class HealPolicy {
    private HealPolicy() {}
    static boolean shouldUse(Double percent, int threshold, long elapsedMs, int cooldownMs) {
        return percent != null && Double.isFinite(percent) && percent >= 0.0 && percent <= 100.0
                && threshold >= 1 && threshold <= 99 && percent <= threshold
                && elapsedMs >= Math.max(150, cooldownMs);
    }
}
