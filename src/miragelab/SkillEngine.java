package miragelab;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

/** Combat skill rotation driven by configurable hotkeys/cooldowns. */
final class SkillEngine {
    private static final Path EVENTS = Path.of(System.getProperty("user.home"), "Downloads", "MirageLab", "runtime", "helper_events.txt");
    private static long nextTickAt;
    private static long lastGlobalAt;
    private static final long[] lastUse = new long[3];
    private static String summary = "Auto Skills: aguardando";

    private SkillEngine() {}

    static void tick(Object game, long now) {
        if (now < nextTickAt) return;
        nextTickAt = now + 40L;
        HelperConfig.Config c = HelperConfig.get(now);
        if (!c.autoSkillsEnabled) {
            summary = "Auto Skills: OFF";
            return;
        }
        if (!CombatContext.isEngaged()) {
            summary = "Auto Skills: ON | aguardando combate";
            return;
        }
        if (now - lastGlobalAt < c.skillGlobalGapMs) {
            summary = "Auto Skills: combate ativo | aguardando global gap";
            return;
        }
        Slot[] slots = {
                new Slot(0, c.skill1Enabled, c.skill1Hotkey, c.skill1CooldownMs),
                new Slot(1, c.skill2Enabled, c.skill2Hotkey, c.skill2CooldownMs),
                new Slot(2, c.skill3Enabled, c.skill3Hotkey, c.skill3CooldownMs)
        };
        for (Slot s : slots) {
            if (!s.enabled || !SkillPolicy.due(now, lastUse[s.index], s.cooldownMs)) continue;
            if (InputArbiter.press(game, s.hotkey, "Skill" + (s.index + 1), now)) {
                lastUse[s.index] = now;
                lastGlobalAt = now;
                summary = "Auto Skills: SKILL " + (s.index + 1) + " -> " + s.hotkey + " | combate ativo";
                event("AUTOSKILL " + (s.index + 1) + " hotkey=" + s.hotkey);
                return;
            }
        }
        summary = "Auto Skills: combate ativo | cooldowns";
    }

    static String summary() { return summary; }

    static void resetForTests() {
        nextTickAt = 0L; lastGlobalAt = 0L;
        for (int i = 0; i < lastUse.length; i++) lastUse[i] = 0L;
    }

    private static void event(String s) {
        try {
            Files.createDirectories(EVENTS.getParent());
            Files.writeString(EVENTS, LocalDateTime.now() + "  " + s + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) { }
    }

    private record Slot(int index, boolean enabled, String hotkey, int cooldownMs) { }
}
