package miragelab;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

/** Runtime calibration/observation for player HP, mana and monster HP. */
final class VitalSensorSupport {
    private static final Path STATE = Path.of(System.getProperty("user.home"), "Downloads", "MirageLab", "state");
    private static final Path HP_FILE = STATE.resolve("player_hp_sensor.txt");
    private static final Path MANA_FILE = STATE.resolve("player_mana_sensor.txt");
    private static final Path MONSTER_HP_FILE = STATE.resolve("monster_hp_sensor.txt");
    private static final Path CALIBRATION = STATE.resolve("vitals_calibration.txt");
    private static final Path MONSTER_TRACE = STATE.resolve("monster_hp_probe_v91.txt");
    private static final Map<Class<?>, List<Field>> NUMERIC_FIELDS = new HashMap<>();
    private static final Map<Class<?>, List<Method>> NUMERIC_GETTERS = new HashMap<>();
    private static final Map<String, ProbeChange> PROBE_CHANGES = new LinkedHashMap<>();
    private static long lastProbeDumpAt, lastSampleCleanupAt;
    private static final int PROBE_LIMIT = 36;
    private static final String POSITION_CLASS = "uk.co.harveydogs.mirage.shared.yY8x1yOsuoav.CzFlqirRObv";
    private static final String IDENTITY_CLASS = "uk.co.harveydogs.mirage.shared.yY8x1yOsuoav.D4yZmsobaWAL";
    // Confirmed from the client bytecode: this is movement/interpolation state, not HP.
    private static final String KNOWN_BAD_MONSTER_SENSOR_PREFIX =
            "uk.co.harveydogs.mirage.client.GEcHnxVxWL24.yY8x1yOsuoav.yY8x1yOsuoav.gauUiPW91yfVcAHjQEvjcCLZ#";

    private static final boolean LEGACY_HP_LEARNING_ENABLED = false;
    private static Sensor hpSensor, manaSensor, monsterHpSensor;
    private static boolean loaded;
    private static String lastCalibrationCommand = "";
    private static Calibration calibration;
    private static Map<String, Double> lastPlayerMetrics = Map.of();
    private static long lastPlayerSampleAt;
    private static final Map<Integer, Map<String, Double>> lastMonsterMetrics = new HashMap<>();
    private static final Map<Integer, Long> lastMonsterSampleAt = new HashMap<>();
    private static final Map<String, Integer> monsterVotes = new HashMap<>();
    private static final Map<String, SensorEvidence> monsterEvidence = new HashMap<>();
    private static final Map<Integer, MonsterHpMemory> monsterMemory = new HashMap<>();
    private static String monsterProbeSummary = "probe: aguardando dano";
    private static String lastEvent;
    private static long monsterSamples;
    private static long monsterNonemptySamples;
    private static long monsterChangedSamples;
    private static long monsterReadErrors;
    private static int monsterLastMetricCount;

    private VitalSensorSupport() {}

    record Snapshot(Double hpPercent, Double manaPercent, String hpStatus, String manaStatus, String event) { }
    record MonsterVitals(Double currentHp, Double maxHp, Double percentHp, long sampleAt) {
        boolean hasAbsolute(){return currentHp!=null&&maxHp!=null&&currentHp>0&&maxHp>0;}
        boolean hasPercent(){return percentHp!=null&&percentHp>0.1&&percentHp<=100.0;}
        boolean deadLike(){return (currentHp!=null&&currentHp<=0)||(percentHp!=null&&percentHp<=0.1);}
    }
    record MonsterHpObservation(Double current, Double max, Double percent, long ageMs, boolean fresh, boolean known) {
        boolean knownAbsolute(){return current!=null&&max!=null&&current>0&&max>0;}
        boolean knownPercent(){return known&&percent!=null&&percent>0.1&&percent<=100.0;}
    }

