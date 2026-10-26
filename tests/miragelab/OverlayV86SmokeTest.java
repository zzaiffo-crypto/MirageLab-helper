package miragelab;
import java.awt.*;
import java.lang.reflect.Field;
import javax.swing.*;
public final class OverlayV86SmokeTest {
    public static void main(String[] args) throws Exception {
        if(GraphicsEnvironment.isHeadless())throw new AssertionError("Xvfb required");
        HelperConfig.ensureCurrentFile();
        GameOverlay.launch(); SwingUtilities.invokeAndWait(()->{});
        Field wField=GameOverlay.class.getDeclaredField("window");wField.setAccessible(true);
        Window w=(Window)wField.get(null);
        if(w==null||!w.isVisible()||!w.isAlwaysOnTop())throw new AssertionError("Overlay not visible/topmost");
        final int[] height={0};
        SwingUtilities.invokeAndWait(()->{
            height[0]=w.getHeight();
            GameOverlay.showPartyEditorForTests();
            if(!GameOverlay.partyEditorVisibleForTests())throw new AssertionError("party not inside overlay");
            if(w.getHeight()<=height[0])throw new AssertionError("editor not expanded");
            for(Window win : Window.getWindows())if(win!=w&&win.isVisible())throw new AssertionError("external dialog unexpectedly created");
            GameOverlay.savePartyForTests("Ladelya, Aliada");
            if(GameOverlay.partyEditorVisibleForTests())throw new AssertionError("editor not dismissed");
            GameOverlay.onlyAttackForTests();
        });
        HelperConfig.Config c=HelperConfig.read();
        if(!c.partyMembers.equals("Ladelya, Aliada"))throw new AssertionError("party list did not save");
        if(!c.autoAttackEnabled||!c.attackRetargetEnabled||c.autoSkillsEnabled||c.manaTrainingEnabled||c.autoHealEnabled)
            throw new AssertionError("only-attack mode did not disable automation modules");
        GameOverlay.hideForPanel();SwingUtilities.invokeAndWait(()->{});
        if(w.isVisible())throw new AssertionError("hide panel failed");
        GameOverlay.showOverlay();SwingUtilities.invokeAndWait(()->{});
        if(!w.isVisible())throw new AssertionError("overlay recovery failed");
        System.out.println("ALL PASS v8.6: overlay, inline party editor, persistence, only-attack, hide/show");
        System.exit(0);
    }
}
