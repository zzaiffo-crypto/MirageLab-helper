package miragelab;

import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Conservative visual fallback based on the actual drawn creature HP bar.
 * No image is saved. If window centering, scale or health-bar framing is uncertain,
 * this module returns unknown instead of fabricating HP.
 */
final class ScreenHpSupport {
    private static final long SNAPSHOT_INTERVAL_MS=260L, VALID_MS=850L;
    private static Robot robot;
    private record Frame(BufferedImage image,Rectangle bounds,long at){}
    private static volatile Frame frame;
    private static final AtomicBoolean captureInProgress=new AtomicBoolean(false);
    private static final ExecutorService capturer=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"MirageLab-HP-Frame");t.setDaemon(true);return t;});
    private static int captureFailures, accepted, rejected;
    private static final Map<Integer, Reading> confirmed=new HashMap<>();
    private static final Map<Integer, Reading> pending=new HashMap<>();
    private static final Map<Integer, Long> processedFrame=new HashMap<>();
    private ScreenHpSupport(){}
    private record Reading(double percent,long at,int x,int y){}

    static Double observe(int id, int dx, int dy, int attackRange, long now) {
        if(Math.abs(dx)>attackRange+1||Math.abs(dy)>attackRange+1)return null;
        try {
            Frame snapshot=frame;
            if((snapshot==null||now-snapshot.at>=SNAPSHOT_INTERVAL_MS)&&captureInProgress.compareAndSet(false,true)){
                capturer.execute(()->{
                    try{
                        Rectangle b=GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
                        if(b.width>=800&&b.height>=500){
                            if(robot==null)robot=new Robot();
                            BufferedImage image=robot.createScreenCapture(b);
                            frame=new Frame(image,b,System.currentTimeMillis());
                        }
                    }catch(Throwable t){captureFailures++;}finally{captureInProgress.set(false);}
                });
            }
            if(snapshot==null)return null;
            BufferedImage lastImage=snapshot.image;
            Rectangle lastBounds=snapshot.bounds;
            long lastCaptureAt=snapshot.at;
            Reading known=confirmed.get(id);
            if(processedFrame.getOrDefault(id,-1L)==lastCaptureAt) {
                return known!=null&&now-known.at<=VALID_MS?known.percent:null;
            }
            processedFrame.put(id,lastCaptureAt);
            double tile=lastBounds.width/30d;
            int cx=lastImage.getWidth()/2+(int)Math.round(dx*tile);
            int cy=lastImage.getHeight()/2+(int)Math.round(dy*tile);
            Double value=findBar(lastImage,cx,cy,tile);
            if(value==null){rejected++;return null;}
            Reading prior=pending.get(id);
            if(prior!=null&&now-prior.at<=1100L && Math.abs(value-prior.percent)<=4.5
                    && Math.abs(cx-prior.x)<=tile*0.35 && Math.abs(cy-prior.y)<=tile*0.35){
                confirmed.put(id,new Reading(value,now,cx,cy));accepted++;
            }
            pending.put(id,new Reading(value,now,cx,cy));
            Reading r=confirmed.get(id);
            if(r==null||now-r.at>VALID_MS||Math.abs(r.percent-value)>10)return null;
            if(confirmed.size()>256){confirmed.entrySet().removeIf(e->now-e.getValue().at>5000L);}
            if(pending.size()>256){pending.entrySet().removeIf(e->now-e.getValue().at>5000L);}
            if(processedFrame.size()>256){processedFrame.keySet().retainAll(pending.keySet());}
            return r.percent;
        }catch(Throwable error){captureFailures++;return null;}
    }

    /** Pure image detector, independent of the game; tests also run on saved captures.
     * Requires a pixel-accurate black-bordered, colored horizontal meter near the actor.
     */
    static Double findBar(BufferedImage img, int actorX,int actorY,double tile){
        if(img==null||tile<15||tile>180)return null;
        int[] widths={(int)Math.round(tile*.50),(int)Math.round(tile*.625),(int)Math.round(tile*.75)};
        int margin=(int)Math.ceil(tile*0.19);
        int ymin=Math.max(3,actorY-(int)Math.round(tile*2.3));
        int ymax=Math.min(img.getHeight()-4,actorY-(int)Math.round(tile*0.28));
        double best=-1, second=-1;Double found=null;int bestY=-1000;
        for(int width:widths)for(int y=ymin;y<=ymax;y++){
            for(int x=actorX-width/2-margin;x<=actorX-width/2+margin;x++){
                if(x<3||x+width+2>=img.getWidth())continue;
                // The Mirage HP meter has a distinct 1px black rectangle and
                // a flat filled central line, 5px tall at 1920x1020.
                int centerX=x+width/2;
                if(!isDark(img.getRGB(x,y))||!isDark(img.getRGB(x+width-1,y)))continue;
                if(!isDark(img.getRGB(centerX,y-3))||!isDark(img.getRGB(centerX,y+3)))continue;
                int fill=0,empty=0,total=0,odd=0;
                for(int xx=x+2;xx<x+width-2;xx++){
                    int rgb=img.getRGB(xx,y);
                    if(isColorBar(rgb))fill++;
                    else if(isEmptyBar(rgb))empty++;
                    else odd++;
                    total++;
                }
                if(total<12||odd>2||fill<1||fill+empty<total-2)continue;
                double pct=100d*fill/(fill+empty);
                if(pct<=1||pct>100)continue;
                double score=6.0-3.5*Math.abs(centerX-actorX)/Math.max(1d,tile)
                        -2d*odd/total;
                // Compare only distinct bars, not multiple rows of the same bar.
                if(score>best){
                    if(bestY!=-1000&&Math.abs(y-bestY)>Math.max(6,tile*.2))second=Math.max(second,best);
                    best=score;bestY=y;found=pct;
                }else if(Math.abs(y-bestY)>Math.max(6,tile*.2))second=Math.max(second,score);
            }
        }
        // If vertically stacked mobs have overlapping search regions, reject.
        if(second>best-.35)return null;
        return best>4.4?found:null;
    }
    private static boolean isDark(int c){int r=(c>>16)&255,g=(c>>8)&255,b=c&255;return r<35&&g<37&&b<35;}
    private static boolean isColorBar(int c){int r=(c>>16)&255,g=(c>>8)&255,b=c&255;
        return (g>155&&g>r*1.5&&g>b*1.35)||(r>155&&r>g*1.45&&r>b*1.4);
    }
    private static boolean isEmptyBar(int c){int r=(c>>16)&255,g=(c>>8)&255,b=c&255;
        return r>=35&&r<=105&&Math.abs(r-g)<=9&&Math.abs(g-b)<=20;
    }
    static String status(){return "Vision bar= "+accepted+" ok / "+rejected+" ambiguous / "+captureFailures+" screen errors";}
}