    static Snapshot tickPlayer(Object playerEntity, long now) {
        load();
        String event = null;
        try {
            Files.createDirectories(STATE);
            if (!Files.exists(CALIBRATION)) Files.writeString(CALIBRATION, "idle\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            String cmd = Files.readString(CALIBRATION, StandardCharsets.UTF_8).replace("\u0000", "").trim().toLowerCase(Locale.ROOT);
            if (!cmd.equals(lastCalibrationCommand)) {
                lastCalibrationCommand = cmd;
                if (cmd.startsWith("hp")) {
                    calibration = new Calibration("HP", sampleMetrics(playerEntity), now, now + 10000L);
                    event = "AUTOHEAL CALIBRACAO HP armada - receba dano nos proximos 10s";
                } else if (cmd.startsWith("mana")) {
                    calibration = new Calibration("MANA", sampleMetrics(playerEntity), now, now + 10000L);
                    event = "AUTOHEAL CALIBRACAO MANA armada - gaste mana nos proximos 10s";
                } else if (cmd.startsWith("clear")) {
                    hpSensor = manaSensor = null;
                    Files.deleteIfExists(HP_FILE); Files.deleteIfExists(MANA_FILE);
                    event = "AUTOHEAL sensores HP/MANA limpos";
                }
            }

            if (playerEntity != null && now - lastPlayerSampleAt >= 80L) {
                lastPlayerSampleAt = now;
                Map<String, Double> current = sampleMetrics(playerEntity);
                lastPlayerMetrics = current;
                if (calibration != null) {
                    Sensor choice = chooseDropSensor(calibration.before, current);
                    if (choice != null) {
                        if ("HP".equals(calibration.kind)) { hpSensor = choice; persist(HP_FILE, choice); }
                        else { manaSensor = choice; persist(MANA_FILE, choice); }
                        event = "AUTOHEAL " + calibration.kind + " CALIBRADO - " + shortKey(choice.currentKey)
                                + "=" + fmt(percent(choice, current)) + "%";
                        calibration = null;
                    } else if (now >= calibration.deadline) {
                        event = "AUTOHEAL CALIBRACAO " + calibration.kind + " expirou sem queda detectavel";
                        calibration = null;
                    }
                }
            }
        } catch (Throwable t) {
            event = "AUTOHEAL SENSOR ERRO - " + t.getClass().getSimpleName();
        }
        if (event != null && event.equals(lastEvent)) event = null;
        if (event != null) lastEvent = event;
        return new Snapshot(percent(hpSensor, lastPlayerMetrics), percent(manaSensor, lastPlayerMetrics),
                hpSensor == null ? "HP=UNCALIBRATED" : "HP=" + fmt(percent(hpSensor, lastPlayerMetrics)) + "%",
                manaSensor == null ? "MANA=UNCALIBRATED" : "MANA=" + fmt(percent(manaSensor, lastPlayerMetrics)) + "%",
                event);
    }

    /** Samples the monster and returns current/max/% when the calibrated component exposes them. */
    static MonsterVitals observeMonsterVitals(Object entity, int id, boolean targeted, long now) {
        load();
        if (entity == null) return rememberedVitals(id, now, 700L);
        // Throttle deep reflection, not the target scan. HP scan is far more expensive
        // than coordinates, and 16ms scans repeatedly read identical component fields.
        Long last=lastMonsterSampleAt.get(id);
        if(last!=null && now>=last && now-last<140L) return rememberedVitals(id,now,700L);
        lastMonsterSampleAt.put(id,now);
        try {
            VitalsAdapter0859.Snapshot typed=VitalsAdapter0859.read(entity);
            if(typed!=null && typed.hpValid()){
                MonsterVitals actual=new MonsterVitals(typed.hp(),typed.maxHp(),100*typed.hp()/typed.maxHp(),now);
                rememberMonsterHp(id,actual,now);
                return actual;
            }
            Map<String, Double> current = sampleMetrics(entity);
            monsterSamples++; monsterLastMetricCount=current.size();
            if (!current.isEmpty()) monsterNonemptySamples++;
            // v9.6: Direct ECS named pair takes precedence over all historical probes.
            // A declining obfuscated field is evidence of damage, NOT proof of max HP.
            GameDirectVitals.Reading direct = GameDirectVitals.monster(current, now);
            if (direct.valid()) {
                MonsterVitals actual = new MonsterVitals(direct.current(),direct.max(),direct.percent(),now);
                rememberMonsterHp(id,actual,now);
                lastMonsterMetrics.put(id,current);
                return actual;
            }
            if (monsterHpSensor != null) {
                if (!plausibleMonsterSensor(monsterHpSensor)) {
                    invalidateMonsterSensor();
                } else {
                    MonsterVitals v = vitals(monsterHpSensor, current, now);
                    if (v != null) {
                        rememberMonsterHp(id, v, now);
                        lastMonsterMetrics.put(id, current);
                        return v;
                    }
                    lastMonsterMetrics.put(id, current);
                    return rememberedVitals(id, now, 700L);
                }
            }

            Map<String, Double> before = lastMonsterMetrics.put(id, current);
            if (before != null) {
                collectProbeChanges(before, current, id, targeted, now);
                if (now - lastProbeDumpAt >= 1800L) dumpProbeSummary(now);
                if (metricsChanged(before,current)) monsterChangedSamples++;
                Sensor candidate = chooseDropSensor(before, current, true);
                if (candidate != null && plausibleMonsterSensor(candidate)) {
                    String k = candidate.currentKey + "|" + candidate.maxKey;
                    int weight = targeted ? 3 : 1;
                    int votes = monsterVotes.merge(k, weight, Integer::sum);
                    SensorEvidence ev = monsterEvidence.computeIfAbsent(k, z -> new SensorEvidence());
                    ev.votes += weight;
                    ev.entityIds.add(id);
                    ev.lastSeenAt = now;
                    if (targeted) ev.targetedVotes += weight;
                    boolean ratioOnly = "RATIO".equals(candidate.maxKey);
                    boolean percentOnly = "PERCENT".equals(candidate.maxKey);
                    int required = ratioOnly ? 9 : (percentOnly ? 8 : 3);
                    boolean enoughBreadth = !ratioOnly && !percentOnly
                            ? (ev.entityIds.size() >= 1 && ev.targetedVotes >= 2)
                            : (ev.entityIds.size() >= 2 || ev.targetedVotes >= required);
                    monsterProbeSummary = "probe=" + shortKey(candidate.currentKey) + "/" + candidate.maxKey
                            + " votes=" + votes + " mobs=" + ev.entityIds.size();
                    if (LEGACY_HP_LEARNING_ENABLED && votes >= required && enoughBreadth) {
                        monsterHpSensor = candidate;
                        persist(MONSTER_HP_FILE, candidate);
                        MonsterVitals v = vitals(candidate, current, now);
                        if (v != null) rememberMonsterHp(id, v, now);
                        monsterProbeSummary = "LOCK " + shortKey(candidate.currentKey) + "/" + candidate.maxKey;
                    }
                }
            }
            // The old implementation retained every entity ID forever; clear snapshots
            // periodically without affecting currently observed monsters.
            if (now-lastSampleCleanupAt>15000L) {
                lastSampleCleanupAt=now;
                if(lastMonsterMetrics.size()>700)lastMonsterMetrics.clear();
                lastMonsterSampleAt.entrySet().removeIf(e->now-e.getValue()>12000L);
                monsterMemory.entrySet().removeIf(e->now-e.getValue().updatedAt>12000L);
                monsterEvidence.entrySet().removeIf(e->now-e.getValue().lastSeenAt>30000L);
            }
        } catch (Throwable ignored) { monsterReadErrors++; }
        return rememberedVitals(id, now, 7000L);
    }

    static Double observeMonster(Object entity, int id, boolean targeted, long now) {
        MonsterVitals v=observeMonsterVitals(entity,id,targeted,now);
        return v==null?null:v.percentHp;
    }

    static MonsterHpObservation monsterHpObservation(int id, long now, long maxAgeMs) {
        MonsterHpMemory m = monsterMemory.get(id);
        if (m == null) return new MonsterHpObservation(null,null,null, Long.MAX_VALUE, false, false);
        long age = Math.max(0L, now - m.updatedAt);
        if (age > Math.max(0L, maxAgeMs)) return new MonsterHpObservation(null,null,null, age, false, false);
        return new MonsterHpObservation(m.current,m.max,m.percent, age, age <= 250L, m.percent!=null);
    }

    static Double monsterHpPercent(int id) { MonsterVitals v=rememberedVitals(id,System.currentTimeMillis(),7000L);return v==null?null:v.percentHp; }
    static Double monsterHpCurrent(int id) { MonsterVitals v=rememberedVitals(id,System.currentTimeMillis(),7000L);return v==null?null:v.currentHp; }
    static Double monsterHpMax(int id) { MonsterVitals v=rememberedVitals(id,System.currentTimeMillis(),7000L);return v==null?null:v.maxHp; }

    static String monsterHpStatus() {
        load();
        return monsterHpSensor == null
                ? "Mob HP=ECS direto (max nao verificado) mem=" + monsterMemory.size() + " "
                  + GameDirectVitals.monsterStatus() + " " + monsterSamplerStatus()
                : "Mob HP=" + shortKey(monsterHpSensor.currentKey) + "/" + monsterHpSensor.maxKey + " mem=" + monsterMemory.size() + " " + monsterSamplerStatus();
    }

    static Path calibrationFile() { return CALIBRATION; }

    static synchronized void clearMonsterSensor() {
        load();
        monsterHpSensor = null;
        monsterVotes.clear();
        monsterEvidence.clear();
        monsterProbeSummary = "probe: aguardando dano";
        lastMonsterMetrics.clear();
        lastMonsterSampleAt.clear();
        monsterMemory.clear();
        PROBE_CHANGES.clear();
        monsterSamples=monsterNonemptySamples=monsterChangedSamples=monsterReadErrors=0L; monsterLastMetricCount=0;
        try { Files.deleteIfExists(MONSTER_HP_FILE); } catch (Exception ignored) { }
    }

    static String chooseDropKeyForTests(Map<String, Double> before, Map<String, Double> after) {
        Sensor s = chooseDropSensor(before, after);
        return s == null ? null : s.currentKey;
    }

    static String chooseDropModeForTests(Map<String, Double> before, Map<String, Double> after) {
        Sensor s = chooseDropSensor(before, after);
        return s == null ? null : s.maxKey;
    }

    static Double sensorPercentForTests(String currentKey, String maxKey, Map<String, Double> metrics) {
        return percent(new Sensor(currentKey, maxKey), metrics);
    }

    static boolean disallowedMonsterMetricForTests(String key) { return disallowedMonsterMetric(key); }

    static boolean plausibleMonsterSensorForTests(String currentKey, String maxKey) {
        return plausibleMonsterSensor(new Sensor(currentKey, maxKey));
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        hpSensor = read(HP_FILE); manaSensor = read(MANA_FILE); monsterHpSensor = read(MONSTER_HP_FILE);
        // v9.1 may also have persisted screen-position fields for the player's HP.
        if(hpSensor != null && !plausibleMonsterSensor(hpSensor)) {
            hpSensor = null; try {Files.deleteIfExists(HP_FILE);}catch(Exception ignored){}
        }
        if(manaSensor != null && !plausibleMonsterSensor(manaSensor)) {
            manaSensor = null; try {Files.deleteIfExists(MANA_FILE);}catch(Exception ignored){}
        }
        // Old learned mappings have produced Actor#x and layout false positives.
        // Keep the file for forensic/recovery purposes, but never trust it in v9.6.
        monsterHpSensor = null;
    }

    private static void rememberMonsterHp(int id, MonsterVitals v, long now) {
        if(v==null)return;
        Double p=v.percentHp;
        if(p!=null&&(!Double.isFinite(p)||p<0||p>100))return;
        MonsterHpMemory old=monsterMemory.get(id);
        // Prevent one bad frame from instantly "healing" a nearly dead target.
        if(old!=null&&p!=null&&old.percent!=null&&now-old.updatedAt<1200L&&p>old.percent+20.0)return;
        monsterMemory.put(id,new MonsterHpMemory(v.currentHp,v.maxHp,p,now));
        if(monsterMemory.size()>512){long cutoff=now-30000L;monsterMemory.entrySet().removeIf(e->e.getValue().updatedAt<cutoff);}
    }

    private static MonsterVitals rememberedVitals(int id,long now,long maxAgeMs){
        MonsterHpMemory m=monsterMemory.get(id);
        if(m==null||now-m.updatedAt>maxAgeMs)return null;
        return new MonsterVitals(m.current,m.max,m.percent,m.updatedAt);
    }

    private static Double rememberedPercent(int id,long now,long maxAgeMs){MonsterVitals v=rememberedVitals(id,now,maxAgeMs);return v==null?null:v.percentHp;}

    private static MonsterVitals vitals(Sensor s,Map<String,Double> metrics,long now){
        if(s==null||metrics==null)return null;
        Double cur=metrics.get(s.currentKey);if(cur==null||!Double.isFinite(cur)||cur<0)return null;
        if("PERCENT".equals(s.maxKey)){
            if(cur>100.0)return null;
            return new MonsterVitals(null,null,Math.max(0,Math.min(100,cur)),now);
        }
        if("RATIO".equals(s.maxKey)){
            if(cur>1.05)return null;
            return new MonsterVitals(null,null,Math.max(0,Math.min(100,cur*100.0)),now);
        }
        Double max=metrics.get(s.maxKey);if(max==null||!Double.isFinite(max)||max<=0||cur>max*1.05)return null;
        return new MonsterVitals(cur,max,Math.max(0,Math.min(100,cur*100.0/max)),now);
    }

    private static boolean disallowedMonsterMetric(String key) {
        if (key == null || key.isBlank()) return true;
        String k = key.toLowerCase(Locale.ROOT);
        // UI layout coordinates and preferred/min sizes fluctuate as panels move or resize.
        // They are not a monster HP source (observed Table#tableMinHeight false candidate in v9.2).
        if (k.startsWith("com.badlogic.gdx.scenes.scene2d.")) return true;
        if (k.startsWith("com.badlogic.gdx.scenes.scene2d.group#")) return true;
        if (k.startsWith(POSITION_CLASS.toLowerCase(Locale.ROOT)+"#") || k.startsWith(IDENTITY_CLASS.toLowerCase(Locale.ROOT)+"#")) return true;
        if (k.startsWith(KNOWN_BAD_MONSTER_SENSOR_PREFIX.toLowerCase(Locale.ROOT))) return true;
        String field=k.substring(k.lastIndexOf('#')+1).replace("()", "");
        return field.matches("(x|y|z|width|height|rotation|scale|scalex|scaley|originx|originy|stage|animationtime|time|elapsed|alpha|color|positionx|positiony|velocityx|velocityy|dx|dy)");
    }

    private static boolean plausibleMonsterSensor(Sensor s) {
        return s != null && !disallowedMonsterMetric(s.currentKey)
               && ("RATIO".equals(s.maxKey) || "PERCENT".equals(s.maxKey)
                   || !disallowedMonsterMetric(s.maxKey));
    }

    private static void invalidateMonsterSensor() {
        monsterHpSensor = null;
        monsterVotes.clear();
        monsterEvidence.clear();
        monsterMemory.clear();
        lastMonsterMetrics.clear();
        lastMonsterSampleAt.clear();
        monsterProbeSummary = "probe: sensor invalidado (nao era HP)";
        try { Files.deleteIfExists(MONSTER_HP_FILE); } catch (Exception ignored) { }
    }

    private static String monsterSamplerStatus() {
        return "samples=" + monsterSamples + " numeric=" + monsterNonemptySamples
                + " fields=" + monsterLastMetricCount + " changed=" + monsterChangedSamples
                + " errors=" + monsterReadErrors;
    }
    static String monsterSamplerStatusForTests() { return monsterSamplerStatus(); }

    private static boolean metricsChanged(Map<String,Double> a, Map<String,Double> b) {
        for (Map.Entry<String,Double> e : b.entrySet()) {
            Double old=a.get(e.getKey());
            if (old!=null && !Objects.equals(old,e.getValue())) return true;
        }
        return false;
    }
    static boolean metricsChangedForTests(Map<String,Double> a, Map<String,Double> b) { return metricsChanged(a,b); }

    private static Sensor chooseDropSensor(Map<String, Double> before, Map<String, Double> after) {
        return chooseDropSensor(before,after,false);
    }
    private static Sensor chooseDropSensor(Map<String, Double> before, Map<String, Double> after, boolean forMonster) {
        if (before == null || after == null) return null;
        String nourishment = null;
        try {
            Path p = STATE.resolve("nourishment_sensor.txt");
            if (Files.exists(p)) nourishment = Files.readString(p, StandardCharsets.UTF_8).trim();
        } catch (Exception ignored) { }
        Sensor best = null; double bestScore = Double.NEGATIVE_INFINITY;
        for (Map.Entry<String, Double> e : after.entrySet()) {
            Double b = before.get(e.getKey());
            if (b == null) continue;
            double a = e.getValue();
            if (!Double.isFinite(a) || !Double.isFinite(b) || b <= 0 || a < 0 || a >= b) continue;
            double drop = b - a;
            if (e.getKey().equals(nourishment)) continue;
            if (disallowedMonsterMetric(e.getKey())) continue;
            if (forMonster && !plausibleMonsterSensor(new Sensor(e.getKey(), "RATIO"))) continue;

            // v8.4: many clients store creature health as a normalized 0..1 ratio.
            // The old learner ignored every change smaller than 1, so such a real HP field
            // could never calibrate. Ratios now get their own strongly-filtered sensor type.
            if (b <= 1.001 && a <= 1.001) {
                if (drop < 0.002) continue;
                double score = 35.0 + Math.min(50.0, drop * 400.0);
                if (score > bestScore) { best = new Sensor(e.getKey(), "RATIO"); bestScore = score; }
                continue;
            }

            if (drop < 0.5) continue;
            String prefix = componentPrefix(e.getKey());
            String maxKey = null; double maxValue = Double.NaN;
            for (Map.Entry<String, Double> x : after.entrySet()) {
                if (x.getKey().equals(e.getKey()) || !componentPrefix(x.getKey()).equals(prefix)) continue;
                Double xb = before.get(x.getKey());
                if (xb == null || Math.abs(xb - x.getValue()) > 0.001) continue;
                double v = x.getValue();
                if (v >= b && v > 0 && (!Double.isFinite(maxValue) || v < maxValue)) {
                    maxKey = x.getKey(); maxValue = v;
                }
            }
            if (maxKey == null && b <= 100.0 && a <= 100.0) maxKey = "PERCENT";
            if (maxKey == null) continue;
            double score = Math.min(200, drop) + ("PERCENT".equals(maxKey) ? 25 : 80);
            if (drop > 0.5 && drop < 10000) score += 10;
            if (score > bestScore) { best = new Sensor(e.getKey(), maxKey); bestScore = score; }
        }
        return best;
    }

    static String chooseMonsterDropKeyForTests(Map<String,Double> before,Map<String,Double> after) {
        Sensor s=chooseDropSensor(before,after,true); return s==null?null:s.currentKey;
    }

    private static Double percent(Sensor s, Map<String, Double> metrics) {
        if (s == null || metrics == null) return null;
        Double cur = metrics.get(s.currentKey); if (cur == null) return null;
        double p;
        if ("PERCENT".equals(s.maxKey)) p = cur;
        else if ("RATIO".equals(s.maxKey)) p = cur * 100.0;
        else { Double max = metrics.get(s.maxKey); if (max == null || max <= 0) return null; p = cur * 100.0 / max; }
        return Math.max(0, Math.min(100, p));
    }

    /** Covers inherited primitive fields that getDeclaredFields() previously skipped.
     * Also probes a small, explicit set of common health-like getter names safely;
     * no arbitrary obfuscated methods are invoked (they may have side effects).
     */
    private static Map<String, Double> sampleMetrics(Object entity) throws Exception {
        Map<String, Double> out = new LinkedHashMap<>();
        if (entity == null) return out;
        for (Object component : componentsOf(entity)) {
            if (component == null) continue;
            Class<?> c = component.getClass();
            for (Field f : fieldsFor(c)) {
                try {
                    Object raw=f.get(component);
                    if (!(raw instanceof Number n)) continue;
                    double v=n.doubleValue();
                    if(Double.isFinite(v)&&v>=0&&v<=1_000_000_000d)
                        out.put(f.getDeclaringClass().getName()+"#"+f.getName(),v);
                } catch (Throwable ignored) { }
            }
            for (Method m : gettersFor(c)) {
                try {
                    Object raw=m.invoke(component);
                    if (!(raw instanceof Number n)) continue;
                    double v=n.doubleValue();
                    if(Double.isFinite(v)&&v>=0&&v<=1_000_000_000d)
                        out.put(m.getDeclaringClass().getName()+"#"+m.getName()+"()",v);
                } catch (Throwable ignored) { }
            }
            // Many ECS clients store numerical stats in a nested value object.
            // Follow at most 8 game-owned scalar containers, one level deep.
            // No network methods, unknown getters or arbitrary object graph traversal.
            int visited=0;
            for(Class<?> t=c;t!=null && t!=Object.class && visited<8;t=t.getSuperclass()){
                for(Field root:t.getDeclaredFields()) {
                    if(visited>=8 || out.size()>=220)break;
                    if(Modifier.isStatic(root.getModifiers()) || root.isSynthetic() || root.getType().isPrimitive())continue;
                    Class<?> rt=root.getType();
                    if(!rt.getName().startsWith("uk.co.harveydogs.mirage.") &&
                       !rt.getName().startsWith("miragelab."))continue;
                    if(rt.isArray()||rt.isEnum()||Collection.class.isAssignableFrom(rt))continue;
                    try{
                        root.setAccessible(true);
                        Object nested=root.get(component);
                        if(nested==null || nested==component)continue;
                        visited++;
                        for(Field child:fieldsFor(nested.getClass())){
                            if(out.size()>=220)break;
                            Object raw=child.get(nested);
                            if(raw instanceof Number n){double v=n.doubleValue();
                                if(Double.isFinite(v)&&v>=0&&v<=1_000_000_000d)
                                    out.put(t.getName()+"#"+root.getName()+"."+child.getName(),v);
                            }
                        }
                    }catch(Throwable ignored){}
                }
            }
        }
        return out;
    }

    private static synchronized List<Field> fieldsFor(Class<?> c) {
        return NUMERIC_FIELDS.computeIfAbsent(c, x -> {
            List<Field> fields = new ArrayList<>();
            for (Class<?> t=x; t!=null && t!=Object.class; t=t.getSuperclass()) {
                for(Field f:t.getDeclaredFields()) {
                    if(Modifier.isStatic(f.getModifiers())||f.isSynthetic()) continue;
                    Class<?> ft=f.getType();
                    if(!(ft.isPrimitive()||Number.class.isAssignableFrom(ft))||ft==boolean.class||ft==char.class) continue;
                    try {f.setAccessible(true);fields.add(f);}catch(Throwable ignored){}
                }
            }
            return fields;
        });
    }

    private static synchronized List<Method> gettersFor(Class<?> c) {
        return NUMERIC_GETTERS.computeIfAbsent(c,x->{
            List<Method> getters=new ArrayList<>();
            for(Method m:x.getMethods()){
                if(m.getParameterCount()!=0||Modifier.isStatic(m.getModifiers()))continue;
                if(!Number.class.isAssignableFrom(m.getReturnType())&&!isNumericPrimitive(m.getReturnType()))continue;
                String name=m.getName().toLowerCase(Locale.ROOT);
                if(!(name.equals("gethealth")||name.equals("gethp")||name.equals("getmaxhealth")
                     ||name.equals("getmaxhp")||name.equals("gethealthpercent")
                     ||name.equals("gethealthpercentage")||name.equals("getcurrenthealth")))continue;
                getters.add(m);
            }
            return getters;
        });
    }
    private static boolean isNumericPrimitive(Class<?> t){return t==int.class||t==long.class||t==short.class||t==byte.class||t==float.class||t==double.class;}

    private static void collectProbeChanges(Map<String,Double> before,Map<String,Double> after,
                                             int id,boolean targeted,long now) {
        for(Map.Entry<String,Double> e:after.entrySet()){
            Double prev=before.get(e.getKey());Double val=e.getValue();
            if(prev==null||val==null||!Double.isFinite(prev)||!Double.isFinite(val)||prev.equals(val))continue;
            String key=e.getKey();
            if(!plausibleMonsterSensor(new Sensor(key,"PERCENT")))continue;
            ProbeChange change=PROBE_CHANGES.computeIfAbsent(key,k->new ProbeChange());
            if(val<prev){change.drops++;if(targeted)change.targetDrops++;}else change.rises++;
            if(targeted)change.targetSamples++;
            change.lastBefore=prev;change.lastAfter=val;change.lastSeenAt=now;
            change.ids.add(id);
        }
        if(PROBE_CHANGES.size()>300){
            PROBE_CHANGES.entrySet().removeIf(e->now-e.getValue().lastSeenAt>120000L);
        }
    }
    private static void dumpProbeSummary(long now) {
        lastProbeDumpAt=now;
        try {
            Files.createDirectories(STATE);
            List<Map.Entry<String,ProbeChange>> entries=new ArrayList<>(PROBE_CHANGES.entrySet());
            entries.sort(Comparator.<Map.Entry<String,ProbeChange>>comparingInt(e->e.getValue().targetDrops*4+e.getValue().drops-e.getValue().rises).reversed());
            StringBuilder b=new StringBuilder("MirageLab v9.2 HP probe (numeric observations, NOT validated HP)\n");
            b.append("Read-only diagnostic. Do not select a key without calibration.\n");
            b.append(monsterSamplerStatus()).append("\n");
            for(int i=0;i<Math.min(PROBE_LIMIT,entries.size());i++){
                var e=entries.get(i);ProbeChange c=e.getValue();
                b.append(e.getKey()).append(" drops=").append(c.drops).append(" targetedDrops=").append(c.targetDrops)
                 .append(" rises=").append(c.rises).append(" mobs=").append(c.ids.size())
                 .append(" last=").append(c.lastBefore).append("->").append(c.lastAfter).append("\n");
            }
            SafeStateStore.write(MONSTER_TRACE,b.toString());
        } catch(Throwable ignored) { }
    }
    static String probeSummaryForTests(){return "fields="+monsterLastMetricCount+" changed="+monsterChangedSamples;}
    static Map<String,Double> sampleMetricsForTests(Object entity) throws Exception{return sampleMetrics(entity);}
    private static final class ProbeChange {
        int drops,rises,targetDrops,targetSamples;
        double lastBefore,lastAfter;
        long lastSeenAt;
        final Set<Integer> ids=new HashSet<>();
    }

    private static List<Object> componentsOf(Object entity) throws Exception {
        List<Object> out = new ArrayList<>();
        Object arr = entity.getClass().getMethod("getComponents").invoke(entity);
        if (arr == null) return out;
        if (arr instanceof Iterable<?> it) { for (Object x : it) out.add(x); return out; }
        try {
            Method size = arr.getClass().getMethod("size"); Method get = arr.getClass().getMethod("get", int.class);
            int n = ((Number) size.invoke(arr)).intValue(); for (int i=0;i<n;i++) out.add(get.invoke(arr,i));
        } catch (Throwable ignored) { }
        return out;
    }

    private static String componentPrefix(String key) { int p = key.lastIndexOf('#'); return p < 0 ? key : key.substring(0,p); }
    private static void persist(Path p, Sensor s) { try { Files.createDirectories(p.getParent()); Files.writeString(p, s.currentKey + "|" + s.maxKey + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING); } catch(Exception ignored){} }
    private static Sensor read(Path p) { try { if(!Files.exists(p)) return null; String[] a=Files.readString(p,StandardCharsets.UTF_8).trim().split("\\|",2); return a.length==2?new Sensor(a[0],a[1]):null; } catch(Exception e){return null;} }
    private static String shortKey(String k){ if(k==null)return "-"; int i=k.lastIndexOf('.'); String s=i>=0?k.substring(i+1):k; return s.length()>40?s.substring(s.length()-40):s; }
    private static String fmt(Double d){ if(d==null)return "?"; return String.format(Locale.ROOT,"%.0f",d); }
    private static final class SensorEvidence {
        int votes;
        int targetedVotes;
        long lastSeenAt;
        final Set<Integer> entityIds = new HashSet<>();
    }
    private record Sensor(String currentKey, String maxKey) {}
    private record MonsterHpMemory(Double current, Double max, Double percent, long updatedAt) {}
    private record Calibration(String kind, Map<String,Double> before, long started, long deadline) {}
}
