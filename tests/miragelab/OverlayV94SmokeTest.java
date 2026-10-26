package miragelab;
import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;

/** Real Swing controls tested on virtual desktop; no game window needed. */
public final class OverlayV94SmokeTest {
    private static <T> T field(String name,Class<T> clazz)throws Exception{
        Field f=GameOverlay.class.getDeclaredField(name);f.setAccessible(true);return clazz.cast(f.get(null));
    }
    public static void main(String[] args)throws Exception{
        if(GraphicsEnvironment.isHeadless())throw new AssertionError("xvfb required");
        HelperConfig.ensureCurrentFile();GameOverlay.launch();SwingUtilities.invokeAndWait(()->{});
        JCheckBox heal=field("heal",JCheckBox.class), hp=field("hpHeal",JCheckBox.class), mp=field("mpHeal",JCheckBox.class);
        JSpinner hpTh=field("healHpThreshold",JSpinner.class), mpTh=field("healMpThreshold",JSpinner.class);
        JComboBox<?> hpKey=field("healHpKey",JComboBox.class), mpKey=field("healMpKey",JComboBox.class);
        if(heal==null||hp==null||mp==null)throw new AssertionError("heal settings missing");
        SwingUtilities.invokeAndWait(()->{
            if(!hp.isSelected()||mp.isSelected())throw new AssertionError("HP on and mana off by default");
            heal.doClick();hpTh.setValue(38);hpKey.setSelectedItem("3");
            mp.doClick();mpTh.setValue(20);mpKey.setSelectedItem("5");
        });
        HelperConfig.Config cfg=HelperConfig.read();
        if(!cfg.autoHealEnabled||!cfg.hpHealEnabled||!cfg.manaHealEnabled)throw new AssertionError("toggles not persisted");
        if(cfg.hpThreshold!=38||cfg.manaThreshold!=20||!cfg.hpHotkey.equals("3")||!cfg.manaHotkey.equals("5"))
            throw new AssertionError("thresholds/keys not persisted");
        SwingUtilities.invokeAndWait(()->mp.doClick());
        if(HelperConfig.read().manaHealEnabled)throw new AssertionError("mana can not be disabled");
        System.out.println("V9.4 OVERLAY HEAL PASS 10 checks");System.exit(0);
    }
}
