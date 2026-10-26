package miragelab;

final class SkillPolicy {
    private SkillPolicy() {}
    static boolean due(long now, long lastUseAt, int cooldownMs) {
        return lastUseAt == 0L || now - lastUseAt >= Math.max(250, cooldownMs);
    }
}
