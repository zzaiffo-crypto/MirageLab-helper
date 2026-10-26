package miragelab;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Deterministic production-module integration, no external game client. */
public final class MirageLabV90StressTest {
    private static long checks=0;
    private static void ok(boolean b,String name){checks++;if(!b)throw new AssertionError("#"+checks+" "+name);}
    public static void main(String[] args) throws Exception {
        Random rng=new Random(20261025L);
        Path base=Path.of(System.getProperty("user.home"),"Downloads","MirageLab","state");Files.createDirectories(base);
        Path config=HelperConfig.file();
        Files.writeString(config,"# MirageLab Helper v8.7\nattack_range=7\nparty_members=AmigoOriginal\n");
        // Legacy-state migration and unknown user settings preserved across hundreds of cycles.
        for(int i=0;i<300;i++) {
            int saved=i;
            String unknown="my_personal_script="+i+"\nlegacy_flag_"+i+"=keep\n";
            String raw="# MirageLab Helper v8.7\nattack_range="+(i%29+1)+"\nparty_members=Aliado"+i+"\n"+unknown;
            SafeStateStore.write(config,raw);
            HelperConfig.Config c=HelperConfig.read();
            ok(c.attackRange==i%29+1,"legacy range");
            ok(c.partyMembers.equals("Aliado"+i),"legacy party");
            c.autoSkillsEnabled=(i%2==0);
            HelperConfig.write(c);
            String updated=Files.readString(config);
            ok(updated.contains("# MirageLab Helper v10.0"),"marker v9");
            ok(updated.contains("my_personal_script="+saved+"\n"),"unknown key kept");
            ok(updated.contains("legacy_flag_"+i+"=keep"),"unrelated state kept");
            ok(HelperConfig.read().autoSkillsEnabled==(i%2==0),"skills stable");
        }
        Path backup=config.resolveSibling(config.getFileName()+".pre_v9.bak");
        ok(Files.exists(backup),"pre-v9 backup created");
        ok(Files.readString(backup).contains("# MirageLab Helper v8.7"),"backup has original content");
        // 4 * 500 randomized preset stress cases, idempotency, preservation of existing choices.
        for(int i=0;i<500;i++) for(AutomationPresets.Mode m:AutomationPresets.Mode.values()) {
            HelperConfig.Config c=new HelperConfig.Config();
            c.attackRange=1+rng.nextInt(30);c.hpThreshold=10+rng.nextInt(70);c.partyMembers="Ana"+i;
            c.attackIgnoreNames="Butterfly"+i;c.attackAntiKsEnabled=rng.nextBoolean();
            int oldRange=c.attackRange,oldHp=c.hpThreshold;String party=c.partyMembers,filter=c.attackIgnoreNames;boolean anti=c.attackAntiKsEnabled;
            AutomationPresets.apply(c,m);
            boolean expectedAttack=m!=AutomationPresets.Mode.PAUSED;
            boolean expectedSkills=m==AutomationPresets.Mode.ASSISTED||m==AutomationPresets.Mode.HUNT_READY;
            ok(c.autoAttackEnabled==expectedAttack,"preset attack");ok(c.autoSkillsEnabled==expectedSkills,"preset skills");
            ok(!c.autoHealEnabled&&!c.manaTrainingEnabled,"no auto-enabling uncalibrated healing");
            ok(c.attackRange==oldRange && c.hpThreshold==oldHp,"presets preserve tuned thresholds");
            ok(c.partyMembers.equals(party)&&c.attackIgnoreNames.equals(filter)&&c.attackAntiKsEnabled==anti,"presets preserve party/filter/antiks");
            AutomationPresets.apply(c,m);
            ok(c.autoAttackEnabled==expectedAttack && c.autoSkillsEnabled==expectedSkills,"idempotent preset");
        }
        // Underflow/overflow/NaN should never trigger automated healing.
        Double[] invalid={null,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,-0.1,101.0};
        for(int i=0;i<750;i++) {
            for(Double val:invalid)ok(!HealPolicy.shouldUse(val,65,9000,850),"invalid health never uses key");
            double v=rng.nextDouble()*100;
            ok(HealPolicy.shouldUse(v,65,9000,850)==(v<=65),"stable healthy sensor threshold");
            ok(!HealPolicy.shouldUse(v,65,149,850),"cooldown boundary");
        }
        // Atomic replacements should leave neither torn files nor temp files after successful writes.
        Path file=base.resolve("atomic_validation.cfg");
        for(int i=0;i<400;i++){
            String s="epoch="+i+"\nvalue="+"x".repeat(i%127)+"\n";
            SafeStateStore.write(file,s);ok(Files.readString(file,StandardCharsets.UTF_8).equals(s),"atomic intact");
        }
        try(var list=Files.list(base)){ok(list.noneMatch(f->f.getFileName().toString().startsWith(".miragelab_")),"no temp residue");}
        // Attack history counter structure consistency and no accidental writes to routing commands.
        long before=SessionTelemetry.totalSelections(),changes=SessionTelemetry.totalChanges();
        for(int i=0;i<500;i++){SessionTelemetry.accept("TARGET 1 Rat");SessionTelemetry.accept("RETARGET 1 -> 2");}
        ok(SessionTelemetry.totalSelections()==before+500,"session target counts");
        ok(SessionTelemetry.totalChanges()==changes+500,"session retarget counts");
        ok(AutomationPresets.describe(AutomationPresets.Mode.HUNT_READY).contains("CaveBot"),"AFK limitation explained");
        System.out.println("MIRAGELAB V9 STRESS PASS "+checks+" / "+checks);
    }
}
