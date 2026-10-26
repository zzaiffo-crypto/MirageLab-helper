package miragelab;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * v9.5: read-only player nourishment telemetry; NO experimental food presses,
 * no previously persisted guessing, and no numeric-field calibration by eating.
 * When the client's attribute is obfuscated, an alternative monotonic 60-second
 * nourishment pattern may establish the field after multiple natural decrements.
 * The module refuses to consume if the field is unknown or ambiguous.
 */
final class AutoFoodSupport {
    private static final long SAMPLE_MS=400L, STALE_MS=2500L;
    private static final long MIN_DECAY_MS=42000L, MAX_DECAY_MS=84000L;
    private static final Map<String,Track> tracked=new HashMap<>();
    private static long nextSampleAt, lastValueAt, firstSeenAt, lastUseAt;
    private static String sensorKey, sensorSource="-", summary="Food: aguardando personagem";
    private static Double nourishment;
    private static int missed, failures, uses;
    private static boolean pending, circuitOpen, previousEnabled;
    private static long pendingAt;
    private static double pendingBefore;

    record Result(String event,String summary,Double nourishment) { }
    private AutoFoodSupport() { }

    static Result tick(Object game,Object playerEntity,boolean enabled,boolean unusedQuiet,
                       int threshold,int cooldownMs,long now){
        return tick(game,playerEntity,enabled,threshold,cooldownMs,"1",now);
    }
    static synchronized Result tick(Object game,Object entity,boolean enabled,int threshold,
                                    int cooldownMs,String hotkey,long now){
        String event=null;
        // Observe the character even with AutoFood OFF so the UI can show the
        // in-game value before any automatic item interaction is enabled.
        if(entity!=null && now>=nextSampleAt){
            nextSampleAt=now+SAMPLE_MS;
            try {
                VitalsAdapter0859.Snapshot direct=VitalsAdapter0859.read(entity);
                if(direct!=null&&direct.nourishment()!=null){
                    java.util.Map<String,Double> single=java.util.Map.of(
                        GameDirectVitals.VITAL_CLASS+"#"+GameDirectVitals.FOOD_RAW,direct.nourishment()*60);
                    ingest(single,now);
                } else ingest(sampleMetrics(entity),now);
            }
            catch(Throwable e){missed++;}
        }
        if(!enabled){
            previousEnabled=false;pending=false;failures=0;circuitOpen=false;
            Double v=value(now);
            summary="Food: desligado"+(v==null?" | nourishment desconhecido":" | nourishment="+fmt(v)+"/60");
            return new Result(null,summary,v);
        }
        if(!previousEnabled){previousEnabled=true;pending=false;failures=0;circuitOpen=false;}
        Double current=value(now);
        if(pending){
            if(current!=null && current>=pendingBefore+0.8){
                pending=false;failures=0;
                event="AUTOFOOD OK: nourishment "+fmt(pendingBefore)+" -> "+fmt(current);
            }else if(now-pendingAt>=3400){
                pending=false;failures++;
                if(failures>=1)circuitOpen=true;
                event="AUTOFOOD SUSPENSO: uso sem aumento confirmado, evitando repetir item";
            }
        }
        // A verified key or decay trend is mandatory. No guessed values, no
        // borrowed hp/mana, no press to 'discover' a sensor or inventory item.
        if(shouldEatPolicy(current,threshold,pending,circuitOpen,now-lastUseAt,cooldownMs)){
            if(validHotkey(hotkey) && InputArbiter.press(game,hotkey,"AutoFood",now)){
                lastUseAt=now;pendingAt=now;pendingBefore=current;pending=true;uses++;
                event="AUTOFOOD: nourishment="+fmt(current)+", tecla "+hotkey+", aguardando confirmacao";
            }
        }
        summary="Food: "+(current==null?"nourishment desconhecido":fmt(current)+"/60")+
                " | sensor="+sensorSource+
                " | usos="+uses+(pending?" | confirmando consumo":"")+
                (circuitOpen?" | BLOQUEADO (sem efeito)":"")+
                (current==null?" | consumo suspenso":"");
        return new Result(event,summary,current);
    }
    static synchronized String summary(){return summary;}
    static synchronized String sensorKeyForTests(){return sensorKey;}
    static synchronized Double value(long now){
        return nourishment!=null && lastValueAt>0 && now-lastValueAt<=STALE_MS?nourishment:null;
    }
    static boolean shouldEatPolicy(Double value,int threshold,boolean waiting,boolean blocked,long sinceLast,int cd){
        return value!=null && Double.isFinite(value) && value>=0 && value<=60 &&
            value<Math.max(1,Math.min(59,threshold)) && !waiting && !blocked &&
            sinceLast>=Math.max(10000,cd);
    }
    static synchronized void resetOnPlayerChange(){tracked.clear();sensorKey=null;nourishment=null;
        sensorSource="novo personagem";lastValueAt=firstSeenAt=0;pending=false;
        nextSampleAt=0;}
    static boolean validHotkey(String key){return key!=null && key.matches("(?:[1-9]|F[1-8])");}
    static synchronized void resetForTests(){tracked.clear();nextSampleAt=lastValueAt=firstSeenAt=lastUseAt=0;
        sensorKey=null;sensorSource="-";summary="Food: aguardando personagem";nourishment=null;
        missed=failures=uses=0;pending=circuitOpen=previousEnabled=false;pendingAt=0;pendingBefore=0;}

