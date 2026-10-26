package miragelab;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

final class HotkeySupport {
    private HotkeySupport() {}

    static boolean press(Object game, String hotkey) {
        int code = resolve(game == null ? null : game.getClass().getClassLoader(), hotkey);
        if (code < 0) return false;
        return keyDown(game, code) && keyUp(game, code);
    }

    static boolean keyDown(Object game, String hotkey) {
        int code = resolve(game == null ? null : game.getClass().getClassLoader(), hotkey);
        return code >= 0 && keyDown(game, code);
    }

    static boolean keyUp(Object game, String hotkey) {
        int code = resolve(game == null ? null : game.getClass().getClassLoader(), hotkey);
        return code >= 0 && keyUp(game, code);
    }

    static boolean keyDown(Object game, int code) { return fire(game, code, true); }
    static boolean keyUp(Object game, int code) { return fire(game, code, false); }

    private static boolean fire(Object game, int code, boolean down) {
        try {
            if (game == null) return false;
            ClassLoader loader = game.getClass().getClassLoader();
            Class<?> gdx = Class.forName("com.badlogic.gdx.Gdx", false, loader);
            Object input = gdx.getField("input").get(null);
            if (input == null) return false;
            Class<?> inputInterface = Class.forName("com.badlogic.gdx.Input", false, loader);
            Object processor = inputInterface.getMethod("getInputProcessor").invoke(input);
            if (processor == null) return false;
            Class<?> processorInterface = Class.forName("com.badlogic.gdx.InputProcessor", false, loader);
            Method m = processorInterface.getMethod(down ? "keyDown" : "keyUp", int.class);
            m.invoke(processor, code);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    static int resolve(ClassLoader loader, String hotkey) {
        try {
            if (loader == null || hotkey == null) return -1;
            String s = hotkey.trim().toUpperCase(Locale.ROOT);
            String field;
            if (s.matches("[0-9]")) field = "NUM_" + s;
            else if (s.matches("F([1-9]|1[0-2])")) field = s;
            else if (s.length() == 1 && Character.isLetter(s.charAt(0))) field = s;
            else if (s.equals("SPACE")) field = "SPACE";
            else return -1;
            Class<?> keys = Class.forName("com.badlogic.gdx.Input$Keys", false, loader);
            Field f = keys.getField(field);
            return f.getInt(null);
        } catch (Throwable t) {
            return -1;
        }
    }
}
