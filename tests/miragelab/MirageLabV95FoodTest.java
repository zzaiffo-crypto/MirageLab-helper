package miragelab;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
public final class MirageLabV95FoodTest {
    static int checks;
    static void ok(boolean c,String message){checks++;if(!c)throw new AssertionError(message+" at check "+checks);}
    public static void main(String[] args){
        AutoFoodSupport.resetForTests();
        ok(!AutoFoodSupport.validHotkey("2X"),"no unknown item keys");
        ok(AutoFoodSupport.validHotkey("1"),"food key 1 allowed");
        ok(!AutoFoodSupport.validHotkey("0"),"key zero forbidden");
        ok(!AutoFoodSupport.validHotkey(null),"null forbidden");
        ok(!AutoFoodSupport.safeCandidateKey("com.badlogic.gdx.scenes.scene2d.ui.Table#tableMinHeight"),"reject UI fields");
        ok(!AutoFoodSupport.safeCandidateKey("game#x"),"reject coordinate");
        ok(AutoFoodSupport.namedField("player#nourishment"),"semantic direct source");
        ok(!AutoFoodSupport.namedField("player#hp"),"HP not food");
        // Thousands of frames of arbitrary damage events must never infer nourishment.
        Random r=new Random(95);
        for(int i=0;i<2000;i++){
            double hp=Math.max(0,60-i%50-r.nextInt(3));
            Map<String,Double> values=Map.of("player#health",hp, "player#mana", (double)(i%45));
            AutoFoodSupport.ingest(values,1000+i*400L);
            ok(AutoFoodSupport.sensorKeyForTests()==null,"combat values not nutrient "+i);
        }
        AutoFoodSupport.resetForTests();
        for(int i=0;i<200;i++){
            AutoFoodSupport.ingest(Map.of("player#nourishment",(double)25),10000+i*400L);
            ok(AutoFoodSupport.sensorKeyForTests().equals("player#nourishment"),"named direct reading");
            ok(AutoFoodSupport.value(10000+i*400L)==25,"correct value");
        }
        ok(AutoFoodSupport.value(500000)==null,"stale fails closed");
        AutoFoodSupport.resetForTests();
        // Deterministic 60-second natural decay, WITHOUT a press or calibration.
        long time=1000;
        for(int minute=0;minute<4;minute++){
            for(int i=0;i<120;i++){
                AutoFoodSupport.ingest(Map.of("shared.Obfuscated#abc", (double)(25-minute),
                        "shared.Other#slot",(double)20),time);
                time+=500;
            }
        }
        ok(AutoFoodSupport.sensorKeyForTests()!=null,"natural nutrient trend detected");
        ok(AutoFoodSupport.value(time-500)==22,"nourishment after three drops");
        AutoFoodSupport.resetForTests();
        // Same decay from multiple unrelated fields -> ambiguity and no actions.
        time=1000;
        for(int minute=0;minute<4;minute++)for(int i=0;i<120;i++){
            AutoFoodSupport.ingest(Map.of("shared.Foo#a",(double)(25-minute),
                    "shared.Bar#b",(double)(30-minute)),time);time+=500;
        }
        ok(AutoFoodSupport.sensorKeyForTests()==null,"two candidates ambiguous");
        AutoFoodSupport.resetForTests();
        AutoFoodSupport.Result blocked=AutoFoodSupport.tick(null,null,true,30,10000,"1",1000000);
        ok(blocked.nourishment()==null && blocked.summary().contains("suspenso"),"does not press when unknown");
        AutoFoodSupport.Result off=AutoFoodSupport.tick(null,null,false,30,10000,"1",1000001);
        ok(off.summary().contains("desligado"),"disabled");
        // 5000 input-boundary tests: never eat on unknown, blocked or pending states.
        for(int i=0;i<5000;i++){
            Double v=i%13==0?null:(double)(i%61);
            boolean waiting=i%7==0,blockedAction=i%11==0;
            boolean decision=AutoFoodSupport.shouldEatPolicy(v,30,waiting,blockedAction,12000,10000);
            ok(!decision || (v!=null && v<30 && !waiting && !blockedAction),"policy refuses unsafe action");
        }
        System.out.println("V9.5 AUTOFOOD PASS "+checks+" checks");
    }
}
