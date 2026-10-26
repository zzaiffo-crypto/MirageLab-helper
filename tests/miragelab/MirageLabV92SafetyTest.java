package miragelab;

import java.util.Random;
import java.util.Map;
import java.util.HashMap;

/** Deterministic test matrix for v9.2 fail-closed HP, heal and chase policies. */
public final class MirageLabV92SafetyTest {
    private static int checks;
    private static void yes(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
    public static void main(String[] args) {
        String[] sceneKeys={"com.badlogic.gdx.scenes.scene2d.Actor#x", "com.badlogic.gdx.scenes.scene2d.Actor#y", "com.badlogic.gdx.scenes.scene2d.Actor#width", "com.badlogic.gdx.scenes.scene2d.Actor#getX()", "com.badlogic.gdx.scenes.scene2d.Group#x"};
        for(String k:sceneKeys){
            yes(!VitalSensorSupport.plausibleMonsterSensorForTests(k,"PERCENT"),"reject fake HP: "+k);
            yes(!VitalSensorSupport.plausibleMonsterSensorForTests("my.Hp#cur",k),"reject fake max HP: "+k);
            yes(VitalSensorSupport.disallowedMonsterMetricForTests(k),"bad metric "+k);
        }
        Random rnd=new Random(92026);
        for(int i=0;i<1200;i++){
            int a=rnd.nextInt(51)-25,b=rnd.nextInt(51)-25;
            int[] step=ChaseSupport.direction(0,0,a,b);
            if(Math.max(Math.abs(a),Math.abs(b))<=1){yes(step==null,"chase must stop beside target");}
            else {yes(step!=null,"chase step exists");yes(Math.abs(step[0])+Math.abs(step[1])==1,"cardinal move only");
                int old=Math.max(Math.abs(a),Math.abs(b));int newer=Math.max(Math.abs(a-step[0]),Math.abs(b-step[1]));
                yes(newer<=old,"chase move no greater range");}
        }
        for(int i=0;i<350;i++){
            HealSafety h=new HealSafety();long base=10000+i*1000L;
            h.observe(25.0,base);yes(!h.canUse(25.0,35,base,850),"wait for confirmation sample");
            h.observe(24.9,base+250);yes(h.canUse(24.9,35,base+251,850),"first once");
            h.used(24.9,base+252);yes(!h.canUse(24.9,35,base+2000,850),"pending no spam");
            h.observe(24.9,base+2500);yes(h.blockedForTests(),"failure closed");
            yes(!h.canUse(24.0,35,base+20000,850),"no presses after unconfirmed use");
            h.reset();yes(!h.blockedForTests(),"reset requires toggle");
        }
        for(int i=0;i<220;i++){
            HealSafety h=new HealSafety();long base=20000+i*1000L;
            h.observe(20.0,base);h.observe(20.0,base+230);h.used(20.0,base+240);
            h.observe(45.0,base+1150);yes(!h.blockedForTests(),"successful use not blocked");
            yes(!h.canUse(30.0,35,base+1500,850),"hard 3.5s cooldown");
        }
        // Old coordinate artifact would fake 60% HP. Must now never lock.
        for(int i=0;i<600;i++){
            Map<String,Double> old=new HashMap<>(),cur=new HashMap<>();
            old.put("com.badlogic.gdx.scenes.scene2d.Actor#x",400d+i);cur.put("com.badlogic.gdx.scenes.scene2d.Actor#x",396d+i);
            old.put("com.badlogic.gdx.scenes.scene2d.Actor#y",750d+i);cur.put("com.badlogic.gdx.scenes.scene2d.Actor#y",750d+i);
            yes(VitalSensorSupport.chooseMonsterDropKeyForTests(old,cur)==null,"reject moving actor position as HP");
        }
        for(int i=0;i<900;i++){
            int px=rnd.nextInt(200),py=rnd.nextInt(200),mx=rnd.nextInt(200),my=rnd.nextInt(200);
            int radius=5+rnd.nextInt(26);
            boolean expected=Math.abs(px-mx)<=radius&&Math.abs(py-my)<=radius;
            yes(AutoAttackEngine.withinScanRadius(px,py,mx,my,radius,false)==expected,"local scan radius");
            yes(AutoAttackEngine.withinScanRadius(px+200,py+100,mx,my,radius,false)==
                    (Math.abs(px+200-mx)<=radius&&Math.abs(py+100-my)<=radius),"refresh after moving");
            yes(AutoAttackEngine.withinScanRadius(px,py,mx,my,radius,true),"selected target retained");
        }
        try {
            java.nio.file.Path cfg=HelperConfig.file();java.nio.file.Files.createDirectories(cfg.getParent());
            java.nio.file.Files.writeString(cfg,"# MirageLab Helper v9.1\noverlay_enabled=false\nattack_visual_hp=true\nattack_range=8\ncustom_saved=abc\n");
            HelperConfig.invalidate();HelperConfig.ensureCurrentFile();
            HelperConfig.Config migrated=HelperConfig.read();
            yes(!migrated.attackVisualHpEnabled,"old broken visual disabled once");
            yes(!migrated.overlayEnabled,"preserve user overlay choice");
            yes(migrated.attackRange==8,"preserve range");
            yes(java.nio.file.Files.readString(cfg).contains("custom_saved=abc"),"preserve unknown custom fields");
            migrated.attackVisualHpEnabled=true;HelperConfig.write(migrated);HelperConfig.ensureCurrentFile();
            yes(HelperConfig.read().attackVisualHpEnabled,"explicit new visual opt in preserved");
        }catch(Exception e){throw new AssertionError(e);}
        HelperConfig.Config config=new HelperConfig.Config();
        yes(!config.attackVisualHpEnabled,"visual must be opt-in");
        yes(!config.attackChaseEnabled,"chase must be opt-in");
        yes(config.attackScanRadius>=config.attackRange,"default refresh radius sufficient");
        yes(config.healCooldownMs>=2500,"default cooldown");
        System.out.println("V9.2 SAFETY TEST PASS "+checks+" checks");
    }
}
