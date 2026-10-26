package miragelab;

import java.util.*;
/** Read-only session metrics. Confirmed XP comes from the client's decoded XP gain command. */
final class AnalyzerEngine {
    private static final Map<Integer,Long> corpses = new HashMap<>();
    private static long resetAt = System.currentTimeMillis();
    private static int bodies = 0;
    private static long gainedXp = 0, xpEvents = 0;
    private static volatile Long experienceStart=null, experienceNow=null, skillRemaining=null;
    private static volatile String skillName="Skill";
    private static volatile String source="Aguardando evento XP do cliente";
    private AnalyzerEngine(){}
    static synchronized void reset(){
        resetAt=System.currentTimeMillis();bodies=0;corpses.clear();
        gainedXp=0;xpEvents=0;experienceStart=null;experienceNow=null;skillRemaining=null;
        source="Aguardando evento XP do cliente";
    }
    static synchronized void corpseObserved(int id,long now){
        corpses.entrySet().removeIf(e->now-e.getValue()>180000L);
        if(!corpses.containsKey(id)){corpses.put(id,now);bodies++;}
    }
    static void tick(long now){ /* Progress is fed by validated client events only. */ }
    /** Invoked once per deserialized PLAYER_EXPERIENCE_GAINED command. */
    static synchronized void gainedXpEvent(int delta){
        if(delta<=0 || delta>100_000_000)return; // Reject malformed payloads
        if(gainedXp>Long.MAX_VALUE-delta)return;
        gainedXp+=delta;xpEvents++;
        source="Comando interno ExperienceGainedCommand (v0.8.59)";
    }
    /** Optional total-XP source: retained for test compatibility and future verified mappings. */
    static synchronized void verifiedXp(long xp,String provenance){
        if(xp<0||provenance==null||provenance.isBlank())return;
        if(experienceStart==null||(experienceNow!=null&&xp<experienceNow))experienceStart=xp;
        experienceNow=xp;
        if(xpEvents==0)source=provenance;
    }
    static synchronized void verifiedSkill(String name,long remaining,String provenance){
        if(remaining<0||provenance==null||provenance.isBlank())return;
        skillName=name==null?"Skill":name;skillRemaining=remaining;
    }
    static synchronized Snapshot snapshot(long now){
        double hours=Math.max(1d/3600d,(now-resetAt)/3600000d);
        Long delta = null;
        if(xpEvents>0)delta=Long.valueOf(gainedXp);
        else if(experienceNow!=null && experienceStart!=null)
            delta=Long.valueOf(Math.max(0,experienceNow-experienceStart));
        Double rate=delta==null?null:delta/hours;
        return new Snapshot(bodies,bodies/hours,rate,delta,skillName,skillRemaining,
                source,Math.max(0,now-resetAt),xpEvents);
    }
    record Snapshot(int observedCorpses,double bodiesPerHour,Double xpPerHour,Long gainedXp,
                    String skillName,Long skillRemaining,String source,long elapsedMs,long xpEvents){}
}
