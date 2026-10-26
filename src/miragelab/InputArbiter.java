package miragelab;

/**
 * Serializes helper-generated hotkeys and emits a real key pulse instead of
 * keyDown+keyUp in the same stack frame. v8.4 fixes repeated F-key actions
 * that some client screens ignored after the first event.
 */
final class InputArbiter {
    private static long lastPressAt;
    private static String lastSource = "-";
    private static String lastKey = "-";
    private static int presses;

    private static Object pulseGame;
    private static String pulseKey;
    private static int pulseCode = -1;
    private static long releaseAt;
    private static boolean keyHeld;

    private InputArbiter() {}

    static synchronized void tick(Object game, long now) {
        if (!keyHeld || now < releaseAt) return;
        Object g = pulseGame != null ? pulseGame : game;
        HotkeySupport.keyUp(g, pulseCode);
        keyHeld = false;
        pulseGame = null;
        pulseKey = null;
        pulseCode = -1;
        releaseAt = 0L;
    }

    static synchronized boolean press(Object game, String hotkey, String source, long now) {
        tick(game, now);
        HelperConfig.Config c = HelperConfig.get(now);
        int gap = Math.max(40, c.inputGlobalGapMs);
        if (keyHeld || now - lastPressAt < gap) return false;
        int code = HotkeySupport.resolve(game == null ? null : game.getClass().getClassLoader(), hotkey);
        if (code < 0 || !HotkeySupport.keyDown(game, code)) return false;
        lastPressAt = now;
        lastSource = source == null ? "?" : source;
        lastKey = hotkey == null ? "?" : hotkey;
        presses++;
        keyHeld = true;
        pulseGame = game;
        pulseKey = hotkey;
        pulseCode = code;
        releaseAt = now + Math.max(20, c.inputPulseMs);
        return true;
    }

    static synchronized boolean isCoolingDown(long now) {
        HelperConfig.Config c = HelperConfig.get(now);
        return keyHeld || now - lastPressAt < Math.max(40, c.inputGlobalGapMs);
    }

    static synchronized long lastPressAtForTests() { return lastPressAt; }
    static synchronized boolean isKeyHeldForTests() { return keyHeld; }
    static synchronized long releaseAtForTests() { return releaseAt; }

    static synchronized String summary() {
        return "InputBus presses=" + presses + " last=" + lastSource + ":" + lastKey
                + (keyHeld ? " pulse=" + pulseKey + " held" : "");
    }

    static synchronized void resetForTests() {
        lastPressAt = 0L; lastSource = "-"; lastKey = "-"; presses = 0;
        pulseGame = null; pulseKey = null; pulseCode = -1; releaseAt = 0L; keyHeld = false;
    }

    static boolean canPressForTests(long now, long lastAt, int gap) {
        return now - lastAt >= Math.max(40, gap);
    }

    static boolean pulseDueForTests(long now, long release) { return now >= release; }
}
