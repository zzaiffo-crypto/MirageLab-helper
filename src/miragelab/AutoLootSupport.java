package miragelab;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;

/** Runtime-safe loot helpers for MirageLab v7.0. */
final class AutoLootSupport {
    static final String LOOT_TOKEN_COMPONENT_CLASS =
            "uk.co.harveydogs.mirage.shared.yY8x1yOsuoav.AHXnlQ8L2gWGxpmXW";
    private static final String ACTION_BASE_CLASS =
            "uk.co.harveydogs.mirage.shared.ppGE4fatBnKfcPyX4Kmtne6Lgsjv.NT6CLfh1EFZKAgt882tQ6RNRZLmx";
    private static final String LOOT_INFO_ACTION_CLASS =
            "uk.co.harveydogs.mirage.shared.ppGE4fatBnKfcPyX4Kmtne6Lgsjv.yY8x1yOsuoav.yY8x1yOsuoav.phIAQDKepCG9Flqa5.NT6CLfh1EFZKAgt882tQ6RNRZLmx";
    private static final String NETWORK_GETTER = "L3t5HInQtJbd0Zshf";
    private static final String FACTORY_OR_SEND = "eACqBMENic3YHQLgkQTnYXFlcXmJ";

    record Candidate(int key, int x, int y, String name, String type, Object token) { }

    private AutoLootSupport() { }

    static Candidate nearest(int corpseX, int corpseY, List<Candidate> candidates, int radius) {
        if (candidates == null || candidates.isEmpty()) return null;
        int r = Math.max(0, radius);
        return candidates.stream()
                .filter(c -> Math.max(Math.abs(c.x - corpseX), Math.abs(c.y - corpseY)) <= r)
                .min(Comparator
                        .comparingInt((Candidate c) -> Math.max(Math.abs(c.x - corpseX), Math.abs(c.y - corpseY)))
                        .thenComparingInt(c -> Math.abs(c.x - corpseX) + Math.abs(c.y - corpseY))
                        .thenComparingInt(Candidate::key))
                .orElse(null);
    }

    /** Mirrors the client's own click path: CALLBACK_LOOT_INFO configured with the loot token component. */
    static void openLoot(Object game, Object lootToken) throws Exception {
        if (game == null || lootToken == null) throw new IllegalArgumentException("game/lootToken null");
        ClassLoader loader = game.getClass().getClassLoader();
        Class<?> actionBase = Class.forName(ACTION_BASE_CLASS, false, loader);
        Class<?> lootInfo = Class.forName(LOOT_INFO_ACTION_CLASS, false, loader);
        Class<?> tokenClass = Class.forName(LOOT_TOKEN_COMPONENT_CLASS, false, loader);

        Method networkGetter = game.getClass().getMethod(NETWORK_GETTER);
        Object network = networkGetter.invoke(game);
        if (network == null) throw new IllegalStateException("network indisponivel para loot");

        Method factory = network.getClass().getMethod(FACTORY_OR_SEND, Class.class);
        Object action = factory.invoke(network, lootInfo);
        if (action == null) throw new IllegalStateException("CALLBACK_LOOT_INFO factory retornou null");

        Method configure = lootInfo.getMethod(FACTORY_OR_SEND, tokenClass);
        Object configured = configure.invoke(action, lootToken);
        if (configured != null) action = configured;

        Method send = network.getClass().getMethod(FACTORY_OR_SEND, actionBase);
        send.invoke(network, action);
    }

    /** Sends SPACE through LibGDX's current InputProcessor, so OS/window focus is not required. */

    static boolean pressSpaceThroughGdx(Object game, long now) {
        return InputArbiter.press(game, "SPACE", "AutoLoot", now);
    }

    static boolean pressSpaceThroughGdx(Object game) {
        try {
            ClassLoader loader = game.getClass().getClassLoader();
            Class<?> gdx = Class.forName("com.badlogic.gdx.Gdx", false, loader);
            Field inputField = gdx.getField("input");
            Object input = inputField.get(null);
            if (input == null) return false;

            Class<?> inputInterface = Class.forName("com.badlogic.gdx.Input", false, loader);
            Method getInputProcessor = inputInterface.getMethod("getInputProcessor");
            Object processor = getInputProcessor.invoke(input);
            if (processor == null) return false;

            Class<?> keys = Class.forName("com.badlogic.gdx.Input$Keys", false, loader);
            int space = keys.getField("SPACE").getInt(null);
            Class<?> processorInterface = Class.forName("com.badlogic.gdx.InputProcessor", false, loader);
            Method keyDown = processorInterface.getMethod("keyDown", int.class);
            Method keyUp = processorInterface.getMethod("keyUp", int.class);
            keyDown.invoke(processor, space);
            keyUp.invoke(processor, space);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
