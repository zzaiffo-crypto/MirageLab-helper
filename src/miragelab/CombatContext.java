package miragelab;

/** Lightweight shared combat signal used by skills/training without coupling modules. */
final class CombatContext {
    private CombatContext() {}
    static boolean isEngaged() {
        return CaveBotEngine.isInCombat() || AutoAttackEngine.hasActiveTarget();
    }
    static String summary() {
        return "engaged=" + isEngaged() + " cave=" + CaveBotEngine.isInCombat() + " aa=" + AutoAttackEngine.hasActiveTarget();
    }
}
