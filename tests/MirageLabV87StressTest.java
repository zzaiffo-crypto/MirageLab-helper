package miragelab;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Repeatable v8.7 regression/stress simulation with a seeded pseudo-random world. */
public final class MirageLabV87StressTest {
    private static int assertions=0;
    static void check(boolean ok,String why){ if(!ok)throw new AssertionError("FAIL " + why + " @"+ assertions); assertions++; }
    public static void main(String[] args)throws Exception {
        Random rng=new Random(87001337L);
        // 400 random ticks in no-HP fallback: no retarget before dwell, even if closer.
        for(int i=0;i<400;i++) {
            int dCur=3+rng.nextInt(7),dNew=1+rng.nextInt(2);
            long now=1_000_000L+rng.nextInt(1700);
            check(!AutoAttackEngine.shouldSwitchAtForTests(HelperConfig.AttackPriority.LOWEST_HP_PERCENT,
                  dCur,null,dNew,null,now,1_000_000L,0L,1800,2,650),"unknown short dwell "+i);
        }
        // 400 test cases after dwell: must require a >=2 distance advantage (even if original had more HP unknown).
        for(int i=0;i<400;i++) {
            int a=1+rng.nextInt(12),b=1+rng.nextInt(12);
            boolean expected=b+2<=a;
            check(AutoAttackEngine.shouldSwitchAtForTests(HelperConfig.AttackPriority.LOWEST_HP_PERCENT,
                  a,null,b,null,10000,0,0,1800,2,650)==expected,"unknown distance margin "+i);
        }
        // 300 cooldown checks in unknown-HP mode: prevent ping-pong after switch.
        for(int i=0;i<300;i++) {
            long elapsed=rng.nextInt(650);
            check(!AutoAttackEngine.shouldSwitchAtForTests(HelperConfig.AttackPriority.LOWEST_HP_PERCENT,
                  8,null,2,null,10_000L,0,10_000L-elapsed,1800,2,650),"unknown cooldown "+i);
        }
        // 300 verified %-HP cases must bypass dwell and cooldown when better by at least 1 point.
        for(int i=0;i<300;i++) {
            double higher=20+rng.nextInt(78),lower=1+rng.nextInt(15);
            check(AutoAttackEngine.shouldSwitchAtDetailedForTests(HelperConfig.AttackPriority.LOWEST_HP_PERCENT,
                  1,null,higher,9,null,lower,10000,9900,9999),"verified hp % override "+i);
        }
        // 300 verified absolute cases must also bypass dwell and cooldown.
        for(int i=0;i<300;i++) {
            double higher=120+rng.nextInt(600),lower=1+rng.nextInt(75);
            check(AutoAttackEngine.shouldSwitchAtDetailedForTests(HelperConfig.AttackPriority.LOWEST_HP_ABSOLUTE,
                  1,higher,80d,9,lower,55d,10000,9900,9999),"verified hp abs override "+i);
        }
        // 300 known-versus-unknown: do NOT abandon a measured target for an unmeasured one.
        for(int i=0;i<300;i++) {
            double measured=2+rng.nextInt(90);
            check(!AutoAttackEngine.shouldSwitchAtDetailedForTests(HelperConfig.AttackPriority.LOWEST_HP_PERCENT,
                  10,null,measured,1,null,null,10000,0,0),"do not lose measured priority "+i);
        }
        // 300 valid exclusions: exact canonical name, flexible whitespace and case.
        for(int i=0;i<300;i++) {
            String x="Butterfly"+i;
            check(AutoAttackEngine.ignoredByNameForTests(" "+x.toUpperCase(Locale.ROOT)+" ","troll; "+x+" ,rat"),"exact blacklist "+i);
        }
        // 300 near-miss exclusions: no substring matching or partial-name collateral damage.
        for(int i=0;i<300;i++) {
            check(!AutoAttackEngine.ignoredByNameForTests("Butterfly Hunter"+i,"Butterfly"),"no substring blacklist "+i);
        }
        // 300 metric change probes: same map means no event, changed key detected.
        for(int i=0;i<300;i++) {
            Map<String,Double> a=Map.of("x",(double)i,"y",25d);
            check(!VitalSensorSupport.metricsChangedForTests(a,a),"unchanged metrics "+i);
            check(VitalSensorSupport.metricsChangedForTests(a,Map.of("x",(double)i,"y",24d)),"changed metrics "+i);
        }
        // 300 bogus movement candidates should not suppress valid health field selection.
        String bad="uk.co.harveydogs.mirage.client.GEcHnxVxWL24.yY8x1yOsuoav.yY8x1yOsuoav.gauUiPW91yfVcAHjQEvjcCLZ#field";
        for(int i=0;i<300;i++) {
            Map<String,Double> before=Map.of(bad,150d,"creature.Vitals#hp",100d,"creature.Vitals#max",100d);
            Map<String,Double> after=Map.of(bad,20d,"creature.Vitals#hp",70d,"creature.Vitals#max",100d);
            check("creature.Vitals#hp".equals(VitalSensorSupport.chooseMonsterDropKeyForTests(before,after)),"reject movement sensor "+i);
        }
        // 300 random comparisons (ordering transitivity sanity checks for all four priority modes).
        for(int i=0;i<300;i++) {
            int da=1+rng.nextInt(20),db=1+rng.nextInt(20);
            double ha=1+rng.nextInt(90),hb=1+rng.nextInt(90);
            var x=new TargetPriority.Candidate(1,da,ha,ha,false);
            var y=new TargetPriority.Candidate(2,db,hb,hb,false);
            for(var p:HelperConfig.AttackPriority.values()){
                int ab=AutoAttackEngine.compareForTests(p,x,y,false);
                int ba=AutoAttackEngine.compareForTests(p,y,x,false);
                check(Integer.signum(ab)==-Integer.signum(ba),"comparator anti-symmetry "+i+" "+p);
            }
        }
        // 250 config roundtrip cycles: no drop of new options or old settings.
        Path cfg=HelperConfig.file();Files.createDirectories(cfg.getParent());
        for(int i=0;i<250;i++){
            HelperConfig.Config c=new HelperConfig.Config();
            c.attackUnknownHpHoldMs=200+i;c.attackUnknownHpDistanceGain=1+i%10;
            c.attackSwitchCooldownMs=100+i;c.attackIgnoreNames="Butterfly"+i+", Rat";
            c.partyMembers="Amigo"+i;c.autoAttackEnabled=i%2==0;c.attackPriority=i%2==0?
                 HelperConfig.AttackPriority.LOWEST_HP_PERCENT:HelperConfig.AttackPriority.LOWEST_HP_ABSOLUTE;
            c.overlayEnabled=i%3==0;c.autoHealEnabled=i%4==0;
            HelperConfig.write(c);
            HelperConfig.Config got=HelperConfig.read();
            check(got.attackUnknownHpHoldMs==c.attackUnknownHpHoldMs,"config hold "+i);
            check(got.attackUnknownHpDistanceGain==c.attackUnknownHpDistanceGain,"config dist "+i);
            check(got.attackSwitchCooldownMs==c.attackSwitchCooldownMs,"config cooldown "+i);
            check(got.attackIgnoreNames.equals(c.attackIgnoreNames),"config filter "+i);
            check(got.partyMembers.equals(c.partyMembers),"config party "+i);
            check(got.overlayEnabled==c.overlayEnabled,"config overlay "+i);
            check(got.autoHealEnabled==c.autoHealEnabled,"config healing "+i);
        }
        // Final v8.7 migration must never force overlay on when user explicitly saved it off.
        HelperConfig.Config off=new HelperConfig.Config();off.overlayEnabled=false;
        HelperConfig.write(off);HelperConfig.ensureCurrentFile();
        check(!HelperConfig.read().overlayEnabled,"v8.7 respects overlay choice");
        check(Files.readString(cfg,StandardCharsets.UTF_8).contains("# MirageLab Helper v10.0"),"v8.7 version marker");
        System.out.println("v8.7 stress simulation PASS: "+assertions+"/"+assertions+" assertions");
    }
}
