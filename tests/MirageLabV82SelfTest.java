package miragelab;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class MirageLabV82SelfTest {
    public static void main(String[] args) throws Exception {
        int pass=0;
        require(AutoAttackEngine.chooseForTests(HelperConfig.AttackPriority.LOWEST_HP_PERCENT,true,
                new TargetPriority.Candidate(1,2,70d,true),new TargetPriority.Candidate(2,5,20d,false))==2,"lowest-hp-is-primary");pass++;
        require(AutoAttackEngine.chooseForTests(HelperConfig.AttackPriority.NEAREST,true,
                new TargetPriority.Candidate(1,2,80d,false),new TargetPriority.Candidate(2,4,20d,true))==1,"nearest-is-primary");pass++;
        require(AutoAttackEngine.chooseForTests(HelperConfig.AttackPriority.FARTHEST,true,
                new TargetPriority.Candidate(1,7,80d,false),new TargetPriority.Candidate(2,3,20d,true))==1,"farthest-is-primary");pass++;
        require(AutoAttackEngine.chooseForTests(HelperConfig.AttackPriority.LOWEST_HP_PERCENT,true,
                new TargetPriority.Candidate(1,2,0d,false),new TargetPriority.Candidate(2,5,45d,false))==2,"zero-hp-does-not-win");pass++;
        require(AutoAttackEngine.chooseForTests(HelperConfig.AttackPriority.LOWEST_HP_PERCENT,false,
                new TargetPriority.Candidate(1,2,null,false),new TargetPriority.Candidate(2,5,45d,false))==2,"known-hp-beats-unknown");pass++;
        require(AutoAttackEngine.shouldSwitchForTests(HelperConfig.AttackPriority.LOWEST_HP_PERCENT,2,51d,false,4,49d,false,1,1,true),"lowest-hp-fast-switch");pass++;
        require(!AutoAttackEngine.shouldSwitchForTests(HelperConfig.AttackPriority.LOWEST_HP_PERCENT,2,49d,false,4,49d,false,1,1,true),"lowest-hp-no-equal-jitter");pass++;
        require(!AutoAttackEngine.shouldSwitchForTests(HelperConfig.AttackPriority.NEAREST,2,80d,false,5,10d,true,1,1,true),"aggressor-no-longer-overrides-nearest");pass++;

        Path state=Path.of(System.getProperty("user.home"),"Downloads","MirageLab","state");Files.createDirectories(state);
        Path cfg=state.resolve("helper_config.txt");
        Files.writeString(cfg,"# MirageLab Helper v8.1\nauto_attack=true\nattack_range=7\nattack_priority=LOWEST_HP\nattack_retarget=true\nattack_retarget_interval_ms=120\nattack_switch_distance_gain=1\nattack_switch_hp_gain=8\nmanual_target_lock_ms=1200\nprefer_aggressor=true\noverlay_enabled=true\n",StandardCharsets.UTF_8);
        HelperConfig.invalidate();HelperConfig.ensureCurrentFile();HelperConfig.Config c=HelperConfig.read();
        require(c.attackScanIntervalMs==20,"migration-adds-fast-scan");pass++;
        require(c.attackRetargetIntervalMs==40,"migration-speeds-retarget");pass++;
        require(c.attackSwitchHpGain==1,"migration-reduces-hp-hysteresis");pass++;
        String raw=Files.readString(cfg,StandardCharsets.UTF_8);require(raw.contains("# MirageLab Helper v10.0"),"migration-version");pass++;
        require(raw.contains("attack_scan_interval_ms=20"),"scan-persisted");pass++;
        System.out.println("ALL PASS "+pass+"/"+pass);
    }
    private static void require(boolean b,String n){if(!b)throw new AssertionError("FAIL "+n);System.out.println("PASS "+n);}
}