    /** Purely observes player-owned fields; NEVER invokes methods on game items. */
    static synchronized void ingest(Map<String,Double> sample,long now){
        if(sample==null || sample.isEmpty()){missed++;if(missed>=5){nourishment=null;sensorKey=null;}return;}
        if(firstSeenAt==0)firstSeenAt=now;
        Double verified=GameDirectVitals.nourishmentFrom(sample);
        if(verified!=null){
            sensorKey=GameDirectVitals.VITAL_CLASS+"#"+GameDirectVitals.FOOD_RAW;
            sensorSource="ECS 0.8.59";nourishment=verified;lastValueAt=now;missed=0;
            return;
        }
        for(Map.Entry<String,Double> e:sample.entrySet()){
            String key=e.getKey();Double boxed=e.getValue();
            if(!safeCandidateKey(key) || boxed==null || !Double.isFinite(boxed) || boxed<0 || boxed>60)continue;
            tracked.computeIfAbsent(key,k->new Track()).observe(boxed,now);
        }
        // Explicit attribute names are strongest evidence, irrespective of obfuscated class.
        List<String> named=new ArrayList<>(), natural=new ArrayList<>();
        for(var e:tracked.entrySet()){
            Track t=e.getValue();
            if(now-t.lastAt>STALE_MS || t.last==null)continue;
            if(namedField(e.getKey()))named.add(e.getKey());
            else if(t.naturalDrops>=2 && t.longestStableMs>=10000)natural.add(e.getKey());
        }
        String selected=null,source="-";
        if(named.size()==1){selected=named.get(0);source="atributo do jogo";}
        else if(named.isEmpty() && natural.size()==1){selected=natural.get(0);source="decaimento observado";}
        if(selected!=null){
            Track t=tracked.get(selected);
            sensorKey=selected;sensorSource=source;nourishment=t.last;lastValueAt=now;missed=0;
        }else{
            // Never preserve an unreliable previously selected sensor once candidates conflict.
            sensorKey=null;nourishment=null;sensorSource=named.size()+natural.size()>1?"ambiguo":"aguardando leitura";
        }
    }
    static boolean namedField(String key){
        if(key==null)return false;
        int sharp=key.lastIndexOf('#');String name=(sharp>=0?key.substring(sharp+1):key).toLowerCase(Locale.ROOT);
        return name.contains("nourish") || name.contains("hunger") || name.contains("satiety") || name.contains("satiation");
    }
    static boolean safeCandidateKey(String key){
        if(key==null || !key.contains("#"))return false;
        String k=key.toLowerCase(Locale.ROOT);
        if(k.contains("scene2d") || k.contains("#x") || k.contains("#y") ||
           k.contains("position") || k.contains("camera") || k.contains("velocity") ||
           k.contains("table") || k.contains("layout") || k.contains("width") || k.contains("height"))return false;
        return true;
    }
    /** Retained for historical test compatibility, not used by the runtime. */
    static String chooseCalibrationSensorForTests(Map<String,Double> before,Map<String,Double> after,Map<String,Integer> falls){
        String best=null;double score=Double.NEGATIVE_INFINITY;
        for(var e:after.entrySet()){
            Double b=before.get(e.getKey());if(b==null)continue;
            double d=e.getValue()-b;if(d<2||e.getValue()<0||e.getValue()>100)continue;
            double s=d*10+falls.getOrDefault(e.getKey(),0)*2;
            if(s>score){best=e.getKey();score=s;}
        }
        return best;
    }
    private static Map<String,Double> sampleMetrics(Object entity)throws Exception{
        Map<String,Double> result=new LinkedHashMap<>();
        Method method=entity.getClass().getMethod("getComponents");Object components=method.invoke(entity);
        if(components==null)return result;
        List<Object> list=new ArrayList<>();
        if(components instanceof Iterable<?> it){for(Object c:it){if(list.size()>=250)break;list.add(c);}}
        else {try{Method sz=components.getClass().getMethod("size");Method get=components.getClass().getMethod("get",int.class);
            int count=Math.min(250,((Number)sz.invoke(components)).intValue());
            for(int i=0;i<count;i++)list.add(get.invoke(components,i));}catch(Exception ignored){return result;}}
        for(Object component:list){if(component==null)continue;
            Class<?> cl=component.getClass();if(!safeCandidateKey(cl.getName()+"#dummy"))continue;
            for(Class<?> c=cl;c!=null&&c!=Object.class;c=c.getSuperclass()){
                for(Field f:c.getDeclaredFields()){
                    if(Modifier.isStatic(f.getModifiers())||f.isSynthetic())continue;
                    Class<?> t=f.getType();if(!(t.isPrimitive()||Number.class.isAssignableFrom(t))||t==boolean.class||t==char.class)continue;
                    String key=c.getName()+"#"+f.getName();if(!safeCandidateKey(key))continue;
                    try{f.setAccessible(true);Object raw=f.get(component);if(raw instanceof Number n){double v=n.doubleValue();
                        if(Double.isFinite(v)&&v>=0&&v<=60)result.put(key,v);}}catch(Throwable ignored){}
                }
            }
        }
        return result;
    }
    private static String fmt(double d){return String.format(Locale.ROOT,"%.0f",d);}
    private static final class Track {
        Double last;long lastAt, lastDropAt, stableSince, longestStableMs;int naturalDrops;
        void observe(double value,long now){
            if(last==null){last=value;lastAt=now;stableSince=now;return;}
            long oldAt=lastAt;
            if(Math.abs(value-last)<0.01){long duration=now-stableSince;if(duration>longestStableMs)longestStableMs=duration;}
            else if(Math.abs(value-last+1.0)<0.05){
                long gap=lastDropAt>0?now-lastDropAt:now-stableSince;
                if(gap>=MIN_DECAY_MS && gap<=MAX_DECAY_MS)naturalDrops++;
                else naturalDrops=0;
                lastDropAt=now;stableSince=now;
            }else{naturalDrops=0;lastDropAt=0;stableSince=now;}
            last=value;lastAt=now;
        }
    }
}
