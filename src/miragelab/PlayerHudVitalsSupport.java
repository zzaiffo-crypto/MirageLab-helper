package miragelab;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reads the health/mana HUD that Mirage itself draws, not inferred fields of arbitrary
 * ECS components. No manual calibration. Fail-closed if the window is covered,
 * moved away from the top of its screen, minimized, or the bar layout changes.
 * The capture worker never blocks LibGDX's render thread.
 */
final class PlayerHudVitalsSupport {
    record Reading(Double hp, Double mana, long timestamp, String detail) { }
    private static volatile Reading latest = new Reading(null, null, 0, "aguardando HUD");
    private static final AtomicBoolean busy = new AtomicBoolean();
    private static volatile long lastStarted;
    private static volatile double lastHp=Double.NaN, lastMana=Double.NaN;
    private static volatile long hpAcceptedAt, mpAcceptedAt;
    private static volatile int hpConsecutive, mpConsecutive;
    private static volatile Double acceptedHp, acceptedMp;

    private PlayerHudVitalsSupport() { }

    static void request(long now) {
        if (now-lastStarted < 180 || !busy.compareAndSet(false,true)) return;
        lastStarted=now;
        Thread t=new Thread(()->{
            try {
                GraphicsDevice d=GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
                Rectangle screen=d.getDefaultConfiguration().getBounds();
                Rectangle top=new Rectangle(screen.x,screen.y,screen.width,Math.min(170,screen.height));
                BufferedImage image=new Robot(d).createScreenCapture(top);
                Reading observed=inspect(image,System.currentTimeMillis());
                accept(observed);

            } catch (Throwable e) {
                latest=new Reading(null,null,System.currentTimeMillis(),"captura indisponivel: "+e.getClass().getSimpleName());
                hpConsecutive=mpConsecutive=0; acceptedHp=acceptedMp=null;
            } finally {busy.set(false);}
        },"MirageLab-HUD-Reader");t.setDaemon(true);t.start();
    }

    private static synchronized void accept(Reading observed) {
        if(observed.hp()==null || observed.mana()==null){
            latest=observed;hpConsecutive=mpConsecutive=0;
            acceptedHp=acceptedMp=null;lastHp=lastMana=Double.NaN;
            return;
        }
        // A health potion, hit or spell can change the other bar quickly. Confirm
        // HP and MP independently so spending mana never disables automatic HP heal.
        if(Double.isFinite(lastHp) && Math.abs(observed.hp()-lastHp)<=3)hpConsecutive++;
        else hpConsecutive=1;
        if(Double.isFinite(lastMana) && Math.abs(observed.mana()-lastMana)<=3)mpConsecutive++;
        else mpConsecutive=1;
        lastHp=observed.hp();lastMana=observed.mana();
        if(hpConsecutive>=2){acceptedHp=observed.hp();hpAcceptedAt=observed.timestamp();}
        else acceptedHp=null;
        if(mpConsecutive>=2){acceptedMp=observed.mana();mpAcceptedAt=observed.timestamp();}
        else acceptedMp=null;
        latest=new Reading(acceptedHp,acceptedMp,observed.timestamp(),
            acceptedHp==null && acceptedMp==null?"confirmando barras (2 quadros)":
            acceptedHp==null?"HP confirmando":acceptedMp==null?"Mana confirmando":"HUD do jogo (confirmado)");
    }
    static synchronized Reading current(long now) {
        Reading r=latest;
        boolean valid=r.timestamp()>0 && now-r.timestamp()<=900;
        Double hp=valid && hpAcceptedAt>0 && now-hpAcceptedAt<=900?r.hp():null;
        Double mana=valid && mpAcceptedAt>0 && now-mpAcceptedAt<=900?r.mana():null;
        return new Reading(hp,mana,r.timestamp(),valid?r.detail():"HUD ausente/desatualizado");
    }
    static void ingestForTests(Reading reading){accept(reading);}

    /** Package-private deterministic recognition entrypoint for image regression tests. */
    static Reading inspect(BufferedImage image,long now) {
        if(image==null || image.getWidth()<850 || image.getHeight()<48)
            return new Reading(null,null,now,"resolucao HUD nao suportada");
        int w=image.getWidth(), rows=Math.min(140,image.getHeight());
        int hs=(int)Math.round(w*.0177), he=(int)Math.round(w*.503);
        int ms=(int)Math.round(w*.5175), me=(int)Math.round(w*.956);
        if(hs<0 || me>=w || he-hs<300 || me-ms<300)
            return new Reading(null,null,now,"geometria HUD invalida");
        double bestScore=-1, hp=-1, mana=-1;
        // Mirage v0.8.59 uses a 21px bar directly under its titlebar. Moving
        // the client downwards on the screen is supported within this range.
        for(int y=27;y<rows-3;y+=2) {
            Bar h=measure(image,hs,he,y,true);
            Bar m=measure(image,ms,me,y,false);
            double score=Math.min(h.confidence,m.confidence);
            if(score>bestScore){bestScore=score;hp=h.percent;mana=m.percent;}
        }
        if(bestScore<.84 || hp<0 || mana<0)
            return new Reading(null,null,now,"HUD nao visivel ou layout diferente");
        return new Reading(hp,mana,now,"HUD direto (barra do jogo)");
    }

    private record Bar(double percent,double confidence) { }
    private static Bar measure(BufferedImage im,int from,int end,int y,boolean hp) {
        int filled=0,base=0,other=0, earliest=-1;
        for(int x=from;x<end;x++) {
            int rgb=im.getRGB(x,y);
            int r=(rgb>>>16)&255,g=(rgb>>>8)&255,b=rgb&255;
            boolean color=hp ? isHp(r,g,b) : (r<45 && g>140 && b>170 && b>g);
            boolean gray=Math.abs(r-g)<5 && Math.abs(g-b)<5 && r>=58 && r<=82;
            if(color){filled++;if(earliest<0)earliest=x;}
            else if(gray)base++;
            else other++;
        }
        int n=end-from;
        double conf=(filled+base)/(double)n;
        // Require a real filled bar in both slots, as proof it is the game HUD.
        if ((filled<Math.max(8,n/150) && (hp || base<n*.97)) || (filled>0 && earliest>from+15))conf=0;
        // Exclude visually impossible geometry and overlays/other windows.
        if(other>n*.17)conf=0;
        return new Bar(100.0*filled/n,conf);
    }
    private static boolean isHp(int r,int g,int b) {
        if(b>65) return false;
        return (r>=170 && g<235) || (g>=155 && r<180);
    }
    static void resetForTests(){latest=new Reading(null,null,0,"aguardando HUD");lastHp=lastMana=Double.NaN;hpAcceptedAt=mpAcceptedAt=0;hpConsecutive=mpConsecutive=0;acceptedHp=acceptedMp=null;lastStarted=0;}
}
