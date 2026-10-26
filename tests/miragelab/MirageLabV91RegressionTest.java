package miragelab;

import java.util.*;
import java.nio.file.*;
/** Synthetic structural checks for inherited health fields, game-component snapshots and safe unknown-HP fallback. */
public final class MirageLabV91RegressionTest {
    private static int checks;
    private static void yes(boolean ok,String reason){if(!ok)throw new AssertionError(reason);checks++;}
    public static class BaseVitals {public int hp=180,maxHealth=300;}
    public static class DerivedVitals extends BaseVitals {private double energy=0.8;}
    public static class FakeEntity {private final List<Object> parts=List.of(new DerivedVitals());public List<Object> getComponents(){return parts;}}
    public static void main(String[] args)throws Exception{
        Map<String,Double> metrics=VitalSensorSupport.sampleMetricsForTests(new FakeEntity());
        yes(metrics.values().contains(180d),"inherited HP field");
        yes(metrics.values().contains(300d),"inherited max field");
        yes(metrics.values().contains(0.8),"subclass float metric");
        Map<String,Double> before=Map.of("test.Body#hp",180d,"test.Body#maxHp",300d);
        Map<String,Double> after=Map.of("test.Body#hp",140d,"test.Body#maxHp",300d);
        yes("test.Body#hp".equals(VitalSensorSupport.chooseMonsterDropKeyForTests(before,after)),"health candidate key");
        yes(VitalSensorSupport.sensorPercentForTests("test.Body#hp","test.Body#maxHp",after)>46&&VitalSensorSupport.sensorPercentForTests("test.Body#hp","test.Body#maxHp",after)<47,"health percent");
        var cfg=new HelperConfig.Config();cfg.attackVisualHpEnabled=false;cfg.attackPriority=HelperConfig.AttackPriority.LOWEST_HP_PERCENT;
        yes(!cfg.copy().attackVisualHpEnabled,"opt-out persists in copy");
        cfg.attackVisualHpEnabled=true;yes(cfg.copy().attackVisualHpEnabled,"opt-in persists in copy");
        Random random=new Random(20261025L);
        for(int i=0;i<1000;i++){
            int a=1+random.nextInt(10),b=1+random.nextInt(10);
            long now=10000+i;
            boolean should=AutoAttackEngine.shouldSwitchAtForTests(HelperConfig.AttackPriority.LOWEST_HP_PERCENT,
               a,null,b,null,now,now-100,now-100,1800,2,650);
            yes(!should,"unknown HP must not cause oscillation during hold");
        }
        for(int i=0;i<1000;i++){
            double cur=30+random.nextInt(60),candidate=5+random.nextInt(15);
            boolean switchIt=AutoAttackEngine.shouldSwitchAtForTests(HelperConfig.AttackPriority.LOWEST_HP_PERCENT,
               2,cur,4,candidate,12000,11900,11950,1800,2,650);
            yes(switchIt,"real HP advantage should override unknown fallback lock");
        }
        System.out.println("MIRAGELAB V9.1 STRUCTURE + RETARGET PASS "+checks+" checks");
    }
}
