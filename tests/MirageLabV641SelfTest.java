package miragelab;

import java.lang.reflect.*;
import java.util.*;

public final class MirageLabV641SelfTest {
    private static int passed;
    public static void main(String[] args) throws Exception {
        testHostileStepFailuresDontImmediateBlacklist();
        testNormalStepFailuresStillBlacklist();
        testHostileBlacklistShorter();
        testMovedBlacklistedMonsterBecomesEligible();
        testStaticBlacklistedMonsterStillSkipped();
        testRangedAggressorPriorityRegression();
        System.out.println("ALL PASS " + passed + "/6");
    }

    static void testHostileStepFailuresDontImmediateBlacklist() {
        ok(!CaveBotEngine.shouldBlacklistFromBlockedSteps(true, 10, 4),
                "hostile-not-blacklisted-by-assisted-step-mismatch");
    }

    static void testNormalStepFailuresStillBlacklist() {
        ok(CaveBotEngine.shouldBlacklistFromBlockedSteps(false, 4, 4),
                "normal-target-blacklists-after-threshold");
    }

    static void testHostileBlacklistShorter() {
        long hostile = CaveBotEngine.unreachableBlacklistDurationForTests(true, 15000, 3000);
        long normal = CaveBotEngine.unreachableBlacklistDurationForTests(false, 15000, 3000);
        ok(hostile == 3000 && normal == 15000 && hostile < normal,
                "hostile-blacklist-is-short-soft-retry");
    }

    static void testMovedBlacklistedMonsterBecomesEligible() throws Exception {
        Harness h = new Harness();
        Object oldPos = h.pos(3, 0);
        Object moved = h.monster(401, "Troll", 4, 0);
        h.blacklist(401, oldPos, System.currentTimeMillis() + 60_000);
        Object w = h.worldWithHostiles(Map.of(401, moved), Map.of(), Set.of(), Set.of(), true);
        Object got = h.nearest(w);
        ok(got != null && h.monsterId(got) == 401,
                "moved-target-clears-stale-unreachable-blacklist");
    }

    static void testStaticBlacklistedMonsterStillSkipped() throws Exception {
        Harness h = new Harness();
        Object pos = h.pos(3, 0);
        Object m = h.monster(402, "Troll", 3, 0);
        h.blacklist(402, pos, System.currentTimeMillis() + 60_000);
        Object w = h.worldWithHostiles(Map.of(402, m), Map.of(), Set.of(), Set.of(), true);
        ok(h.nearest(w) == null, "static-blacklisted-target-remains-skipped");
    }

    static void testRangedAggressorPriorityRegression() throws Exception {
        Harness h = new Harness();
        h.setConfig("detectionRange", 4);
        h.setConfig("rangedDetectionRange", 7);
        h.setConfig("aggroDetectionRange", 12);
        h.setConfig("antiKsEnabled", false);
        Object passive = h.monster(500, "Passive", 3, 0);
        Object hostile = h.monster(501, "Hostile Ranged", 10, 0);
        Object w = h.worldWithHostiles(Map.of(500, passive, 501, hostile), Map.of(), Set.of(), Set.of(501), true);
        Object got = h.nearest(w);
        ok(got != null && h.monsterId(got) == 501, "hostile-ranged-keeps-priority");
    }

    static void ok(boolean value, String name) {
        if (!value) throw new AssertionError("FAIL " + name);
        passed++; System.out.println("PASS " + name);
    }

    static final class Harness {
        final Class<?> engine, posC, monsterC, occupantC, worldC;
        final Object config;
        final Method nearest;
        final Constructor<?> posCtor, monsterCtor, occupantCtor, worldCtor;
        final Map<Integer,Long> unreachableUntil;
        final Map<Integer,Object> unreachableAt;

        @SuppressWarnings("unchecked")
        Harness() throws Exception {
            engine = Class.forName("miragelab.CaveBotEngine");
            posC = Class.forName("miragelab.CaveBotEngine$Position");
            monsterC = Class.forName("miragelab.CaveBotEngine$Monster");
            occupantC = Class.forName("miragelab.CaveBotEngine$Occupant");
            worldC = Class.forName("miragelab.CaveBotEngine$WorldState");
            posCtor = ctor(posC, int.class, int.class);
            monsterCtor = ctor(monsterC, int.class, String.class, posC);
            occupantCtor = ctor(occupantC, int.class, String.class, String.class, posC);
            worldCtor = worldC.getDeclaredConstructors()[0]; worldCtor.setAccessible(true);
            Field cf = engine.getDeclaredField("config"); cf.setAccessible(true); config = cf.get(null);
            nearest = engine.getDeclaredMethod("nearestEligibleMonster", worldC, posC, posC, long.class); nearest.setAccessible(true);
            Field uf = engine.getDeclaredField("unreachableUntil"); uf.setAccessible(true);
            unreachableUntil = (Map<Integer,Long>)uf.get(null);
            Field up = engine.getDeclaredField("unreachableAtPosition"); up.setAccessible(true);
            unreachableAt = (Map<Integer,Object>)up.get(null);
            unreachableUntil.clear(); unreachableAt.clear();
        }
        static Constructor<?> ctor(Class<?> c, Class<?>... args) throws Exception { Constructor<?> x=c.getDeclaredConstructor(args); x.setAccessible(true); return x; }
        Object pos(int x,int y) throws Exception { return posCtor.newInstance(x,y); }
        Object monster(int id,String n,int x,int y) throws Exception { return monsterCtor.newInstance(id,n,pos(x,y)); }
        Object occupant(int id,String n,String t,int x,int y) throws Exception { return occupantCtor.newInstance(id,n,t,pos(x,y)); }
        Object worldWithHostiles(Map<Integer,Object> monsters, Map<Integer,Object> occ, Set<Integer> claimed, Set<Integer> hostile, boolean telemetry) throws Exception {
            return worldCtor.newInstance(pos(0,0), null, monsters, occ, Set.of(), List.of(), claimed, hostile, null, telemetry);
        }
        Object nearest(Object w) throws Exception { return nearest.invoke(null,w,pos(0,0),null,System.currentTimeMillis()); }
        void setConfig(String name,Object v) throws Exception { Field f=config.getClass().getDeclaredField(name); f.setAccessible(true); f.set(config,v); }
        int monsterId(Object m) throws Exception { Method id=monsterC.getDeclaredMethod("id"); id.setAccessible(true); return (Integer)id.invoke(m); }
        void blacklist(int id, Object pos, long until) { unreachableUntil.put(id, until); unreachableAt.put(id, pos); }
    }
}
