package miragelab;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;

public final class MirageLabV85SelfTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Path cfg = Path.of(System.getProperty("user.home"), "Downloads", "MirageLab", "state", "helper_config.txt");
        Files.createDirectories(cfg.getParent());
        // This is the reported bug: a saved overlay=false silently hid every startup.
        Files.writeString(cfg, "# MirageLab Helper v8.4\n"+
                "overlay_enabled=false\noverlay_x=99999\noverlay_y=-1\n"+
                "attack_priority=LOWEST_HP_PERCENT\nattack_retarget=true\n"+
                "attack_anti_ks=true\nparty_members=Parceiro, Aço\n"+
                "attack_hp_memory_ms=7000\n", StandardCharsets.UTF_8);
        HelperConfig.invalidate();HelperConfig.ensureCurrentFile();
        HelperConfig.Config c=HelperConfig.read();
        require(c.overlayEnabled,"v8.4 hidden overlay automatically recovered on migration");
        require(c.attackPriority==HelperConfig.AttackPriority.LOWEST_HP_PERCENT,"chosen HP% mode preserved");
        require(c.attackRetargetEnabled&&c.attackAntiKsEnabled,"combat settings preserved");
        require("Parceiro, Aço".equals(c.partyMembers),"UTF8 party names preserved");
        require(c.attackHpMemoryMs==2500,"shorter monster health memory migration");
        // User explicitly disabling overlay after installing 8.5 remains respected.
        HelperConfig.update(x->x.overlayEnabled=false);
        HelperConfig.ensureCurrentFile();
        require(!HelperConfig.read().overlayEnabled,"explicit v8.5 overlay preference preserved");
        // Same absolute HP, different max HP: absolute and percent must select differently.
        TargetPriority.Candidate a=new TargetPriority.Candidate(1,2,80d,80d,false);
        TargetPriority.Candidate b=new TargetPriority.Candidate(2,3,120d,20d,false);
        require(AutoAttackEngine.chooseForTests(HelperConfig.AttackPriority.LOWEST_HP_ABSOLUTE,false,a,b)==1,"absolute priority selects HP 80");
        require(AutoAttackEngine.chooseForTests(HelperConfig.AttackPriority.LOWEST_HP_PERCENT,false,a,b)==2,"percent priority selects 20%");
        // Ally damages another mob: priority must re-evaluate; distance must not defeat HP.
        TargetPriority.Candidate allyHit=new TargetPriority.Candidate(2,6,18d,9d,false);
        require(AutoAttackEngine.chooseForTests(HelperConfig.AttackPriority.LOWEST_HP_ABSOLUTE,false,a,allyHit)==2,"ally damage reranks absolute");
        require(AutoAttackEngine.chooseForTests(HelperConfig.AttackPriority.LOWEST_HP_PERCENT,false,a,allyHit)==2,"ally damage reranks percentage");
        require(AutoAttackEngine.shouldSwitchDetailedForTests(HelperConfig.AttackPriority.LOWEST_HP_ABSOLUTE,2,80d,80d,6,18d,9d,1),"absolute switches to ally-wounded mob");
        require(AutoAttackEngine.shouldSwitchDetailedForTests(HelperConfig.AttackPriority.LOWEST_HP_PERCENT,2,80d,80d,6,18d,9d,1),"percentage switches to ally-wounded mob");
        // Losing max-HP telemetry of current target must not block measured candidate.
        require(!AutoAttackEngine.finishBlocksForTests(HelperConfig.AttackPriority.LOWEST_HP_ABSOLUTE,null,15d,12d,20d,1,35),"finish lock does not trap unmeasured absolute HP");
        require(Files.readString(cfg,StandardCharsets.UTF_8).contains("# MirageLab Helper v10.0"),"version 8.5 persisted");
        System.out.println("ALL PASS " + checks + "/" + checks);
    }
    static void require(boolean ok,String s){if(!ok)throw new AssertionError("FAILED: "+s);checks++;}
}
