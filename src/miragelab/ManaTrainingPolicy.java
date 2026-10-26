package miragelab;

final class ManaTrainingPolicy {
    private ManaTrainingPolicy() {}
    static long intervalMs(int seconds) { return Math.max(5, seconds) * 1000L; }
    static boolean due(long now, long lastUseAt, int seconds) {
        return lastUseAt == 0L || now - lastUseAt >= intervalMs(seconds);
    }
}
