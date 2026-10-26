package miragelab;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MirageLabV80SelfTest {
    public static void main(String[] args) throws Exception {
        int pass=0;
        require(SkillPolicy.due(10_000,0,4000),"skill-first-use-due");pass++;
        require(!SkillPolicy.due(10_000,8_000,4000),"skill-cooldown-blocks");pass++;
        require(SkillPolicy.due(12_100,8_000,4000),"skill-cooldown-expires");pass++;
        require(ManaTrainingPolicy.intervalMs(60)==60_000L,"mana-training-60s");pass++;
        require(ManaTrainingPolicy.due(61_000,1_000,60),"mana-training-due");pass++;
        require(!ManaTrainingPolicy.due(50_000,1_000,60),"mana-training-not-due");pass++;
        require(InputArbiter.canPressForTests(1_000,900,90),"inputbus-gap-open");pass++;
        require(!InputArbiter.canPressForTests(950,900,90),"inputbus-gap-blocked");pass++;
        require(CaveBotEngine.plannedBurstCountForTests(5,2)==2,"route-burst-two");pass++;
        require(CaveBotEngine.plannedBurstCountForTests(1,3)==1,"route-burst-clamped-by-path");pass++;
        require(CaveBotEngine.loopNeedsConnectionForTests(true,8,1),"open-route-loop-connects");pass++;
        require(!CaveBotEngine.loopNeedsConnectionForTests(false,8,1),"loop-disabled-stops");pass++;
        require(CaveBotEngine.burstRetryDueForTests(500,480,0,1),"burst-soft-retry");pass++;
        require(!CaveBotEngine.burstRetryDueForTests(300,480,0,1),"burst-waits-before-retry");pass++;

        HelperConfig.Config c=new HelperConfig.Config();
        c.autoSkillsEnabled=true;c.skill1Enabled=true;c.skill1Hotkey="4";c.skill1CooldownMs=3333;
        c.skill2Enabled=true;c.skill2Hotkey="F2";c.skill2CooldownMs=7777;c.manaTrainingEnabled=true;
        c.manaTrainingHotkey="8";c.manaTrainingIntervalSec=60;c.manaTrainingInCombat=false;c.inputGlobalGapMs=110;
        HelperConfig.write(c);HelperConfig.Config r=HelperConfig.read();
        require(r.autoSkillsEnabled&&r.skill1Enabled&&"4".equals(r.skill1Hotkey)&&r.skill1CooldownMs==3333,"helper-skills-roundtrip");pass++;
        require(r.skill2Enabled&&"F2".equals(r.skill2Hotkey)&&r.skill2CooldownMs==7777,"helper-skill2-roundtrip");pass++;
        require(r.manaTrainingEnabled&&"8".equals(r.manaTrainingHotkey)&&r.manaTrainingIntervalSec==60&&!r.manaTrainingInCombat,"mana-training-roundtrip");pass++;
        require(r.inputGlobalGapMs==110,"input-gap-roundtrip");pass++;

        Path state=Path.of(System.getProperty("user.home"),"Downloads","MirageLab","state");Files.createDirectories(state);
        Files.writeString(state.resolve("cavebot_config.txt"),"# MirageLab CaveBot v7.0\nworld_poll_ms=25\nloop_route=true\n",StandardCharsets.UTF_8);
        var m=CaveBotEngine.class.getDeclaredMethod("ensureFiles");m.setAccessible(true);m.invoke(null);
        String migrated=Files.readString(state.resolve("cavebot_config.txt"),StandardCharsets.UTF_8);
        require(migrated.contains("# MirageLab CaveBot v8.4"),"v7-to-v8-migration-version");pass++;
        require(migrated.contains("world_poll_ms=60")&&migrated.contains("fast_player_poll_ms=12"),"v8-fast-player-migration");pass++;
        require(migrated.contains("route_burst_size=2")&&migrated.contains("route_burst_timeout_ms=480"),"v8-burst-migration");pass++;

        System.out.println("ALL PASS "+pass+"/"+pass);
    }
    private static void require(boolean b,String n){if(!b)throw new AssertionError("FAIL "+n);System.out.println("PASS "+n);}
}
