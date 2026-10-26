package miragelab;

/** Pure combat policy: distance must NEVER override an active target in an HP mode
 * while actual HP is unavailable. Target loss/death and explicit user choices are handled
 * by the engine separately. Fail-closed for questionable health observations. */
final class CombatFocusPolicy {
    private CombatFocusPolicy() { }
    static boolean permitSwitch(boolean enabled, boolean hpMode, boolean currentKnown,
                                boolean candidateKnown, Double currentValue, Double candidateValue,
                                Double currentPercent, long currentAgeMs, long candidateAgeMs,
                                int minGain, int finishThreshold) {
        if (!enabled) return true;
        if (!hpMode) return true;
        if (!currentKnown || !candidateKnown) return false;
        if (currentValue == null || candidateValue == null
                || !Double.isFinite(currentValue) || !Double.isFinite(candidateValue)
                || currentValue <= 0 || candidateValue <= 0) return false;
        if (currentAgeMs > 700 || candidateAgeMs > 700) return false;
        // Already nearly finished: complete the kill; avoid consuming the final hit on another target.
        if (currentPercent != null && Double.isFinite(currentPercent) && currentPercent <= finishThreshold)
            return false;
        return currentValue - candidateValue >= Math.max(8, minGain);
    }

    /** A single missing render/packet must not erase a live target. */
    static boolean keepMissing(long now, long lastSeen, int graceMs, boolean stillLocallySelected) {
        return stillLocallySelected && lastSeen > 0 && now >= lastSeen
                && now - lastSeen <= Math.max(0, graceMs);
    }
}
