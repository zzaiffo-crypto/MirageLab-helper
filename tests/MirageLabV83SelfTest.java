package miragelab;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

public final class MirageLabV83SelfTest {
    public static void main(String[] args) throws Exception {
        int pass=0;

        // Same two mobs, opposite answers depending on definition of "lowest HP".
        var a=new TargetPriority.Candidate(1,2,80d,80d,false);   // 80/100 => 80%
        var b=new TargetPriority.Candidate(2,3,120d,12d,false); // 120/1000 => 12%
        require(TargetPriority.choose(HelperConfig.AttackPriority.LOWEST_HP_ABSOLUTE,List.of(a,b))==1,"absolute-picks-80-over-120");pass++;
        require(TargetPriority.choose(HelperConfig.AttackPriority.LOWEST_HP_PERCENT,List.of(a,b))==2,"percent-picks-12pct");pass++;

        require(AutoAttackEngine.shouldSwitchDetailedForTests(HelperConfig.AttackPriority.LOWEST_HP_ABSOLUTE,2,200d,20d,4,150d,75d,1),"absolute-switch-200-to-150");pass++;
        require(!AutoAttackEngine.shouldSwitchDetailedForTests(HelperConfig.AttackPriority.LOWEST_HP_PERCENT,2,200d,20d,4,150d,75d,1),"percent-keeps-20pct");pass++;
        require(AutoAttackEngine.shouldSwitchDetailedForTests(HelperConfig.AttackPriority.LOWEST_HP_PERCENT,2,200d,80d,4,900d,15d,1),"percent-switches-to-15pct");pass++;
        require(!AutoAttackEngine.shouldSwitchDetailedForTests(HelperConfig.AttackPriority.LOWEST_HP_ABSOLUTE,2,200d,80d,4,900d,15d,1),"absolute-keeps-200-over-900");pass++;
        require(AutoAttackEngine.finishProtectedForTests(25d,true,35),"finish-lock-protects-25pct");pass++;
        require(!AutoAttackEngine.finishProtectedForTests(45d,true,35),"finish-lock-releases-45pct");pass++;
        require(!AutoAttackEngine.finishProtectedForTests(25d,false,35),"finish-lock-configurable");pass++;
        require(AutoAttackEngine.chooseForTests(HelperConfig.AttackPriority.LOWEST_HP_PERCENT,true,
                new TargetPriority.Candidate(1,2,500d,65d,true),new TargetPriority.Candidate(2,4,300d,20d,false))==2,"lowest-percent-beats-aggressor");pass++;

        // Unknown HP must rank behind known HP, not silently win because it is closer.
        require(TargetPriority.choose(HelperConfig.AttackPriority.LOWEST_HP_ABSOLUTE,List.of(
                new TargetPriority.Candidate(1,1,null,null,false),new TargetPriority.Candidate(2,5,300d,60d,false)))==2,"absolute-known-beats-unknown");pass++;
        require(TargetPriority.choose(HelperConfig.AttackPriority.LOWEST_HP_PERCENT,List.of(
                new TargetPriority.Candidate(1,1,null,null,false),new TargetPriority.Candidate(2,5,300d,60d,false)))==2,"percent-known-beats-unknown");pass++;
        require(TargetPriority.choose(HelperConfig.AttackPriority.LOWEST_HP_PERCENT,List.of(
                new TargetPriority.Candidate(1,1,null,0d,false),new TargetPriority.Candidate(2,5,300d,60d,false)))==2,"zero-percent-does-not-win");pass++;

        // The movement/interpolation component that polluted old HP calibration is explicitly rejected.
        String bad="uk.co.harveydogs.mirage.client.GEcHnxVxWL24.yY8x1yOsuoav.yY8x1yOsuoav.gauUiPW91yfVcAHjQEvjcCLZ#FIeR9ZxfSOyX";
        require(!VitalSensorSupport.plausibleMonsterSensorForTests(bad,"PERCENT"),"known-bad-hp-component-rejected");pass++;
        require(VitalSensorSupport.plausibleMonsterSensorForTests("game.Health#current","game.Health#max"),"health-pair-accepted");pass++;

        require(HelperConfig.AttackPriority.parse("LOWEST_HP")==HelperConfig.AttackPriority.LOWEST_HP_PERCENT,"legacy-lowest-hp-migrates-to-percent");pass++;
        require(HelperConfig.AttackPriority.parse("LOWEST_HP_ABSOLUTE")==HelperConfig.AttackPriority.LOWEST_HP_ABSOLUTE,"absolute-config-parses");pass++;
        require(HelperConfig.AttackPriority.parse("LOWEST_HP_PERCENT")==HelperConfig.AttackPriority.LOWEST_HP_PERCENT,"percent-config-parses");pass++;

        Path state=Path.of(System.getProperty("user.home"),"Downloads","MirageLab","state");Files.createDirectories(state);
        Path cfg=state.resolve("helper_config.txt");
        Files.writeString(cfg,"# MirageLab Helper v8.2\nauto_attack=true\nattack_range=7\nattack_priority=LOWEST_HP\nattack_scan_interval_ms=25\nattack_retarget=true\nattack_retarget_interval_ms=50\nattack_switch_hp_gain=1\noverlay_enabled=true\n",StandardCharsets.UTF_8);
        HelperConfig.invalidate();HelperConfig.ensureCurrentFile();HelperConfig.Config c=HelperConfig.read();
        require(c.attackPriority==HelperConfig.AttackPriority.LOWEST_HP_PERCENT,"v82-config-priority-preserved-as-percent");pass++;
        require(c.attackScanIntervalMs==20,"v82-default-scan-upgraded-to-20ms");pass++;
        require(c.attackRetargetIntervalMs==40,"v82-default-retarget-upgraded-to-40ms");pass++;
        require(c.attackHpMemoryMs==2500&&c.attackFinishWounded&&c.attackFinishThreshold==35,"v83-target-intelligence-defaults");pass++;
        String raw=Files.readString(cfg,StandardCharsets.UTF_8);
        require(raw.contains("# MirageLab Helper v10.0"),"v83-header");pass++;
        require(raw.contains("attack_priority=LOWEST_HP_PERCENT"),"legacy-priority-rewritten-explicitly");pass++;

        require("Menor HP (valor)".equals(HelperConfig.AttackPriority.LOWEST_HP_ABSOLUTE.label()),"absolute-ui-label");pass++;
        require("Menor HP (%)".equals(HelperConfig.AttackPriority.LOWEST_HP_PERCENT.label()),"percent-ui-label");pass++;

        System.out.println("ALL PASS "+pass+"/"+pass);
    }
    private static void require(boolean b,String n){if(!b)throw new AssertionError("FAIL "+n);System.out.println("PASS "+n);}
}
