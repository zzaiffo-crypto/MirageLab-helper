package miragelab;

import java.util.*;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;

/**
 * Read-only direct ECS attributes. Deliberately does NOT turn a declining random
 * float into HP%, and never treats coordinates or widget values as vitals.
 * The game/client still needs to expose both current and maximum values.
 */
final class GameDirectVitals {
    static final String CANDIDATE_CURRENT =
        "uk.co.harveydogs.mirage.shared.yY8x1yOsuoav.Xb046TljtoadQTFItzaxrgXLI#eACqBMENic3YHQLgkQTnYXFlcXmJ";
    static final String VITAL_CLASS="uk.co.harveydogs.mirage.shared.yY8x1yOsuoav.Xb046TljtoadQTFItzaxrgXLI";
    static final String HP_CURRENT="eACqBMENic3YHQLgkQTnYXFlcXmJ";
    static final String HP_MAX="FIeR9ZxfSOyX";
    static final String MP_CURRENT="WVfgbdbcCRRmDq1CfPzsI1qtv";
    static final String MP_MAX="RdO6XVVbolTs44OnCNBZTVZS";
    static final String FOOD_RAW="iNCztOvJLBPunDQaaXW1VZF4ggQ";
    private static final String WORLD_METHOD="P17VtM6bkJVXJB65ftLh7dMon";
    private static final String PLAYER_METHOD="eACqBMENic3YHQLgkQTnYXFlcXmJ";
    private static Object cachedPlayer;
    private static Object cachedGame;
    private static long lastPlayerFind, lastPlayerSample;
    private static Reading lastPlayer = Reading.unavailable("aguardando entidade do jogador");
    private static volatile String monsterDetail="dados diretos: aguardando componentes";
    private static int observations, candidateSeen, paired;
    private static long lastMobAuditAt, lastPlayerAuditAt;
    private static final Path MONSTER_AUDIT = Path.of(System.getProperty("user.home"), "Downloads", "MirageLab", "state", "direct_monster_fields_v96.txt");
    private static final Path PLAYER_AUDIT = Path.of(System.getProperty("user.home"), "Downloads", "MirageLab", "state", "direct_player_fields_v96.txt");

    record Reading(Double current, Double max, Double percent, String source, long at) {
        static Reading unavailable(String reason){return new Reading(null,null,null,reason,0);}
        boolean valid(){return current!=null && max!=null && percent!=null &&
            Double.isFinite(percent) && percent>=0 && percent<=100 && max>0 && current>=0 && current<=max;}
    }
    record Player(Reading hp, Reading mana, String status) { }
    static Double nourishmentFrom(Map<String,Double> m){
        if(m==null)return null;
        Double n=m.get(VITAL_CLASS+"#"+FOOD_RAW);
        if(n==null||!Double.isFinite(n)||n<0||n>5000)return null;
        return Math.min(60d,n/60d);
    }
    private static volatile Double lastFood;
    static Double lastNourishment(){return lastFood;}

