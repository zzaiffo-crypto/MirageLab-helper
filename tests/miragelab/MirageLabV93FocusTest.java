package miragelab;

import java.util.Random;
import java.nio.file.Files;

/** Deterministic integration/contract tests for the v9.3 focus policy and actual engine. */
public final class MirageLabV93FocusTest {
    private static int checks;
    private static void yes(boolean b,String msg){checks++;if(!b)throw new AssertionError(msg+" #"+checks);}
    public static void main(String[] args) throws Exception {
        Random rng = new Random(932025L);
        for (int n=0; n<14000; n++) {
            int a=1+rng.nextInt(6), b=1+rng.nextInt(6);
            // The core regression: an active target with unknown HP can never be abandoned
            // simply because another monster is closer, even at identical distance.
            yes(!AutoAttackEngine.focusedSwitchForTests(HelperConfig.AttackPriority.LOWEST_HP_PERCENT,
                    a,null,null,Long.MAX_VALUE,b,null,null,Long.MAX_VALUE,true),"unknown pct focus");
            yes(!AutoAttackEngine.focusedSwitchForTests(HelperConfig.AttackPriority.LOWEST_HP_ABSOLUTE,
                    a,null,null,Long.MAX_VALUE,b,null,null,Long.MAX_VALUE,true),"unknown abs focus");
            // Mixed known/unknown cannot generate a spurious switch either.
            yes(!AutoAttackEngine.focusedSwitchForTests(HelperConfig.AttackPriority.LOWEST_HP_PERCENT,
                    a,null,10d,0,b,null,null,0,true),"candidate unknown while near finished");
            yes(!AutoAttackEngine.focusedSwitchForTests(HelperConfig.AttackPriority.LOWEST_HP_PERCENT,
                    a,null,null,0,b,null,20d,0,true),"unknown current: no fresh target grab");
        }
        for (int n=0; n<10000; n++) {
            int cd=1+rng.nextInt(7), nd=1+rng.nextInt(7);
            double cur=37+rng.nextInt(64);
            double cand=1+rng.nextInt(100);
            boolean expected=cur-cand>=8;
            boolean actual=AutoAttackEngine.focusedSwitchForTests(HelperConfig.AttackPriority.LOWEST_HP_PERCENT,
                    cd,null,cur,0,nd,null,cand,0,true);
            yes(actual==expected,"percent switch require 8 points - current="+cur+" cand="+cand);
            yes(!AutoAttackEngine.focusedSwitchForTests(HelperConfig.AttackPriority.LOWEST_HP_PERCENT,
                    cd,null,Math.max(1,cur-15),900,nd,null,cand,0,true),"stale reject");
            yes(!AutoAttackEngine.focusedSwitchForTests(HelperConfig.AttackPriority.LOWEST_HP_PERCENT,
                    cd,null,20d,0,nd,null,1d,0,true),"finishing target stays selected");
            yes(!AutoAttackEngine.focusedSwitchForTests(HelperConfig.AttackPriority.LOWEST_HP_PERCENT,
                    cd,null,cur,0,nd,null,cand,1200,true),"stale candidate reject");
        }
        for (int n=0; n<3000; n++) {
            int d=1+rng.nextInt(7);
            yes(!AutoAttackEngine.focusedSwitchForTests(HelperConfig.AttackPriority.LOWEST_HP_PERCENT,
                    d,null,22d,0,1,null,94d,0,true),"screenshot nearly dead vs fresh nearby");
            yes(!CombatFocusPolicy.keepMissing(4000,3200,500,true),"expired gap");
            yes(CombatFocusPolicy.keepMissing(4000,3600,500,true),"brief render gap");
            yes(!CombatFocusPolicy.keepMissing(4000,3600,500,false),"explicit manual deselection");
        }
        // Absolute health applies exactly the same no-distance-fallback constraint.
        for(int n=0;n<3000;n++){
            double cur=100+rng.nextInt(900),cand=1+rng.nextInt(900);
            boolean expected=cur-cand>=8;
            yes(AutoAttackEngine.focusedSwitchForTests(HelperConfig.AttackPriority.LOWEST_HP_ABSOLUTE,
                    3,cur,null,0,1,cand,null,0,true)==expected,"absolute values");
        }
        // v9.2 field probe was almost tricked by Table layout fields. No UI scene graph
        // measurement is allowed to calibrate the monster HP sensor.
        String[] badUi={"Table#tableMinHeight", "Table#tableMinWidth", "Table#tablePrefHeight",
                "Table#tablePrefWidth", "ProgressBar#value", "Actor#x"};
        for(int i=0;i<1200;i++){
            String b="com.badlogic.gdx.scenes.scene2d.ui."+badUi[i%badUi.length];
            yes(VitalSensorSupport.disallowedMonsterMetricForTests(b),"UI layout must not become HP: "+b);
            yes(!VitalSensorSupport.plausibleMonsterSensorForTests(b,"PERCENT"),"UI layout false positive");
        }
        HelperConfig.Config conf = new HelperConfig.Config();
        yes(conf.attackFocusProtection,"focus enabled by default");
        yes(conf.attackFocusMinGain>=8,"minimum advantage");
        String sample="# MirageLab Helper v9.2\nauto_attack=true\nattack_visual_hp=false\nattack_range=5\noverlay_enabled=false\nparty_members=Guilherme\ncustom_note=retained\n";
        Files.createDirectories(HelperConfig.file().getParent());
        Files.writeString(HelperConfig.file(),sample);
        HelperConfig.invalidate();HelperConfig.ensureCurrentFile();
        HelperConfig.Config migrated=HelperConfig.read();
        yes(migrated.attackFocusProtection,"migrated focus defaults on");
        yes(!migrated.attackVisualHpEnabled,"visual remains off");
        yes(!migrated.overlayEnabled,"overlay respected");
        yes(migrated.attackRange==5,"attack range preserved");
        yes("Guilherme".equals(migrated.partyMembers),"party saved");
        yes(Files.readString(HelperConfig.file()).contains("custom_note=retained"),"unknown settings preserved");
        migrated.attackFocusProtection=false;HelperConfig.write(migrated);
        yes(!HelperConfig.read().attackFocusProtection,"focus can be disabled explicitly");
        System.out.println("V9.3 FOCUS TEST PASS "+checks+" checks");
    }
}
