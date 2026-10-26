package miragelab;
import javax.swing.*;
import java.awt.*;
import java.lang.reflect.*;
import java.nio.file.*;
/** Headed Xvfb test: genuine production overlay and stop-command integration. */
public final class OverlayV90SmokeTest {
    public static void main(String[] args) throws Exception{
        if(GraphicsEnvironment.isHeadless())throw new AssertionError("Xvfb required");
        HelperConfig.ensureCurrentFile();GameOverlay.launch();SwingUtilities.invokeAndWait(()->{});
        Field field=GameOverlay.class.getDeclaredField("window");field.setAccessible(true);
        Window w=(Window)field.get(null);if(w==null||!w.isVisible()||!w.isAlwaysOnTop())throw new AssertionError("initial GUI");
        JTabbedPane tabs=find(w,JTabbedPane.class);
        if(tabs==null||tabs.getTabCount()!=3)throw new AssertionError("tabs absent");
        if(!tabs.getTitleAt(0).equals("COMBATE")||!tabs.getTitleAt(1).equals("SUPORTE")||!tabs.getTitleAt(2).equals("STATUS"))
           throw new AssertionError("tab labels");
        int[] initial={0};SwingUtilities.invokeAndWait(()->{
            initial[0]=w.getHeight();GameOverlay.showPartyEditorForTests();
            if(!GameOverlay.partyEditorVisibleForTests()||w.getHeight()<=initial[0])throw new AssertionError("embedded party panel");
            GameOverlay.savePartyForTests("Ana, Bruno");GameOverlay.onlyAttackForTests();
            if(GameOverlay.partyEditorVisibleForTests())throw new AssertionError("party stays open");
        });
        HelperConfig.Config c=HelperConfig.read();
        if(!c.partyMembers.equals("Ana, Bruno")||!c.autoAttackEnabled||c.autoHealEnabled||c.autoSkillsEnabled)throw new AssertionError("party/preset state");
        SwingUtilities.invokeAndWait(GameOverlay::pauseForTests);
        c=HelperConfig.read();if(c.autoAttackEnabled||c.autoHealEnabled||c.autoSkillsEnabled||c.manaTrainingEnabled)throw new AssertionError("panic helper stop");
        Path root=Path.of(System.getProperty("user.home"),"Downloads","MirageLab","state");
        if(!Files.readString(root.resolve("cavebot.txt")).trim().equals("stop") || !Files.readString(root.resolve("playback.txt")).trim().equals("stop"))
           throw new AssertionError("panic cave/replay stop");
        GameOverlay.hideForPanel();SwingUtilities.invokeAndWait(()->{});if(w.isVisible())throw new AssertionError("hide");
        GameOverlay.showOverlay();SwingUtilities.invokeAndWait(()->{});if(!w.isVisible())throw new AssertionError("reappear");
        System.out.println("V9 GUI PASS tabs/party/presets/panic/visibility");System.exit(0);
    }
    @SuppressWarnings("unchecked") private static <T extends Component> T find(Container root,Class<T> target){
        if(target.isInstance(root))return (T)root;
        for(Component c:root.getComponents())if(c instanceof Container k){T found=find(k,target);if(found!=null)return found;}
        return null;
    }
}
