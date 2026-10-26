package miragelab;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Party-aware Anti-KS support.
 *
 * Reliable path: explicit party member names from HelperConfig.
 * Best-effort path: inspect player entity components for readable PARTY/ALLY/GROUP markers.
 * Obfuscated clients may expose no readable marker, so automatic detection is intentionally
 * conservative to avoid classifying strangers as party members.
 */
final class PartySupport {
    private static final long CACHE_MS = 450L;
    private static final Map<Integer, Entry> cache = new HashMap<>();
    private static volatile int lastManualMatches;
    private static volatile int lastAutoMatches;
    private static volatile int lastUnknownPlayers;

    private PartySupport() {}

    static boolean isPartyMember(Object playerEntity, int id, String name, HelperConfig.Config c, long now) {
        if (manualNameMatch(name, c)) {
            lastManualMatches++;
            cache.put(id, new Entry(true, now + CACHE_MS, "manual"));
            return true;
        }
        if (c == null || !c.partyAutoDetect || playerEntity == null) {
            lastUnknownPlayers++;
            return false;
        }
        Entry old = cache.get(id);
        if (old != null && old.until >= now) return old.party;
        boolean auto = detectMarker(playerEntity);
        if (auto) lastAutoMatches++; else lastUnknownPlayers++;
        cache.put(id, new Entry(auto, now + CACHE_MS, auto ? "auto" : "none"));
        if (cache.size() > 256) cache.entrySet().removeIf(e -> e.getValue().until < now);
        return auto;
    }

    static void beginScan() {
        lastManualMatches = 0;
        lastAutoMatches = 0;
        lastUnknownPlayers = 0;
    }

    static String summary() {
        return "party manual=" + lastManualMatches + " auto=" + lastAutoMatches + " ext?=" + lastUnknownPlayers;
    }

    static boolean manualNameMatchForTests(String name, String list) {
        HelperConfig.Config c = new HelperConfig.Config();
        c.partyMembers = list == null ? "" : list;
        return manualNameMatch(name, c);
    }

    static boolean markerTextForTests(String value) { return markerText(value); }

    private static boolean manualNameMatch(String name, HelperConfig.Config c) {
        if (name == null || c == null) return false;
        return HelperConfig.normalizedPartyMembers(c).contains(name.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean detectMarker(Object entity) {
        try {
            for (Object component : componentsOf(entity)) {
                if (component == null) continue;
                Class<?> cc = component.getClass();
                if (markerText(cc.getName()) || markerText(cc.getSimpleName())) return true;
                for (Field f : cc.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) continue;
                    try {
                        f.setAccessible(true);
                        Object v = f.get(component);
                        if (v == null) continue;
                        if (v instanceof Enum<?> en) {
                            if (markerText(en.name()) || markerText(String.valueOf(en))) return true;
                        } else if (v instanceof CharSequence cs) {
                            if (markerText(cs.toString())) return true;
                        }
                    } catch (Throwable ignored) { }
                }
            }
        } catch (Throwable ignored) { }
        return false;
    }

    private static boolean markerText(String raw) {
        if (raw == null) return false;
        String s = raw.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (s.contains("NOT_PARTY") || s.contains("NO_PARTY") || s.contains("PARTY_NONE") || s.equals("NONE")) return false;
        return s.contains("PARTY_MEMBER") || s.contains("IN_PARTY") || s.contains("PARTYALLY")
                || s.contains("PARTY_ALLY") || s.contains("TEAMMATE") || s.contains("GROUP_MEMBER")
                || s.endsWith(".PARTY") || s.equals("PARTY") || s.equals("ALLY");
    }

    private static List<Object> componentsOf(Object entity) throws Exception {
        List<Object> out = new ArrayList<>();
        Object arr = entity.getClass().getMethod("getComponents").invoke(entity);
        if (arr == null) return out;
        if (arr instanceof Iterable<?> it) {
            for (Object x : it) out.add(x);
            return out;
        }
        try {
            Method size = arr.getClass().getMethod("size");
            Method get = arr.getClass().getMethod("get", int.class);
            int n = ((Number) size.invoke(arr)).intValue();
            for (int i = 0; i < n; i++) out.add(get.invoke(arr, i));
        } catch (Throwable ignored) { }
        return out;
    }

    private record Entry(boolean party, long until, String source) { }
}
