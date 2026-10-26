package miragelab;
import java.lang.reflect.Field;
import java.nio.file.*;
import javax.swing.*;
import java.awt.*;
public final class OverlayV85SmokeTest {
    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) throw new AssertionError("Need a graphical display");
        HelperConfig.ensureCurrentFile();
        GameOverlay.launch();
        SwingUtilities.invokeAndWait(()->{});
        Field f=GameOverlay.class.getDeclaredField("window");f.setAccessible(true);
        Window w=(Window)f.get(null);
        if (w==null || !w.isVisible()) throw new AssertionError("Overlay did not become visible");
        if (!w.isAlwaysOnTop())throw new AssertionError("Not topmost");
        if(w.getWidth()<46||w.getHeight()<46)throw new AssertionError("Invalid dimensions");
        System.out.println("PASS overlay visible and topmost at "+w.getBounds());
        GameOverlay.hideForPanel(); SwingUtilities.invokeAndWait(()->{});
        if(w.isVisible())throw new AssertionError("Overlay did not hide for panel");
        GameOverlay.showOverlay(); SwingUtilities.invokeAndWait(()->{});
        if(!w.isVisible())throw new AssertionError("Overlay did not recover from panel");
        System.out.println("ALL PASS overlay visibility + recovery");
        System.exit(0);
    }
}
