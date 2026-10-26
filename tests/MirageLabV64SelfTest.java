package miragelab;

import java.lang.reflect.*;
import java.util.*;

public final class MirageLabV64SelfTest {
    private static int passed;
    public static void main(String[] args) throws Exception {
        testRangedDetection();
        testAggressorExtendedDetection();
        testAntiKsClaimedTarget();
        testAntiKsProximityFallback();
        testFoodCalibrationChoice();
        testFoodCalibrationRejectsNoIncrease();
        testLateArrivalHealingWindow();
        testPathDetourStillWorks();
        System.out.println("ALL PASS " + passed + "/8");
    }

    static void testRangedDetection() throws Exception {
        Harness h = new Harness();
        h.setConfig("detectionRange", 4);
        h.setConfig("rangedDetectionRange", 7);
        h.setConfig("antiKsEnabled", false);
        Object m = h.monster(101, "Ranged Troll", 6, 0);
        Object w = h.world(Map.of(101, m), Map.of(), Set.of(), false);
        Object got = h.nearest(w);
        ok(got != null && h.monsterId(got) == 101, "ranged-detection-6-tiles");
    }


    static void testAggressorExtendedDetection() throws Exception {
        Harness h = new Harness();
        h.setConfig("detectionRange", 4);
        h.setConfig("rangedDetectionRange", 7);
        h.setConfig("aggroDetectionRange", 12);
        h.setConfig("antiKsEnabled", false);
        Object m = h.monster(150, "Ranged Troll", 10, 0);
        Object w = h.worldWithHostiles(Map.of(150, m), Map.of(), Set.of(), Set.of(150), true);
        Object got = h.nearest(w);
        ok(got != null && h.monsterId(got) == 150, "ranged-aggressor-extended-detection");
    }
    static void testAntiKsClaimedTarget() throws Exception {
        Harness h = new Harness();
        h.setConfig("detectionRange", 4);
        h.setConfig("rangedDetectionRange", 7);
        h.setConfig("antiKsEnabled", true);
        Object m = h.monster(202, "Troll", 3, 0);
        Object w = h.world(Map.of(202, m), Map.of(), Set.of(202), true);
        ok(h.nearest(w) == null, "anti-ks-remote-target-claim");
    }

    static void testAntiKsProximityFallback() throws Exception {
        Harness h = new Harness();
        h.setConfig("antiKsEnabled", true);
        h.setConfig("antiKsPlayerRadius", 2);
        Object m = h.monster(303, "Troll", 3, 0);
        Object p = h.occupant(77, "Tester2", "PLAYER", 3, 1);
        Object w = h.world(Map.of(303, m), Map.of(77, p), Set.of(), false);
        ok(h.nearest(w) == null, "anti-ks-player-proximity-fallback");
    }

    static void testFoodCalibrationChoice() {
        Map<String,Double> before = Map.of("Stats#hp", 40.0, "Vitals#nourishment", 24.0, "Stats#mana", 30.0);
        Map<String,Double> after = Map.of("Stats#hp", 41.0, "Vitals#nourishment", 78.0, "Stats#mana", 30.0);
        String k = AutoFoodSupport.chooseCalibrationSensorForTests(before, after, Map.of("Vitals#nourishment", 4));
        ok("Vitals#nourishment".equals(k), "autofood-calibration-prefers-real-increase");
    }

    static void testFoodCalibrationRejectsNoIncrease() {
        String k = AutoFoodSupport.chooseCalibrationSensorForTests(
                Map.of("A#x", 20.0, "B#y", 50.0),
                Map.of("A#x", 20.5, "B#y", 49.0), Map.of());
        ok(k == null, "autofood-no-unsafe-guess");
    }

    static void testLateArrivalHealingWindow() {
        ok(CaveBotEngine.shouldWaitLateArrival(false, 1200, 1000, 500), "late-arrival-grace-waits");
        ok(!CaveBotEngine.shouldWaitLateArrival(false, 1501, 1000, 500), "late-arrival-grace-expires");
        ok(!CaveBotEngine.shouldWaitLateArrival(true, 1200, 1000, 500), "late-arrival-visible-block-does-not-wait");
        // Count the three assertions as one scenario for report readability.
        passed -= 2;
    }

    static void testPathDetourStillWorks() {
        Set<Pathfinder.Pos> goals = Set.of(new Pathfinder.Pos(5,0));
        Set<Pathfinder.Pos> blocked = Set.of(new Pathfinder.Pos(2,0));
        List<Pathfinder.Pos> path = Pathfinder.find(new Pathfinder.Pos(0,0), goals, blocked, 5, 1000, p -> true);
        ok(!path.isEmpty() && path.get(path.size()-1).equals(new Pathfinder.Pos(5,0)) && !path.contains(new Pathfinder.Pos(2,0)),
                "pathing-regression-dynamic-detour");
    }

    static void ok(boolean value, String name) {
        if (!value) throw new AssertionError("FAIL " + name);
        passed++; System.out.println("PASS " + name);
    }

    static final class Harness {
        final Class<?> engine;
        final Class<?> posC, monsterC, occupantC, worldC;
        final Object config;
        final Method nearest;
        final Constructor<?> posCtor, monsterCtor, occupantCtor, worldCtor;
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
        }
        static Constructor<?> ctor(Class<?> c, Class<?>... args) throws Exception { Constructor<?> x=c.getDeclaredConstructor(args); x.setAccessible(true); return x; }
        Object pos(int x,int y) throws Exception { return posCtor.newInstance(x,y); }
        Object monster(int id,String n,int x,int y) throws Exception { return monsterCtor.newInstance(id,n,pos(x,y)); }
        Object occupant(int id,String n,String t,int x,int y) throws Exception { return occupantCtor.newInstance(id,n,t,pos(x,y)); }
        Object world(Map<Integer,Object> monsters, Map<Integer,Object> occ, Set<Integer> claimed, boolean telemetry) throws Exception {
            return worldWithHostiles(monsters, occ, claimed, Set.of(), telemetry);
        }
        Object worldWithHostiles(Map<Integer,Object> monsters, Map<Integer,Object> occ, Set<Integer> claimed, Set<Integer> hostile, boolean telemetry) throws Exception {
            return worldCtor.newInstance(pos(0,0), null, monsters, occ, Set.of(), List.of(), claimed, hostile, null, telemetry);
        }
        Object nearest(Object w) throws Exception { return nearest.invoke(null,w,pos(0,0),null,System.currentTimeMillis()); }
        void setConfig(String name,Object v) throws Exception { Field f=config.getClass().getDeclaredField(name); f.setAccessible(true); f.set(config,v); }
        int monsterId(Object m) throws Exception { Method id=monsterC.getDeclaredMethod("id"); id.setAccessible(true); return (Integer)id.invoke(m); }
    }
}
