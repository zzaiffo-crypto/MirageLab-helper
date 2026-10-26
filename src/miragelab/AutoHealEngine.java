package miragelab;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

final class AutoHealEngine {
    private static final Path EVENTS = Path.of(System.getProperty("user.home"), "Downloads", "MirageLab", "runtime", "helper_events.txt");
    private static long nextTickAt;
    private static long lastHpUseAt, lastManaUseAt;
    private static final HealSafety hpGuard = new HealSafety(), manaGuard = new HealSafety();
    private static boolean wasEnabled;
    private static String summary = "Auto Heal: aguardando";
    private static String lastEvent;

    private AutoHealEngine() {}

    static void tick(Object game, long now) {
        if (now < nextTickAt) return;
        nextTickAt = now + 80L;
        try {
            // v9.6: only named ECS current/max pairs may authorize item use.
            // No screen capture and no manual drop-field calibration.
            GameDirectVitals.Player direct=GameDirectVitals.player(game,now);
            Double hudHp=direct.hp().valid()?direct.hp().percent():null;
            Double hudMana=direct.mana().valid()?direct.mana().percent():null;
            String source=direct.status();
            HelperConfig.Config c = HelperConfig.get(now);
            if (!c.autoHealEnabled) {
                wasEnabled=false; hpGuard.reset(); manaGuard.reset();
                summary = "Auto Heal: OFF | HP="+fmt(hudHp)+" MANA="+fmt(hudMana)+" | "+source;
                return;
            }
            if (!wasEnabled) { hpGuard.reset(); manaGuard.reset(); wasEnabled=true; }
            Double hp = hudHp;
            Double mana = hudMana;
            String action = "";
            hpGuard.observe(hp,now); manaGuard.observe(mana,now);
            if (c.hpHealEnabled && hpGuard.canUse(hp,c.hpThreshold,now,c.healCooldownMs)) {
                if (InputArbiter.press(game, c.hpHotkey, "AutoHealHP", now)) {
                    lastHpUseAt = now; hpGuard.used(hp,now);
                    action = " HP->" + c.hpHotkey;
                    event("AUTOHEAL HP - " + Math.round(hp) + "% <= " + c.hpThreshold + "% hotkey=" + c.hpHotkey);
                }
            } else if (c.manaHealEnabled && manaGuard.canUse(mana,c.manaThreshold,now,c.healCooldownMs)) {
                if (InputArbiter.press(game, c.manaHotkey, "AutoHealMana", now)) {
                    lastManaUseAt = now; manaGuard.used(mana,now);
                    action = " MANA->" + c.manaHotkey;
                    event("AUTOHEAL MANA - " + Math.round(mana) + "% <= " + c.manaThreshold + "% hotkey=" + c.manaHotkey);
                }
            }
            summary = "Auto Heal: ON | HP="+fmt(hp)+" ("+(c.hpHealEnabled?"limite "+c.hpThreshold+"% tecla "+c.hpHotkey:"OFF")+")"
                    + " | MP="+fmt(mana)+" ("+(c.manaHealEnabled?"limite "+c.manaThreshold+"% tecla "+c.manaHotkey:"OFF")+")"
                    + action + " | "+source+" | HP="+hpGuard.status()+" MP="+manaGuard.status();
        } catch (Throwable t) {
            summary = "Auto Heal: ERRO " + t.getClass().getSimpleName();
        }
    }

    static String summary() { return summary; }
    private static String fmt(Double d){return d==null?"?":Math.round(d)+"%";}

    private static void event(String s) {
        if (s == null || s.isBlank() || s.equals(lastEvent)) return;
        lastEvent = s;
        try {
            Files.createDirectories(EVENTS.getParent());
            Files.writeString(EVENTS, LocalDateTime.now() + "  " + s + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) { }
    }
}
