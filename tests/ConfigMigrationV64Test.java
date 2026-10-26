package miragelab;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigMigrationV64Test {
    public static void main(String[] args) throws Exception {
        Path lab = Path.of(System.getProperty("user.home"), "Downloads", "MirageLab");
        Path state = lab.resolve("state");
        Files.createDirectories(state);
        Files.writeString(state.resolve("cavebot_config.txt"),
                "# MirageLab CaveBot v6.3.1\n" +
                "combat_enabled=true\n" +
                "detection_range=4\n" +
                "native_chase_grace_ms=700\n" +
                "assist_move_cooldown_ms=450\n" +
                "move_timeout_ms=1600\n" +
                "world_poll_ms=60\n" +
                "loot_open_delay_ms=400\n" +
                "loot_confirm_ms=500\n" +
                "loot_task_timeout_ms=20000\n" +
                "loot_max_attempts=2\n", StandardCharsets.UTF_8);
        Class<?> c = Class.forName("miragelab.CaveBotEngine");
        Method m = c.getDeclaredMethod("ensureFiles");
        m.setAccessible(true);
        m.invoke(null);
        String out = Files.readString(state.resolve("cavebot_config.txt"), StandardCharsets.UTF_8);
        require(out.contains("# MirageLab CaveBot v8.4"), "version header");
        require(out.contains("ranged_detection_range=7"), "ranged range");
        require(out.contains("aggro_detection_range=12"), "aggro range");
        require(out.contains("late_arrival_grace_ms=1400"), "late arrival");
        require(out.contains("anti_ks=true"), "anti ks");
        require(out.contains("auto_food=true"), "auto food");
        require(out.contains("auto_food_threshold=30"), "food threshold");
        require(out.contains("loot_open_delay_ms=700"), "loot open delay");
        require(out.contains("loot_confirm_ms=800"), "loot confirm");
        require(out.contains("loot_task_timeout_ms=45000"), "loot queue timeout");
        require(out.contains("loot_max_attempts=1"), "single loot attempt");
        require(out.contains("native_chase_grace_ms=250"), "smooth chase");
        require(out.contains("assist_move_cooldown_ms=120"), "smooth move cooldown");
        require(out.contains("move_timeout_ms=650"), "smooth timeout");
        require(out.contains("world_poll_ms=60"), "smooth world poll");
        require(out.contains("fast_player_poll_ms=12"), "fast player poll");
        require(out.contains("route_burst_size=2"), "route burst");
        System.out.println("ALL PASS config-migration-v6.3.1-to-v8.4");
    }
    static void require(boolean ok, String name) {
        if (!ok) throw new AssertionError("FAIL " + name);
        System.out.println("PASS " + name);
    }
}
