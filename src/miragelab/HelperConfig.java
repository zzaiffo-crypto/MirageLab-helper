package miragelab;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Set;
import java.util.LinkedHashSet;

final class HelperConfig {
    enum AttackPriority {
        NEAREST("Mais perto"), FARTHEST("Mais longe"),
        LOWEST_HP_ABSOLUTE("Menor HP (valor)"), LOWEST_HP_PERCENT("Menor HP (%)");
        final String label;
        AttackPriority(String label){this.label=label;}
        String label(){return label;}
        static AttackPriority parse(String raw){
            if(raw==null)return NEAREST;
            String v=raw.trim().toUpperCase(Locale.ROOT);
            if("LOWEST_HP".equals(v))return LOWEST_HP_PERCENT;
            return AttackPriority.valueOf(v);
        }
    }

    private static final Path FILE = Path.of(System.getProperty("user.home"), "Downloads", "MirageLab", "state", "helper_config.txt");
    private static volatile Config cached = new Config();
    private static volatile long nextReadAt;

    private HelperConfig() {}

    static synchronized Config get(long now) {
        if (now >= nextReadAt) {
            nextReadAt = now + 350L;
            cached = read();
        }
        return cached;
    }

    static synchronized Config read() {
        Config c = new Config();
        try {
            Files.createDirectories(FILE.getParent());
            if (!Files.exists(FILE)) {
                write(c);
                return c;
            }
            for (String line : Files.readAllLines(FILE, StandardCharsets.UTF_8)) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#") || !t.contains("=")) continue;
                int eq = t.indexOf('=');
                String k = t.substring(0, eq).trim().toLowerCase(Locale.ROOT);
                String v = t.substring(eq + 1).trim();
                try {
                    switch (k) {
                        case "auto_attack" -> c.autoAttackEnabled = bool(v, c.autoAttackEnabled);
                        case "attack_range" -> c.attackRange = clamp(Integer.parseInt(v), 1, 30);
                        case "attack_scan_interval_ms" -> c.attackScanIntervalMs = clamp(Integer.parseInt(v), 15, 500);
                        case "attack_priority" -> c.attackPriority = AttackPriority.parse(v);
                        case "attack_scan_radius" -> c.attackScanRadius = clamp(Integer.parseInt(v), 5, 40);
                        case "attack_chase" -> c.attackChaseEnabled = bool(v, c.attackChaseEnabled);
                        case "attack_chase_leash" -> c.attackChaseLeash = clamp(Integer.parseInt(v), 2, 10);
                        case "attack_visual_hp" -> c.attackVisualHpEnabled = bool(v,c.attackVisualHpEnabled);
                        case "attack_retarget" -> c.attackRetargetEnabled = bool(v, c.attackRetargetEnabled);
                        case "attack_focus_protection" -> c.attackFocusProtection = bool(v, c.attackFocusProtection);
                        case "attack_focus_min_gain" -> c.attackFocusMinGain = clamp(Integer.parseInt(v), 1, 100);
                        case "attack_target_missing_grace_ms" -> c.attackTargetMissingGraceMs = clamp(Integer.parseInt(v), 100, 2500);
                        case "attack_retarget_interval_ms" -> c.attackRetargetIntervalMs = clamp(Integer.parseInt(v), 20, 2000);
                        case "attack_unknown_hp_hold_ms" -> c.attackUnknownHpHoldMs = clamp(Integer.parseInt(v), 0, 10000);
                        case "attack_unknown_hp_distance_gain" -> c.attackUnknownHpDistanceGain = clamp(Integer.parseInt(v), 1, 10);
                        case "attack_switch_cooldown_ms" -> c.attackSwitchCooldownMs = clamp(Integer.parseInt(v), 0, 5000);
                        case "attack_ignore_names" -> c.attackIgnoreNames = v;
                        case "attack_switch_distance_gain" -> c.attackSwitchDistanceGain = clamp(Integer.parseInt(v), 0, 10);
                        case "attack_switch_hp_gain" -> c.attackSwitchHpGain = clamp(Integer.parseInt(v), 0, 100);
                        case "manual_target_lock_ms" -> c.manualTargetLockMs = clamp(Integer.parseInt(v), 0, 10000);
                        case "prefer_aggressor" -> c.preferAggressor = bool(v, c.preferAggressor);
                        case "attack_hp_memory_ms" -> c.attackHpMemoryMs = clamp(Integer.parseInt(v), 500, 30000);
                        case "attack_finish_wounded" -> c.attackFinishWounded = bool(v, c.attackFinishWounded);
                        case "attack_finish_threshold" -> c.attackFinishThreshold = clamp(Integer.parseInt(v), 1, 95);
                        case "attack_decision_confirm_ms" -> c.attackDecisionConfirmMs = clamp(Integer.parseInt(v), 0, 1000);
                        case "attack_claim_confirm_ms" -> c.attackClaimConfirmMs = clamp(Integer.parseInt(v), 0, 3000);
                        case "attack_claim_release_ms" -> c.attackClaimReleaseMs = clamp(Integer.parseInt(v), 100, 10000);
                        case "attack_self_defense_override" -> c.attackSelfDefenseOverride = bool(v, c.attackSelfDefenseOverride);
                        case "attack_anti_ks" -> c.attackAntiKsEnabled = bool(v, c.attackAntiKsEnabled);
                        case "party_auto_detect" -> c.partyAutoDetect = bool(v, c.partyAutoDetect);
                        case "party_members" -> c.partyMembers = v;
                        case "auto_heal" -> c.autoHealEnabled = bool(v, c.autoHealEnabled);
                        case "hp_heal_enabled" -> c.hpHealEnabled = bool(v,c.hpHealEnabled);
                        case "mana_heal_enabled" -> c.manaHealEnabled = bool(v,c.manaHealEnabled);
                        case "hp_threshold" -> c.hpThreshold = clamp(Integer.parseInt(v), 1, 99);
                        case "hp_hotkey" -> c.hpHotkey = v;
                        case "mana_threshold" -> c.manaThreshold = clamp(Integer.parseInt(v), 1, 99);
                        case "mana_hotkey" -> c.manaHotkey = v;
                        case "heal_cooldown_ms" -> c.healCooldownMs = clamp(Integer.parseInt(v), 2500, 60000);
                        case "auto_skills" -> c.autoSkillsEnabled = bool(v, c.autoSkillsEnabled);
                        case "skill_1_enabled" -> c.skill1Enabled = bool(v, c.skill1Enabled);
                        case "skill_1_hotkey" -> c.skill1Hotkey = v;
                        case "skill_1_cooldown_ms" -> c.skill1CooldownMs = clamp(Integer.parseInt(v), 250, 120000);
                        case "skill_2_enabled" -> c.skill2Enabled = bool(v, c.skill2Enabled);
                        case "skill_2_hotkey" -> c.skill2Hotkey = v;
                        case "skill_2_cooldown_ms" -> c.skill2CooldownMs = clamp(Integer.parseInt(v), 250, 120000);
                        case "skill_3_enabled" -> c.skill3Enabled = bool(v, c.skill3Enabled);
                        case "skill_3_hotkey" -> c.skill3Hotkey = v;
                        case "skill_3_cooldown_ms" -> c.skill3CooldownMs = clamp(Integer.parseInt(v), 250, 120000);
                        case "skill_global_gap_ms" -> c.skillGlobalGapMs = clamp(Integer.parseInt(v), 80, 5000);
                        case "mana_training" -> c.manaTrainingEnabled = bool(v, c.manaTrainingEnabled);
                        case "mana_training_hotkey" -> c.manaTrainingHotkey = v;
                        case "mana_training_interval_sec" -> c.manaTrainingIntervalSec = clamp(Integer.parseInt(v), 5, 3600);
                        case "mana_training_in_combat" -> c.manaTrainingInCombat = bool(v, c.manaTrainingInCombat);
                        case "input_global_gap_ms" -> c.inputGlobalGapMs = clamp(Integer.parseInt(v), 40, 1000);
                        case "input_pulse_ms" -> c.inputPulseMs = clamp(Integer.parseInt(v), 20, 250);
                        case "overlay_enabled" -> c.overlayEnabled = bool(v, c.overlayEnabled);
                        case "overlay_collapsed" -> c.overlayCollapsed = bool(v, c.overlayCollapsed);
                        case "overlay_x" -> c.overlayX = clamp(Integer.parseInt(v), -10000, 10000);
                        case "overlay_y" -> c.overlayY = clamp(Integer.parseInt(v), -10000, 10000);
                    }
                } catch (Exception ignored) { }
            }
        } catch (IOException ignored) { }
        return c;
    }

    static synchronized void write(Config c) throws IOException {
        Files.createDirectories(FILE.getParent());
        String text = "# MirageLab Helper v10.0\n"
                + "auto_attack=" + c.autoAttackEnabled + "\n"
                + "attack_range=" + c.attackRange + "\n"
                + "attack_scan_interval_ms=" + c.attackScanIntervalMs + "\n"
                + "attack_priority=" + c.attackPriority + "\n"
                + "attack_visual_hp=" + c.attackVisualHpEnabled + "\n"
                + "attack_scan_radius=" + c.attackScanRadius + "\n"
                + "attack_chase=" + c.attackChaseEnabled + "\n"
                + "attack_chase_leash=" + c.attackChaseLeash + "\n"
                + "attack_retarget=" + c.attackRetargetEnabled + "\n"
                + "attack_focus_protection=" + c.attackFocusProtection + "\n"
                + "attack_focus_min_gain=" + c.attackFocusMinGain + "\n"
                + "attack_target_missing_grace_ms=" + c.attackTargetMissingGraceMs + "\n"
                + "attack_retarget_interval_ms=" + c.attackRetargetIntervalMs + "\n"
                + "attack_unknown_hp_hold_ms=" + c.attackUnknownHpHoldMs + "\n"
                + "attack_unknown_hp_distance_gain=" + c.attackUnknownHpDistanceGain + "\n"
                + "attack_switch_cooldown_ms=" + c.attackSwitchCooldownMs + "\n"
                + "attack_ignore_names=" + c.attackIgnoreNames.replace("\n", " ").replace("\r", " ") + "\n"
                + "attack_switch_distance_gain=" + c.attackSwitchDistanceGain + "\n"
                + "attack_switch_hp_gain=" + c.attackSwitchHpGain + "\n"
                + "manual_target_lock_ms=" + c.manualTargetLockMs + "\n"
                + "prefer_aggressor=" + c.preferAggressor + "\n"
                + "attack_hp_memory_ms=" + c.attackHpMemoryMs + "\n"
                + "attack_finish_wounded=" + c.attackFinishWounded + "\n"
                + "attack_finish_threshold=" + c.attackFinishThreshold + "\n"
                + "attack_decision_confirm_ms=" + c.attackDecisionConfirmMs + "\n"
                + "attack_claim_confirm_ms=" + c.attackClaimConfirmMs + "\n"
                + "attack_claim_release_ms=" + c.attackClaimReleaseMs + "\n"
                + "attack_self_defense_override=" + c.attackSelfDefenseOverride + "\n"
                + "attack_anti_ks=" + c.attackAntiKsEnabled + "\n"
                + "party_auto_detect=" + c.partyAutoDetect + "\n"
                + "party_members=" + c.partyMembers + "\n"
                + "auto_heal=" + c.autoHealEnabled + "\n"
                + "hp_heal_enabled=" + c.hpHealEnabled + "\n"
                + "mana_heal_enabled=" + c.manaHealEnabled + "\n"
                + "hp_threshold=" + c.hpThreshold + "\n"
                + "hp_hotkey=" + c.hpHotkey + "\n"
                + "mana_threshold=" + c.manaThreshold + "\n"
                + "mana_hotkey=" + c.manaHotkey + "\n"
                + "heal_cooldown_ms=" + c.healCooldownMs + "\n"
                + "auto_skills=" + c.autoSkillsEnabled + "\n"
                + "skill_1_enabled=" + c.skill1Enabled + "\n"
                + "skill_1_hotkey=" + c.skill1Hotkey + "\n"
                + "skill_1_cooldown_ms=" + c.skill1CooldownMs + "\n"
                + "skill_2_enabled=" + c.skill2Enabled + "\n"
                + "skill_2_hotkey=" + c.skill2Hotkey + "\n"
                + "skill_2_cooldown_ms=" + c.skill2CooldownMs + "\n"
                + "skill_3_enabled=" + c.skill3Enabled + "\n"
                + "skill_3_hotkey=" + c.skill3Hotkey + "\n"
                + "skill_3_cooldown_ms=" + c.skill3CooldownMs + "\n"
                + "skill_global_gap_ms=" + c.skillGlobalGapMs + "\n"
                + "mana_training=" + c.manaTrainingEnabled + "\n"
                + "mana_training_hotkey=" + c.manaTrainingHotkey + "\n"
                + "mana_training_interval_sec=" + c.manaTrainingIntervalSec + "\n"
                + "mana_training_in_combat=" + c.manaTrainingInCombat + "\n"
                + "input_global_gap_ms=" + c.inputGlobalGapMs + "\n"
                + "input_pulse_ms=" + c.inputPulseMs + "\n"
                + "overlay_enabled=" + c.overlayEnabled + "\n"
                + "overlay_collapsed=" + c.overlayCollapsed + "\n"
                + "overlay_x=" + c.overlayX + "\n"
                + "overlay_y=" + c.overlayY + "\n";
        String previous = Files.isRegularFile(FILE) ? Files.readString(FILE, StandardCharsets.UTF_8) : "";
        SafeStateStore.write(FILE, SafeStateStore.preserveUnknown(previous, text));
        cached = c.copy();
        nextReadAt = Long.MAX_VALUE - 1;
    }

    static synchronized void update(java.util.function.Consumer<Config> mutator) throws IOException {
        Config c = cached.copy();
        // If nothing has been loaded yet, preserve on-disk values.
        if (nextReadAt == 0L) c = read();
        mutator.accept(c);
        write(c);
    }

    static synchronized void ensureCurrentFile() {
        try {
            String raw = Files.exists(FILE) ? Files.readString(FILE, StandardCharsets.UTF_8) : "";
            Config c = read();
            // v8.4 migration: preserve explicit user tuning, but upgrade old defaults.
            if (raw.contains("# MirageLab Helper v8.1")) {
                if (c.attackRetargetIntervalMs == 120) c.attackRetargetIntervalMs = 40;
                if (c.attackSwitchHpGain == 8) c.attackSwitchHpGain = 1;
                if (!raw.toLowerCase(Locale.ROOT).contains("attack_scan_interval_ms=")) c.attackScanIntervalMs = 20;
            }
            if (raw.contains("# MirageLab Helper v8.2")) {
                if (c.attackScanIntervalMs == 25) c.attackScanIntervalMs = 20;
                if (c.attackRetargetIntervalMs == 50) c.attackRetargetIntervalMs = 40;
            }
            if (raw.contains("# MirageLab Helper v8.3")) {
                if (c.attackRetargetIntervalMs == 40) c.attackRetargetIntervalMs = 25;
                if (c.attackDecisionConfirmMs == 60) c.attackDecisionConfirmMs = 25;
                if (c.attackScanIntervalMs >= 20) c.attackScanIntervalMs = 16;
                if (c.attackSelfDefenseOverride) c.attackSelfDefenseOverride = false;
            }
            // v9.1 false-positive regression: Actor x/y was accepted as monster health.
            // Disable visual mode once on migration; user can explicitly re-enable later.
            if (raw.contains("# MirageLab Helper v9.1")) c.attackVisualHpEnabled = false;
            // Migration to the direct-data architecture: visual HP produced false positives.
            // Keep the feature code for offline regression, not for runtime target selection.
            if (!raw.contains("# MirageLab Helper v9.6") && !raw.contains("# MirageLab Helper v9.7") && !raw.contains("# MirageLab Helper v10.0")) c.attackVisualHpEnabled = false;
            // The v8.4 overlay could remain hidden because an older saved setting
            // survived the updater. Restore it ONCE when migrating, without
            // overriding choices explicitly saved in v8.5 or v8.7.
            if (!raw.contains("# MirageLab Helper v8.5") && !raw.contains("# MirageLab Helper v8.6") && !raw.contains("# MirageLab Helper v8.7") && !raw.contains("# MirageLab Helper v9.1") && !raw.contains("# MirageLab Helper v9.2") && !raw.contains("# MirageLab Helper v9.3") && !raw.contains("# MirageLab Helper v9.4") && !raw.contains("# MirageLab Helper v9.6") && !raw.contains("# MirageLab Helper v9.7") && !raw.contains("# MirageLab Helper v10.0")) {
                c.overlayEnabled = true;
                if (c.attackHpMemoryMs == 7000) c.attackHpMemoryMs = 2500;
            }
            write(c);
        } catch (Exception ignored) { }
    }

    static synchronized void invalidate() { nextReadAt = 0L; }
    static Path file() { return FILE; }

    private static boolean bool(String s, boolean fallback) {
        String v = s.toLowerCase(Locale.ROOT);
        if (v.equals("true") || v.equals("1") || v.equals("on") || v.equals("sim") || v.equals("yes")) return true;
        if (v.equals("false") || v.equals("0") || v.equals("off") || v.equals("nao") || v.equals("não") || v.equals("no")) return false;
        return fallback;
    }
    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }


    static Set<String> normalizedPartyMembers(Config c) {
        Set<String> out = new LinkedHashSet<>();
        if (c == null || c.partyMembers == null || c.partyMembers.isBlank()) return out;
        for (String raw : c.partyMembers.split("[,;\n]")) {
            String n = raw.trim().toLowerCase(Locale.ROOT);
            if (!n.isBlank()) out.add(n);
        }
        return out;
    }

    static final class Config {
        boolean autoAttackEnabled = true;
        int attackRange = 7;
        int attackScanIntervalMs = 16;
        AttackPriority attackPriority = AttackPriority.NEAREST;
        boolean attackVisualHpEnabled = false;
        int attackScanRadius = 10;
        boolean attackChaseEnabled = false;
        int attackChaseLeash = 5;
        boolean attackRetargetEnabled = true;
        boolean attackFocusProtection = true;
        int attackFocusMinGain = 8;
        int attackTargetMissingGraceMs = 500;
        int attackRetargetIntervalMs = 25;
        int attackUnknownHpHoldMs = 1800;
        int attackUnknownHpDistanceGain = 2;
        int attackSwitchCooldownMs = 650;
        String attackIgnoreNames = "";
        int attackSwitchDistanceGain = 1;
        int attackSwitchHpGain = 1;
        int manualTargetLockMs = 1200;
        boolean preferAggressor = true;
        int attackHpMemoryMs = 2500;
        boolean attackFinishWounded = true;
        int attackFinishThreshold = 35;
        int attackDecisionConfirmMs = 25;
        int attackClaimConfirmMs = 120;
        int attackClaimReleaseMs = 700;
        boolean attackSelfDefenseOverride = false;
        boolean attackAntiKsEnabled = true;
        boolean partyAutoDetect = true;
        String partyMembers = "";

        boolean autoHealEnabled = false;
        boolean hpHealEnabled = true;
        boolean manaHealEnabled = false;
        int hpThreshold = 50;
        String hpHotkey = "1";
        int manaThreshold = 35;
        String manaHotkey = "3";
        int healCooldownMs = 3500;

        boolean autoSkillsEnabled = false;
        boolean skill1Enabled = true;
        String skill1Hotkey = "4";
        int skill1CooldownMs = 4000;
        boolean skill2Enabled = false;
        String skill2Hotkey = "5";
        int skill2CooldownMs = 7000;
        boolean skill3Enabled = false;
        String skill3Hotkey = "6";
        int skill3CooldownMs = 12000;
        int skillGlobalGapMs = 220;

        boolean manaTrainingEnabled = false;
        String manaTrainingHotkey = "7";
        int manaTrainingIntervalSec = 60;
        boolean manaTrainingInCombat = true;

        int inputGlobalGapMs = 90;
        int inputPulseMs = 55;
        boolean overlayEnabled = true;
        boolean overlayCollapsed = false;
        int overlayX = -1;
        int overlayY = -1;

        Config copy() {
            Config x = new Config();
            x.autoAttackEnabled = autoAttackEnabled; x.attackRange = attackRange; x.attackScanIntervalMs = attackScanIntervalMs; x.attackPriority = attackPriority; x.attackVisualHpEnabled = attackVisualHpEnabled; x.attackScanRadius = attackScanRadius; x.attackChaseEnabled = attackChaseEnabled; x.attackChaseLeash = attackChaseLeash;
            x.attackRetargetEnabled = attackRetargetEnabled; x.attackRetargetIntervalMs = attackRetargetIntervalMs;
            x.attackFocusProtection = attackFocusProtection; x.attackFocusMinGain = attackFocusMinGain; x.attackTargetMissingGraceMs = attackTargetMissingGraceMs;
            x.attackUnknownHpHoldMs = attackUnknownHpHoldMs; x.attackUnknownHpDistanceGain = attackUnknownHpDistanceGain;
            x.attackSwitchCooldownMs = attackSwitchCooldownMs; x.attackIgnoreNames = attackIgnoreNames;
            x.attackSwitchDistanceGain = attackSwitchDistanceGain; x.attackSwitchHpGain = attackSwitchHpGain;
            x.manualTargetLockMs = manualTargetLockMs; x.preferAggressor = preferAggressor;
            x.attackHpMemoryMs = attackHpMemoryMs; x.attackFinishWounded = attackFinishWounded;
            x.attackFinishThreshold = attackFinishThreshold; x.attackDecisionConfirmMs = attackDecisionConfirmMs;
            x.attackClaimConfirmMs = attackClaimConfirmMs; x.attackClaimReleaseMs = attackClaimReleaseMs;
            x.attackSelfDefenseOverride = attackSelfDefenseOverride; x.attackAntiKsEnabled = attackAntiKsEnabled;
            x.partyAutoDetect = partyAutoDetect; x.partyMembers = partyMembers;
            x.autoHealEnabled = autoHealEnabled; x.hpHealEnabled=hpHealEnabled; x.manaHealEnabled=manaHealEnabled; x.hpThreshold = hpThreshold; x.hpHotkey = hpHotkey;
            x.manaThreshold = manaThreshold; x.manaHotkey = manaHotkey; x.healCooldownMs = healCooldownMs;
            x.autoSkillsEnabled = autoSkillsEnabled;
            x.skill1Enabled = skill1Enabled; x.skill1Hotkey = skill1Hotkey; x.skill1CooldownMs = skill1CooldownMs;
            x.skill2Enabled = skill2Enabled; x.skill2Hotkey = skill2Hotkey; x.skill2CooldownMs = skill2CooldownMs;
            x.skill3Enabled = skill3Enabled; x.skill3Hotkey = skill3Hotkey; x.skill3CooldownMs = skill3CooldownMs;
            x.skillGlobalGapMs = skillGlobalGapMs;
            x.manaTrainingEnabled = manaTrainingEnabled; x.manaTrainingHotkey = manaTrainingHotkey;
            x.manaTrainingIntervalSec = manaTrainingIntervalSec; x.manaTrainingInCombat = manaTrainingInCombat;
            x.inputGlobalGapMs = inputGlobalGapMs; x.inputPulseMs = inputPulseMs;
            x.overlayEnabled = overlayEnabled; x.overlayCollapsed = overlayCollapsed; x.overlayX = overlayX; x.overlayY = overlayY;
            return x;
        }
    }
}
