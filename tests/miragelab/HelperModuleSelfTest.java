package miragelab;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import java.util.List;

public final class HelperModuleSelfTest {
    public static void main(String[] args) throws Exception {
        int[] last = {-1, -1}; int[] downs={0}, ups={0};
        Gdx.input = () -> new InputProcessor() {
            public boolean keyDown(int k){ last[0]=k; downs[0]++; return true; }
            public boolean keyUp(int k){ last[1]=k; ups[0]++; return true; }
        };
        Object game = new HelperModuleSelfTest();
        require(HotkeySupport.press(game,"1") && last[0]==Input.Keys.NUM_1 && last[1]==Input.Keys.NUM_1,"hotkey-num1");
        require(HotkeySupport.press(game,"F2") && last[0]==Input.Keys.F2 && last[1]==Input.Keys.F2,"hotkey-f2");
        require(HotkeySupport.press(game,"SPACE") && last[0]==Input.Keys.SPACE && last[1]==Input.Keys.SPACE,"hotkey-space");
        require(!HotkeySupport.press(game,"CTRL+X"),"hotkey-invalid-rejected");
        HelperConfig.Config c=new HelperConfig.Config();c.inputGlobalGapMs=40;c.inputPulseMs=55;HelperConfig.write(c);InputArbiter.resetForTests();downs[0]=0;ups[0]=0;
        require(InputArbiter.press(game,"F2","pulse",1000)&&downs[0]==1&&ups[0]==0&&InputArbiter.isKeyHeldForTests(),"input-pulse-keydown-only");
        InputArbiter.tick(game,1040);require(ups[0]==0&&InputArbiter.isKeyHeldForTests(),"input-pulse-holds");
        InputArbiter.tick(game,1060);require(ups[0]==1&&!InputArbiter.isKeyHeldForTests(),"input-pulse-releases");
        require(InputArbiter.press(game,"F2","pulse2",1100),"input-pulse-repeatable");
        InputArbiter.tick(game,1160);require(ups[0]==2,"input-pulse-second-release");
        require(HealPolicy.shouldUse(35d,40,1000,850),"heal-policy-low-hp");
        require(!HealPolicy.shouldUse(55d,40,1000,850),"heal-policy-high-hp");
        require(!HealPolicy.shouldUse(35d,40,500,850),"heal-policy-cooldown");
        Integer farthest = TargetPriority.choose(HelperConfig.AttackPriority.FARTHEST,List.of(new TargetPriority.Candidate(1,9,90d,false),new TargetPriority.Candidate(2,3,90d,true)));
        require(farthest!=null&&farthest==1,"target-criterion-primary");
        System.out.println("ALL PASS 13/13");
    }
    static void require(boolean b,String n){if(!b)throw new AssertionError("FAIL "+n);System.out.println("PASS "+n);}
}
