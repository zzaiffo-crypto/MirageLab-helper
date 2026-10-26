package miragelab;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigMigrationV641Test {
    public static void main(String[] args) throws Exception {
        Path lab = Path.of(System.getProperty("user.home"), "Downloads", "MirageLab");
        Path state = lab.resolve("state");
        Files.createDirectories(state);
        Files.writeString(state.resolve("cavebot_config.txt"),
                "# MirageLab CaveBot v6.4\n" +
                "combat_enabled=true\n" +
                "unreachable_timeout_ms=3500\n" +
                "unreachable_failed_steps=2\n" +
                "unreachable_blacklist_ms=120000\n" +
                "ranged_detection_range=7\n" +
                "aggro_detection_range=12\n" +
                "auto_loot=true\n" +
                "auto_food=true\n" +
                "anti_ks=true\n", StandardCharsets.UTF_8);
        Class<?> c = Class.forName("miragelab.CaveBotEngine");
        Method m = c.getDeclaredMethod("ensureFiles");
        m.setAccessible(true);
        m.invoke(null);
        String out = Files.readString(state.resolve("cavebot_config.txt"), StandardCharsets.UTF_8);
        require(out.contains("# MirageLab CaveBot v8.4"), "version-header");
        require(out.contains("unreachable_timeout_ms=8000"), "longer-progress-window");
        require(out.contains("unreachable_failed_steps=4"), "more-blocked-steps-required");
        require(out.contains("unreachable_blacklist_ms=15000"), "short-normal-blacklist");
        require(out.contains("hostile_unreachable_blacklist_ms=3000"), "short-hostile-blacklist");
        require(out.contains("auto_loot=true"), "preserve-autoloot");
        require(out.contains("auto_food=true"), "preserve-autofood");
        require(out.contains("anti_ks=true"), "preserve-antiks");
        require(out.contains("fast_player_poll_ms=12"), "fast-player-poll");
        require(out.contains("route_burst_size=2"), "route-burst");
        System.out.println("ALL PASS config-migration-v6.4-to-v8.4");
    }
    static void require(boolean ok, String name) {
        if (!ok) throw new AssertionError("FAIL " + name);
        System.out.println("PASS " + name);
    }
}
