package miragelab;
import javax.swing.*;
import java.lang.reflect.*;
/** V10 GUI smoke, runs under xvfb: translucent analyzer/XP labels/reset/close. */
public final class OverlayV100SmokeTest {
    static int count;
    static void ok(boolean yes,String s){count++;if(!yes)throw new AssertionError(s);}
    static Object field(String name)throws Exception{
        Field f=AnalyzerWindow.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);
    }
    public static void main(String[] args)throws Exception{
        AnalyzerEngine.reset();AnalyzerEngine.gainedXpEvent(42);
        AnalyzerWindow.show();
        SwingUtilities.invokeAndWait(()->{});
        JWindow w=(JWindow)field("window");
        ok(w!=null,"window created");ok(w.isAlwaysOnTop(),"always on top");
        ok(w.isVisible(),"visible");ok(w.getBackground().getAlpha()==0,"translucent backing");
        JLabel xp=(JLabel)field("xp"), events=(JLabel)field("events");
        ok(xp.getText().contains("XP/h"),"XP visible");ok(events.getText().contains("42"),"XP event shown");
        JButton reset=findReset(w.getContentPane());ok(reset!=null,"reset button");
        SwingUtilities.invokeAndWait(reset::doClick);
        ok(AnalyzerEngine.snapshot(System.currentTimeMillis()).xpEvents()==0,"reset clear data");
        AnalyzerWindow.hide();SwingUtilities.invokeAndWait(()->{});
        ok(!w.isVisible(),"hide works");
        System.out.println("V10 ANALYSER GUI PASS "+count+" checks");System.exit(0);
    }
    static JButton findReset(java.awt.Container p){
        for(java.awt.Component c:p.getComponents()){
            if(c instanceof JButton b && "RESET".equals(b.getText()))return b;
            if(c instanceof java.awt.Container o){JButton b=findReset(o);if(b!=null)return b;}
        }
        return null;
    }
}
