package miragelab;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

public final class MirageLabV94HealTest {
    static int checks;
    private static void ok(boolean v,String name){checks++;if(!v)throw new AssertionError("FAILED "+name+" @"+checks);}
    private static void near(double a,double b,double tolerance,String tag){ok(Math.abs(a-b)<tolerance,tag+": "+a+" vs "+b);}

    static BufferedImage fake(int hp,int mana){
        int w=1920;BufferedImage im=new BufferedImage(w,130,BufferedImage.TYPE_INT_RGB);
        int hpStart=(int)Math.round(w*.0177),hpEnd=(int)Math.round(w*.503);
        int mpStart=(int)Math.round(w*.5175),mpEnd=(int)Math.round(w*.956);
        int endHp=hpStart+(int)Math.round((hpEnd-hpStart)*hp/100.0),endMp=mpStart+(int)Math.round((mpEnd-mpStart)*mana/100.0);
        for(int y=28;y<50;y++)for(int x=hpStart;x<hpEnd;x++)im.setRGB(x,y,x<endHp?new Color(hp>60?0:255,hp>60?255:hp>25?195:55,0).getRGB():new Color(70,70,70).getRGB());
        for(int y=28;y<50;y++)for(int x=mpStart;x<mpEnd;x++)im.setRGB(x,y,x<endMp?new Color(0,194,242).getRGB():new Color(70,70,70).getRGB());
        return im;
    }

    public static void main(String[] args) throws Exception {
        var dir=Path.of("tests/fixtures");
        PlayerHudVitalsSupport.Reading real=PlayerHudVitalsSupport.inspect(ImageIO.read(dir.resolve("mirage_58hp_48mana.png").toFile()),1000);
        ok(real.hp()!=null,"real HP recognized: "+real.detail());
        near(real.hp(),100.0*58/325,2.5,"actual in-game 58/325");
        near(real.mana(),100.0*48/235,2.5,"actual in-game 48/235");
        var real2=PlayerHudVitalsSupport.inspect(ImageIO.read(dir.resolve("mirage_139hp_13mana.png").toFile()),1000);
        ok(real2.hp()!=null,"another real screenshot recognized: "+real2.detail());
        near(real2.hp(),100.0*139/325,2.5,"actual 139/325");
        near(real2.mana(),100.0*13/235,2.5,"actual 13/235");
        for(int h=3;h<100;h+=2)for(int m=3;m<100;m+=5){
            var r=PlayerHudVitalsSupport.inspect(fake(h,m),1000);
            ok(r.hp()!=null,"synthetic detected "+h+"/"+m);
            near(r.hp(),h,1.5,"synthetic hp "+h);
            near(r.mana(),m,1.5,"synthetic mana "+m);
        }
        var emptyMana=PlayerHudVitalsSupport.inspect(fake(41,0),1000);
        ok(emptyMana.hp()!=null,"HP still readable when mana reaches zero");
        near(emptyMana.mana(),0,0.5,"zero mana");
        BufferedImage black=new BufferedImage(1920,130,BufferedImage.TYPE_INT_RGB);
        ok(PlayerHudVitalsSupport.inspect(black,1000).hp()==null,"unrelated window rejected");
        ok(PlayerHudVitalsSupport.inspect(new BufferedImage(640,120,BufferedImage.TYPE_INT_RGB),1000).hp()==null,"smaller unsupported window rejected");
        PlayerHudVitalsSupport.resetForTests();
        PlayerHudVitalsSupport.ingestForTests(new PlayerHudVitalsSupport.Reading(18.0,95.0,1000,"frame1"));
        ok(PlayerHudVitalsSupport.current(1000).hp()==null,"single-frame not trusted");
        PlayerHudVitalsSupport.ingestForTests(new PlayerHudVitalsSupport.Reading(18.1,80.0,1200,"frame2"));
        ok(PlayerHudVitalsSupport.current(1200).hp()!=null,"HP confirms independently of falling mana");
        ok(PlayerHudVitalsSupport.current(1200).mana()==null,"mana not confirmed while changing");
        PlayerHudVitalsSupport.ingestForTests(new PlayerHudVitalsSupport.Reading(17.9,80.3,1400,"frame3"));
        ok(PlayerHudVitalsSupport.current(1400).mana()!=null,"mana confirms when stable");
        PlayerHudVitalsSupport.ingestForTests(new PlayerHudVitalsSupport.Reading(null,null,1600,"window covered"));
        ok(PlayerHudVitalsSupport.current(1600).hp()==null,"covered HUD fails closed");
        PlayerHudVitalsSupport.ingestForTests(new PlayerHudVitalsSupport.Reading(18.0,80.0,2000,"frame1"));
        PlayerHudVitalsSupport.ingestForTests(new PlayerHudVitalsSupport.Reading(18.0,80.0,2200,"frame2"));
        ok(PlayerHudVitalsSupport.current(3200).hp()==null,"stale HUD fails closed");
        // HealSafety never issues a second key without seeing the value recover.
        HealSafety guard=new HealSafety();guard.observe(18.0,1000);guard.observe(18.0,1400);
        ok(guard.canUse(18.0,50,1450,3500),"eligible 18% below 50% threshold");
        guard.used(18.0,1450);guard.observe(17.0,2300);ok(!guard.canUse(17.0,50,2300,3500),"no immediate repeated potion");
        guard.observe(16.0,4000);ok(guard.blockedForTests(),"no improvement blocks subsequent presses");
        guard.reset();guard.observe(18.0,1000);guard.observe(18.0,1400);guard.used(18.0,1450);
        guard.observe(48.0,2300);ok(!guard.blockedForTests(),"heal confirmed");
        ok(!HealPolicy.shouldUse(null,50,10000,3500),"unknown HP cannot cause potion");
        HelperConfig.Config c=new HelperConfig.Config();ok(c.hpHealEnabled && !c.manaHealEnabled,"HP enabled mana opt-in");
        c.manaHealEnabled=true;c.hpThreshold=33;HelperConfig.write(c);
        HelperConfig.Config d=HelperConfig.read();ok(d.manaHealEnabled && d.hpThreshold==33,"new heal settings persist");
        System.out.println("V9.4 HUD/HEAL PASS "+checks+" checks");
    }
}
