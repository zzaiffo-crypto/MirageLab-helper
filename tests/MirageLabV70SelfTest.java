package miragelab;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class MirageLabV70SelfTest {
    public static void main(String[] args) throws Exception {
        int passed = 0;

        require(TargetPriority.choose(HelperConfig.AttackPriority.NEAREST,
                List.of(new TargetPriority.Candidate(1,5,80d,false), new TargetPriority.Candidate(2,2,90d,false))) == 2,
                "autoattack-nearest"); passed++;
        require(TargetPriority.choose(HelperConfig.AttackPriority.FARTHEST,
                List.of(new TargetPriority.Candidate(1,5,80d,false), new TargetPriority.Candidate(2,2,90d,false))) == 1,
                "autoattack-farthest"); passed++;
        require(TargetPriority.choose(HelperConfig.AttackPriority.LOWEST_HP_PERCENT,
                List.of(new TargetPriority.Candidate(1,5,70d,false), new TargetPriority.Candidate(2,2,25d,false), new TargetPriority.Candidate(3,1,null,false))) == 2,
                "autoattack-lowest-hp"); passed++;

        String hp = VitalSensorSupport.chooseDropKeyForTests(
                Map.of("C#hp",80d,"C#max",100d,"C#other",10d),
                Map.of("C#hp",55d,"C#max",100d,"C#other",10d));
        require("C#hp".equals(hp), "vital-sensor-drop-with-stable-max"); passed++;
        String pct = VitalSensorSupport.chooseDropKeyForTests(
                Map.of("P#life",90d,"P#x",20d), Map.of("P#life",65d,"P#x",20d));
        require("P#life".equals(pct), "vital-sensor-percent-fallback"); passed++;

        require(CaveBotEngine.shouldWaitLateArrival(false, 1200, 650, 1400), "movement-late-arrival-waits"); passed++;
        require(!CaveBotEngine.shouldWaitLateArrival(false, 2100, 650, 1400), "movement-hard-timeout-after-grace"); passed++;
        require(!CaveBotEngine.shouldWaitLateArrival(true, 800, 650, 1400), "movement-visible-obstacle-replans"); passed++;

        HelperConfig.Config cfg = new HelperConfig.Config();
        cfg.autoAttackEnabled = true; cfg.attackRange = 9; cfg.attackPriority = HelperConfig.AttackPriority.LOWEST_HP_PERCENT;
        cfg.autoHealEnabled = true; cfg.hpThreshold = 48; cfg.hpHotkey = "F2"; cfg.manaThreshold = 31; cfg.manaHotkey = "4"; cfg.healCooldownMs = 700;
        HelperConfig.write(cfg);
        HelperConfig.Config reread = HelperConfig.read();
        require(reread.autoAttackEnabled && reread.attackRange==9 && reread.attackPriority==HelperConfig.AttackPriority.LOWEST_HP_PERCENT,
                "helper-config-autoattack-roundtrip"); passed++;
        require(reread.autoHealEnabled && reread.hpThreshold==48 && "F2".equals(reread.hpHotkey) && reread.manaThreshold==31 && "4".equals(reread.manaHotkey),
                "helper-config-autoheal-roundtrip"); passed++;

        Path state = Path.of(System.getProperty("user.home"), "Downloads", "MirageLab", "state");
        Files.createDirectories(state);
        Files.writeString(state.resolve("cavebot_config.txt"),
                "# MirageLab CaveBot v6.4.1\ncombat_enabled=true\nnative_chase_grace_ms=350\nassist_move_cooldown_ms=180\nmove_timeout_ms=1000\nlate_arrival_grace_ms=500\nworld_poll_ms=30\nauto_loot=true\nauto_food=true\nanti_ks=true\n",
                StandardCharsets.UTF_8);
        Method ensure = CaveBotEngine.class.getDeclaredMethod("ensureFiles"); ensure.setAccessible(true); ensure.invoke(null);
        String migrated = Files.readString(state.resolve("cavebot_config.txt"), StandardCharsets.UTF_8);
        require(migrated.contains("# MirageLab CaveBot v8.4"), "config-migration-version"); passed++;
        require(migrated.contains("attack_priority=NEAREST"), "config-migration-priority"); passed++;
        require(migrated.contains("native_chase_grace_ms=250") && migrated.contains("assist_move_cooldown_ms=120")
                && migrated.contains("move_timeout_ms=650") && migrated.contains("late_arrival_grace_ms=1400")
                && migrated.contains("world_poll_ms=60"), "config-migration-smooth-values"); passed++;
        require(migrated.contains("auto_loot=true") && migrated.contains("auto_food=true") && migrated.contains("anti_ks=true"),
                "config-migration-preserves-cave-features"); passed++;

        System.out.println("ALL PASS " + passed + "/" + passed);
    }

    private static void require(boolean b, String name) {
        if (!b) throw new AssertionError("FAIL " + name);
        System.out.println("PASS " + name);
    }
}
