package miragelab;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

/** Periodic mana-training hotkey, similar to timed rune/spell training. */
final class ManaTrainingEngine {
    private static final Path EVENTS = Path.of(System.getProperty("user.home"), "Downloads", "MirageLab", "runtime", "helper_events.txt");
    private static long nextTickAt;
    private static long lastUseAt;
    private static String summary = "Mana Training: aguardando";
    private static int uses;

    private ManaTrainingEngine() {}

    static void tick(Object game, long now) {
        if (now < nextTickAt) return;
        nextTickAt = now + 100L;
        HelperConfig.Config c = HelperConfig.get(now);
        if (!c.manaTrainingEnabled) {
            summary = "Mana Training: OFF";
            return;
        }
        if (!c.manaTrainingInCombat && CombatContext.isEngaged()) {
            summary = "Mana Training: pausado durante combate";
            return;
        }
        long intervalMs = ManaTrainingPolicy.intervalMs(c.manaTrainingIntervalSec);
        long remaining = lastUseAt == 0L ? 0L : Math.max(0L, intervalMs - (now - lastUseAt));
        if (remaining > 0L) {
            summary = "Mana Training: ON | próxima em " + ((remaining + 999L) / 1000L) + "s | " + c.manaTrainingHotkey;
            return;
        }
        if (InputArbiter.press(game, c.manaTrainingHotkey, "ManaTraining", now)) {
            lastUseAt = now;
            uses++;
            summary = "Mana Training: USOU " + c.manaTrainingHotkey + " | uses=" + uses;
            event("MANA TRAINING hotkey=" + c.manaTrainingHotkey + " intervalo=" + c.manaTrainingIntervalSec + "s");
        }
    }

    static String summary() { return summary; }
    static void resetForTests() { nextTickAt = 0L; lastUseAt = 0L; uses = 0; }

    private static void event(String s) {
        try {
            Files.createDirectories(EVENTS.getParent());
            Files.writeString(EVENTS, LocalDateTime.now() + "  " + s + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) { }
    }
}
