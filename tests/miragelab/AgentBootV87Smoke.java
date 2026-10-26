package miragelab;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;
import java.awt.Window;
import javax.swing.SwingUtilities;
/** Smoke actual production .jar via -javaagent outside the real game. */
public final class AgentBootV87Smoke {
  public static void main(String[] args) throws Exception {
    if(GraphicsEnvironment.isHeadless())throw new AssertionError("Missing graphical display");
    SwingUtilities.invokeAndWait(()->{});
    Field f=GameOverlay.class.getDeclaredField("window"); f.setAccessible(true);
    Window win=(Window)f.get(null);
    if(win==null || !win.isVisible() || !win.isAlwaysOnTop())
       throw new AssertionError("Agent premain did not construct visible overlay");
    String version=HelperConfig.read().toString();
    System.out.println("PASS actual JAR premain transformer initialization + overlay; "+win.getBounds());
    System.exit(0);
  }
}
