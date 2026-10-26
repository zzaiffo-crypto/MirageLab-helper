package miragelab;

import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

public final class MirageLabV84SelfTest {
    static int checks=0;
    public static void main(String[] args) throws Exception {
        // 70 NEAREST ranking/switch checks.
        for(int cur=2;cur<=8;cur++) for(int cand=1;cand<=10;cand++){
            boolean expected=cand+1<=cur;
            boolean got=AutoAttackEngine.shouldSwitchForTests(HelperConfig.AttackPriority.NEAREST,cur,50d,false,cand,50d,false,1,1,false);
            check(got==expected,"nearest "+cur+"->"+cand);
        }
        // 70 FARTHEST ranking/switch checks.
        for(int cur=2;cur<=8;cur++) for(int cand=1;cand<=10;cand++){
            boolean expected=cand>=cur+1;
            boolean got=AutoAttackEngine.shouldSwitchForTests(HelperConfig.AttackPriority.FARTHEST,cur,50d,false,cand,50d,false,1,1,false);
            check(got==expected,"farthest "+cur+"->"+cand);
        }
        // 100 LOWEST_HP_PERCENT comparisons, including distance inversion.
        for(int cur=10;cur<=100;cur+=10) for(int cand=5;cand<=95;cand+=10){
            boolean expected=cand+1<=cur;
            boolean got=AutoAttackEngine.shouldSwitchForTests(HelperConfig.AttackPriority.LOWEST_HP_PERCENT,1,(double)cur,false,6,(double)cand,false,1,1,false);
            check(got==expected,"hp% "+cur+"->"+cand);
        }
        // 50 absolute HP comparisons.
        for(int cur=100;cur<=500;cur+=100) for(int cand=50;cand<=500;cand+=50){
            boolean expected=cand+1<=cur;
            boolean got=AutoAttackEngine.shouldSwitchDetailedForTests(HelperConfig.AttackPriority.LOWEST_HP_ABSOLUTE,1,(double)cur,80d,6,(double)cand,10d,1);
            check(got==expected,"hpabs "+cur+"->"+cand);
        }
        // 24 normalized-ratio sensor checks. Old versions could never learn drops < 1.
        for(int i=1;i<=12;i++){
            double before=1.0-(i-1)*0.02, after=before-0.01;
            Map<String,Double>b=new LinkedHashMap<>(),a=new LinkedHashMap<>();
            b.put("game.Health#ratio",before);a.put("game.Health#ratio",after);
            b.put("game.Pos#x",500d+i);a.put("game.Pos#x",501d+i);
            check("RATIO".equals(VitalSensorSupport.chooseDropModeForTests(b,a)),"ratio mode "+i);
            Double pct=VitalSensorSupport.sensorPercentForTests("game.Health#ratio","RATIO",a);
            check(pct!=null&&Math.abs(pct-after*100.0)<0.001,"ratio percent "+i);
        }
        // 20 finish-lock checks: a lower-HP candidate must beat current finish lock.
        for(int cur=20;cur<=38;cur+=2){
            double cand=Math.max(1,cur-5);
            check(!AutoAttackEngine.finishBlocksForTests(HelperConfig.AttackPriority.LOWEST_HP_PERCENT,null,(double)cur,null,cand,1,35),"finish allows lower "+cur);
            check(AutoAttackEngine.finishBlocksForTests(HelperConfig.AttackPriority.LOWEST_HP_PERCENT,null,(double)Math.min(cur,35),null,(double)Math.min(99,cur+5),1,35),"finish stabilizes worse "+cur);
        }
        // 20 claim timing checks.
        for(int t=0;t<10;t++){
            long first=1000,now=1000+t*30L;
            check(AutoAttackEngine.claimWouldConfirmForTests(first,now,220)==(now-first>=220),"claim confirm "+t);
            check(!AutoAttackEngine.claimBlocksForTests(first,now,now+500,now,true,true),"self defense override "+t);
        }
        // 20 configured ranking checks: selected criterion must beat aggressor bias.
        for(int i=0;i<10;i++){
            var hostile=new TargetPriority.Candidate(1,2,500d,80d,true);
            var weak=new TargetPriority.Candidate(2,5,100d,10d,false);
            check(AutoAttackEngine.compareForTests(HelperConfig.AttackPriority.LOWEST_HP_PERCENT,weak,hostile,true)<0,"criterion hp primary "+i);
            var near=new TargetPriority.Candidate(3,1,900d,90d,false);
            check(AutoAttackEngine.compareForTests(HelperConfig.AttackPriority.NEAREST,near,hostile,true)<0,"criterion near primary "+i);
        }
        // Config migration/persistence: v8.4 -> v8.4 and collapsed icon/Anti-KS fields.
        Path state=Path.of(System.getProperty("user.home"),"Downloads","MirageLab","state");Files.createDirectories(state);Path cfg=state.resolve("helper_config.txt");
        Files.writeString(cfg,"# MirageLab Helper v8.3\nauto_attack=true\nattack_priority=LOWEST_HP_PERCENT\nattack_retarget_interval_ms=40\nattack_decision_confirm_ms=60\noverlay_enabled=true\noverlay_collapsed=true\nattack_anti_ks=false\n",StandardCharsets.UTF_8);
        HelperConfig.invalidate();HelperConfig.ensureCurrentFile();HelperConfig.Config c=HelperConfig.read();
        check(c.attackRetargetIntervalMs==25,"migration retarget 25");
        check(c.attackDecisionConfirmMs==25,"migration confirm 25");
        check(c.overlayCollapsed,"collapsed persists");
        check(!c.attackAntiKsEnabled,"attack antiks persists");
        check(!c.attackSelfDefenseOverride,"strict external claim default");
        check(Files.readString(cfg).contains("# MirageLab Helper v10.0"),"v84 header");
        // 20 unknown/known HP ranking checks.
        for(int i=0;i<20;i++){
            Integer id=AutoAttackEngine.chooseForTests(HelperConfig.AttackPriority.LOWEST_HP_PERCENT,true,
                new TargetPriority.Candidate(1,1,null,null,true),new TargetPriority.Candidate(2,6,500d,50d,false));
            check(id==2,"known hp beats unknown "+i);
        }
        System.out.println("ALL PASS "+checks+"/"+checks);
    }
    static void check(boolean ok,String name){if(!ok)throw new AssertionError("FAIL "+name);checks++;}
}
