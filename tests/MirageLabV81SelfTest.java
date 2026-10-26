package miragelab;

public final class MirageLabV81SelfTest {
    public static void main(String[] args) throws Exception {
        int pass=0;
        require(AutoAttackEngine.shouldSwitchForTests(HelperConfig.AttackPriority.NEAREST,4,70d,false,2,80d,false,1,8,true),"retarget-nearer");pass++;
        require(!AutoAttackEngine.shouldSwitchForTests(HelperConfig.AttackPriority.NEAREST,4,70d,false,4,10d,false,1,8,true),"retarget-no-equal-distance-jitter");pass++;
        require(AutoAttackEngine.shouldSwitchForTests(HelperConfig.AttackPriority.FARTHEST,2,70d,false,5,70d,false,1,8,true),"retarget-farthest");pass++;
        require(AutoAttackEngine.shouldSwitchForTests(HelperConfig.AttackPriority.LOWEST_HP_PERCENT,2,75d,false,3,50d,false,1,8,true),"retarget-lowest-hp");pass++;
        require(!AutoAttackEngine.shouldSwitchForTests(HelperConfig.AttackPriority.LOWEST_HP_PERCENT,2,55d,false,3,50d,false,1,8,true),"retarget-hp-hysteresis");pass++;
        require(!AutoAttackEngine.shouldSwitchForTests(HelperConfig.AttackPriority.NEAREST,1,90d,false,6,90d,true,1,8,true),"criterion-beats-aggressor");pass++;
        require(!AutoAttackEngine.shouldSwitchForTests(HelperConfig.AttackPriority.NEAREST,1,90d,false,6,90d,true,1,8,false),"aggressor-configurable");pass++;

        HelperConfig.Config c=new HelperConfig.Config();
        c.attackRetargetEnabled=true;c.attackRetargetIntervalMs=95;c.attackSwitchDistanceGain=2;c.attackSwitchHpGain=12;c.manualTargetLockMs=900;c.preferAggressor=false;c.inputPulseMs=61;c.overlayEnabled=true;c.overlayX=123;c.overlayY=456;
        HelperConfig.write(c);HelperConfig.Config r=HelperConfig.read();
        require(r.attackRetargetEnabled&&r.attackRetargetIntervalMs==95&&r.attackSwitchDistanceGain==2,"retarget-config-roundtrip");pass++;
        require(r.attackSwitchHpGain==12&&r.manualTargetLockMs==900&&!r.preferAggressor,"manual-target-config-roundtrip");pass++;
        require(r.inputPulseMs==61&&r.overlayEnabled&&r.overlayX==123&&r.overlayY==456,"overlay-pulse-config-roundtrip");pass++;
        require(InputArbiter.pulseDueForTests(1060,1055),"pulse-release-due");pass++;
        require(!InputArbiter.pulseDueForTests(1040,1055),"pulse-release-not-early");pass++;
        System.out.println("ALL PASS "+pass+"/"+pass);
    }
    private static void require(boolean b,String n){if(!b)throw new AssertionError("FAIL "+n);System.out.println("PASS "+n);}
}
