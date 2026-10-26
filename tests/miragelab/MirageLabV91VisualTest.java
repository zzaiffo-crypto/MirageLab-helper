package miragelab;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.nio.file.*;
import javax.imageio.ImageIO;
import java.util.*;

public final class MirageLabV91VisualTest {
    static int pass;
    static void check(boolean b,String message){if(!b)throw new AssertionError(message);pass++;}
    static BufferedImage sample(int percent,boolean noisy,boolean ambiguous){
        BufferedImage img=new BufferedImage(960,540,BufferedImage.TYPE_INT_RGB);
        var r=new Random(12);for(int y=0;y<540;y++)for(int x=0;x<960;x++)img.setRGB(x,y,noisy?new Color(r.nextInt(70),70+r.nextInt(60),r.nextInt(50)).getRGB():0x41622A);
        bar(img,465,198,19,percent);
        if(ambiguous)bar(img,465,166,19,20);
        return img;
    }
    static void bar(BufferedImage img,int x,int y,int w,int percentage){
        for(int i=0;i<w;i++)for(int j=0;j<7;j++){
            int c=(i==0||i==w-1||j==0||j==6)?0:
                    i<=1+(w-4)*percentage/100?0x1DFF00:0x494949;
            img.setRGB(x+i,y+j,c);
        }
    }
    static void run(){
        for(int pct=10;pct<100;pct+=5){
            Double hp=ScreenHpSupport.findBar(sample(pct,false,false),475,245,30);
            check(hp!=null&&Math.abs(hp-pct)<13,"visual synthetic percent="+pct+" -> "+hp);
        }
        check(ScreenHpSupport.findBar(sample(67,true,false),475,245,30)!=null,"noisy terrain");
        check(ScreenHpSupport.findBar(new BufferedImage(960,540,BufferedImage.TYPE_INT_RGB),475,245,30)==null,"black does not fake bar");
        for(int i=0;i<200;i++){
            BufferedImage img=new BufferedImage(960,540,BufferedImage.TYPE_INT_RGB);
            check(ScreenHpSupport.findBar(img,475,245,30)==null,"unobserved black sample");
        }
        try{
            if(args.length>0){BufferedImage a=ImageIO.read(Path.of(args[0]).toFile());
                Double first=ScreenHpSupport.findBar(a,900,510,64);
                check(first!=null&&first>55&&first<100,"real cyclops screenshot "+first);
                if(args.length>1){BufferedImage b=ImageIO.read(Path.of(args[1]).toFile());
                    Double second=ScreenHpSupport.findBar(b,900,475,64);
                    check(second!=null&&second>45&&second<=100,"real imp screenshot "+second);
                }
            }
        }catch(Exception e){throw new AssertionError(e);}
    }
    private static String[] args;
    public static void main(String[] args){MirageLabV91VisualTest.args=args;run();System.out.println("MIRAGELAB V9.1 VISION PASS "+pass+" checks");}
}