    private GameDirectVitals() { }
    static synchronized Player player(Object game,long now){
        if(game!=cachedGame || now-lastPlayerFind>=350){
            cachedGame=game;lastPlayerFind=now;Object found=null;
            try {
                Object world=game.getClass().getMethod(WORLD_METHOD).invoke(game);
                if(world!=null)found=world.getClass().getMethod(PLAYER_METHOD).invoke(world);
            } catch(Throwable ignored) { }
            if(found!=cachedPlayer){cachedPlayer=found;lastPlayerSample=0;lastPlayer=Reading.unavailable("personagem alterado");}
        }
        if(cachedPlayer==null) return new Player(Reading.unavailable("jogador nao disponivel"),
                Reading.unavailable("jogador nao disponivel"),"ECS: jogador ausente");
        if(now-lastPlayerSample>=140){
            lastPlayerSample=now;
            try {
                VitalsAdapter0859.Snapshot direct=VitalsAdapter0859.read(cachedPlayer);
                if(direct!=null && direct.hpValid() && direct.manaValid()){
                    lastPlayerHp=new Reading(direct.hp(),direct.maxHp(),100*direct.hp()/direct.maxHp(),direct.source(),now);
                    lastPlayerMana=new Reading(direct.mana(),direct.maxMana(),100*direct.mana()/direct.maxMana(),direct.source(),now);
                    lastFood=direct.nourishment();
                    return new Player(lastPlayerHp,lastPlayerMana,"ECS 0.8.59 tipado, HP="+show(lastPlayerHp)+" MP="+show(lastPlayerMana));
                }
                Map<String,Double> metrics=VitalSensorSupport.sampleMetricsForTests(cachedPlayer);
                lastFood=nourishmentFrom(metrics);
                Reading hp=resolve(metrics,"hp",now);
                Reading mp=resolve(metrics,"mana",now);
                lastPlayerHp=hp;lastPlayerMana=mp;
                audit(metrics,PLAYER_AUDIT,now,false);
            }catch(Throwable ignored){lastPlayerHp=Reading.unavailable("falha de leitura direta");
                                  lastPlayerMana=Reading.unavailable("falha de leitura direta");}
        }
        return new Player(lastPlayerHp,lastPlayerMana,
                "ECS HP="+show(lastPlayerHp)+" MP="+show(lastPlayerMana));
    }
    private static Reading lastPlayerHp=Reading.unavailable("aguardando"),
                           lastPlayerMana=Reading.unavailable("aguardando");
    static synchronized Reading monster(Map<String,Double> m,long now){
        observations++;
        if(m!=null && m.containsKey(CANDIDATE_CURRENT))candidateSeen++;
        if(m!=null && m.containsKey(CANDIDATE_CURRENT))audit(m,MONSTER_AUDIT,now,true);
        Reading direct=resolve(m,"hp",now);
        if(direct.valid()){paired++;monsterDetail="ECS HP confirmado: "+paired+" leituras, candidatos brutos="+candidateSeen;}
        else if(m!=null && m.containsKey(CANDIDATE_CURRENT))
            monsterDetail="ECS HP atual candidato presente; MAX ainda NAO confirmado ("+candidateSeen+" amostras)";
        return direct;
    }
    static String monsterStatus(){return monsterDetail;}
    /** Local, read-only field snapshot; not a guessed HP sensor and not uploaded anywhere. */
    private static void audit(Map<String,Double> all, Path target,long now, boolean monster){
        if(all==null||all.isEmpty())return;
        if(monster){if(now-lastMobAuditAt<5000)return;lastMobAuditAt=now;}
        else {if(now-lastPlayerAuditAt<5000)return;lastPlayerAuditAt=now;}
        try{
            StringBuilder sb=new StringBuilder("MirageLab V10 - atributos ECS brutos, APENAS diagnostico\n");
            sb.append("epoch_ms=").append(now).append("; canal=").append(monster?"monstro":"jogador").append('\n');
            sb.append("Nenhum valor abaixo e HP confirmado sem pareamento atual/max.\n");
            all.entrySet().stream().filter(e->e.getKey()!=null &&
                !disallowed(e.getKey()) && Double.isFinite(e.getValue()))
                .sorted(Map.Entry.comparingByKey()).limit(200)
                .forEach(e->sb.append(e.getKey()).append('=').append(e.getValue()).append('\n'));
            SafeStateStore.write(target,sb.toString());
        }catch(Throwable ignored){}
    }
    static String show(Reading r){return r!=null&&r.valid()?String.format(Locale.ROOT,"%.0f/%.0f (%.0f%%)",r.current,r.max,r.percent):"?";}
    /** Only semantically named current/max pairs are trusted; never infer max from damage. */
    static Reading resolve(Map<String,Double> m,String vital,long now){
        if(m==null || m.isEmpty())return Reading.unavailable("ECS sem atributos");
        // V10: verified fields in the exact component present in the supplied v0.8.59 client JAR.
        String currentField="mana".equals(vital)?MP_CURRENT:HP_CURRENT;
        String maximumField="mana".equals(vital)?MP_MAX:HP_MAX;
        Double exactCurrent=m.get(VITAL_CLASS+"#"+currentField);
        Double exactMax=m.get(VITAL_CLASS+"#"+maximumField);
        if(exactCurrent!=null || exactMax!=null){
            if(exactCurrent!=null && exactMax!=null && Double.isFinite(exactCurrent)
               && Double.isFinite(exactMax) && exactMax>0 && exactCurrent>=0 && exactCurrent<=exactMax)
                return new Reading(exactCurrent,exactMax,100*exactCurrent/exactMax,"ECS 0.8.59",now);
            return Reading.unavailable("atributos ECS inconsistentes");
        }
        ArrayList<String> currents=new ArrayList<>(), maxima=new ArrayList<>();
        for(String key:m.keySet()){
            if(key==null||disallowed(key))continue;
            String n=fieldName(key);
            if(isCurrent(n,vital))currents.add(key);
            if(isMaximum(n,vital))maxima.add(key);
        }
        Reading match=null;int matches=0;
        for(String ck:currents){
            Double c=m.get(ck);
            if(c==null||!Double.isFinite(c)||c<0)continue;
            for(String mk:maxima){
                Double mx=m.get(mk);
                if(mx==null||!Double.isFinite(mx)||mx<=0||c>mx)continue;
                if(!classKey(ck).equals(classKey(mk)))continue;
                matches++;match=new Reading(c,mx,100*c/mx,"ECS: "+fieldName(ck),now);
            }
        }
        return matches==1?match:Reading.unavailable(matches>1?"atributos ambiguos":"HP/MP maximo nao identificado");
    }
    private static boolean disallowed(String key){
        String k=key.toLowerCase(Locale.ROOT);
        return k.contains("scene2d")||k.contains("position")||k.contains("camera")||
               k.contains("velocity")||k.contains("#x")||k.contains("#y");
    }
    private static String classKey(String k){int p=k.indexOf('#');return p<0?k:k.substring(0,p);}
    private static String fieldName(String k){int p=k.lastIndexOf('#');
        String s=p<0?k:k.substring(p+1);int nesting=s.lastIndexOf('.');
        if(nesting>=0)s=s.substring(nesting+1);return s.replace("()","").toLowerCase(Locale.ROOT).replace("_","");}
    private static boolean isCurrent(String n,String v){
        if("mana".equals(v))return Set.of("mana","currentmana","getmana","getcurrentmana","mp","currentmp","getmp").contains(n);
        return Set.of("hp","health","currenthp","currenthealth","gethp","gethealth","getcurrenthp","getcurrenthealth","hitpoints").contains(n);
    }
    private static boolean isMaximum(String n,String v){
        if("mana".equals(v))return Set.of("maxmana","manamax","getmaxmana","maxmp","getmaxmp","maximummana").contains(n);
        return Set.of("maxhp","hpmax","maxhealth","healthmax","getmaxhp","getmaxhealth","maximumhealth","maximumhp","maxhitpoints").contains(n);
    }
    static synchronized Player playerStatus(){return new Player(lastPlayerHp,lastPlayerMana,
                "ECS HP="+show(lastPlayerHp)+" MP="+show(lastPlayerMana));}
    static synchronized void resetForTests(){observations=candidateSeen=paired=0;
        monsterDetail="dados diretos: aguardando componentes";cachedPlayer=null;cachedGame=null;
        lastPlayerFind=lastPlayerSample=lastMobAuditAt=lastPlayerAuditAt=0;lastPlayerHp=Reading.unavailable("aguardando");lastPlayerMana=Reading.unavailable("aguardando");lastFood=null;}
}
