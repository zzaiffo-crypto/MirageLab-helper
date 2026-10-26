package miragelab;

import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** High-volume deterministic simulations for v8.4 targeting + party-aware Anti-KS. */
public final class MirageLabV84HardeningTest {
    static int checks=0;
    public static void main(String[] args) throws Exception {
        Random r = new Random(0x84A11CE5L);

        // 3,000 LOWEST_HP_PERCENT rankings. Lower known HP must win even if farther/non-hostile.
        for (int i=0;i<3000;i++) {
            double aHp=1+r.nextInt(99), bHp=1+r.nextInt(99);
            if (Math.abs(aHp-bHp)<0.01) bHp=Math.min(100,bHp+1);
            int ad=1+r.nextInt(7), bd=1+r.nextInt(7);
            var a=new TargetPriority.Candidate(1,ad,null,aHp,r.nextBoolean());
            var b=new TargetPriority.Candidate(2,bd,null,bHp,r.nextBoolean());
            int cmp=AutoAttackEngine.compareForTests(HelperConfig.AttackPriority.LOWEST_HP_PERCENT,a,b,true);
            check((aHp<bHp && cmp<0)||(aHp>bHp && cmp>0),"hp criterion "+i);
        }

        // 1,500 NEAREST rankings. Distance is primary, HP/aggressor only tie-break.
        for (int i=0;i<1500;i++) {
            int ad=1+r.nextInt(7), bd=1+r.nextInt(7);
            if(ad==bd){bd=bd==7?6:bd+1;}
            var a=new TargetPriority.Candidate(1,ad,null,(double)(1+r.nextInt(99)),r.nextBoolean());
            var b=new TargetPriority.Candidate(2,bd,null,(double)(1+r.nextInt(99)),r.nextBoolean());
            int cmp=AutoAttackEngine.compareForTests(HelperConfig.AttackPriority.NEAREST,a,b,true);
            check((ad<bd && cmp<0)||(ad>bd && cmp>0),"nearest criterion "+i);
        }

        // 1,500 FARTHEST rankings.
        for (int i=0;i<1500;i++) {
            int ad=1+r.nextInt(7), bd=1+r.nextInt(7);
            if(ad==bd){bd=bd==7?6:bd+1;}
            var a=new TargetPriority.Candidate(1,ad,null,(double)(1+r.nextInt(99)),r.nextBoolean());
            var b=new TargetPriority.Candidate(2,bd,null,(double)(1+r.nextInt(99)),r.nextBoolean());
            int cmp=AutoAttackEngine.compareForTests(HelperConfig.AttackPriority.FARTHEST,a,b,true);
            check((ad>bd && cmp<0)||(ad<bd && cmp>0),"farthest criterion "+i);
        }

        // 1,000 Party-aware Anti-KS cases.
        for (int i=0;i<1000;i++) {
            boolean anti=r.nextBoolean(), party=r.nextBoolean(), hostile=r.nextBoolean(), self=r.nextBoolean();
            boolean expected=anti && !party && !(self && hostile);
            boolean got=AutoAttackEngine.partyClaimBlocksForTests(anti,party,hostile,self);
            check(got==expected,"party antiks "+i);
        }

        // 600 manual party matching checks, case/whitespace/separators.
        String list="Winny, ElyZto;  Ally Three";
        for(int i=0;i<200;i++){
            check(PartySupport.manualNameMatchForTests(i%2==0?"WINNY":" winny ",list),"manual winny "+i);
            check(PartySupport.manualNameMatchForTests("elyzto",list),"manual elyzto "+i);
            check(!PartySupport.manualNameMatchForTests("Stranger"+i,list),"manual stranger "+i);
        }

        // 400 conservative marker checks: explicit party markers yes, negative/noise no.
        String[] yes={"PARTY","PARTY_MEMBER","IN_PARTY","TEAMMATE","GROUP_MEMBER","foo.PARTY","PARTY_ALLY"};
        String[] no={"NONE","NOT_PARTY","NO_PARTY","PARTY_NONE","PARTICLE","THIRD_PARTY_LIB","ALLY_DAMAGE?"};
        for(int i=0;i<200;i++){
            check(PartySupport.markerTextForTests(yes[i%yes.length]),"marker yes "+i);
            check(!PartySupport.markerTextForTests(no[i%no.length]),"marker no "+i);
        }

        // 500 HP confidence checks: stale adds only a small penalty; a clearly wounded stale mob still wins.
        for(int i=0;i<250;i++){
            double low=5+r.nextInt(20), high=low+10+r.nextInt(30);
            double stale=AutoAttackEngine.hpRankPercentForTests(low,5000);
            double fresh=AutoAttackEngine.hpRankPercentForTests(high,50);
            check(stale<fresh,"stale wounded still wins "+i);
            check(AutoAttackEngine.hpRankPercentForTests(low,5000)>AutoAttackEngine.hpRankPercentForTests(low,50),"stale penalty "+i);
        }

        // Config persistence and safer default Anti-KS behavior.
        Path state=Path.of(System.getProperty("user.home"),"Downloads","MirageLab","state");Files.createDirectories(state);Path cfg=state.resolve("helper_config.txt");
        Files.writeString(cfg,"# MirageLab Helper v8.3\nauto_attack=true\nattack_priority=LOWEST_HP_PERCENT\nattack_scan_interval_ms=20\nattack_retarget_interval_ms=40\nattack_decision_confirm_ms=60\nattack_self_defense_override=true\nparty_members=Winny,ElyZto\n",StandardCharsets.UTF_8);
        HelperConfig.invalidate();HelperConfig.ensureCurrentFile();HelperConfig.Config c=HelperConfig.read();
        check(c.attackScanIntervalMs==16,"migration scan16");
        check(c.attackRetargetIntervalMs==25,"migration retarget25");
        check(c.attackDecisionConfirmMs==25,"migration confirm25");
        check(!c.attackSelfDefenseOverride,"migration strict antiks");
        check(c.partyMembers.contains("Winny"),"party persists");
        check(c.partyAutoDetect,"party auto default");

        System.out.println("ALL PASS "+checks+"/"+checks);
    }
    static void check(boolean ok,String n){if(!ok)throw new AssertionError("FAIL "+n);checks++;}
}
