package miragelab;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Runs independently of CaveBot and never consumes food to learn nourishment. */
final class AutoFoodEngine {
    private static final String WORLD_GETTER="P17VtM6bkJVXJB65ftLh7dMon";
    private static final String PLAYER_GETTER="eACqBMENic3YHQLgkQTnYXFlcXmJ";
    private static final Path CONFIG=Path.of(System.getProperty("user.home"),"Downloads","MirageLab","state","cavebot_config.txt");
    private static long nextConfigAt, nextReadAt;private static Object player;
    private static boolean enabled;private static int threshold=30,cooldown=10000;
    private static String hotkey="1";private static String lastEvent="";
    private AutoFoodEngine(){}
    static void tick(Object game,long now){
        if(now>=nextConfigAt){nextConfigAt=now+1200;readConfig();}
        // Do not attempt reflection every frame, and never traverse the entire monster world.
        if(now>=nextReadAt){nextReadAt=now+400;Object found=readPlayer(game);if(found!=player){AutoFoodSupport.resetOnPlayerChange();player=found;}}
        AutoFoodSupport.Result r=AutoFoodSupport.tick(game,player,enabled,threshold,cooldown,hotkey,now);
        if(r.event()!=null&&!r.event().equals(lastEvent)){
            lastEvent=r.event();LabHook.status(r.event());
        }
    }
    static String summary(){return AutoFoodSupport.summary()+" | tecla="+hotkey+" | "+(enabled?"ATIVO":"OFF");}
    private static Object readPlayer(Object game){
        try{Method worldMethod=game.getClass().getMethod(WORLD_GETTER);
            Object world=worldMethod.invoke(game);if(world==null)return null;
            return world.getClass().getMethod(PLAYER_GETTER).invoke(world);
        }catch(Throwable ignored){return null;}
    }
    private static void readConfig(){
        Map<String,String> map=new HashMap<>();
        try{if(Files.exists(CONFIG))for(String row:Files.readAllLines(CONFIG,StandardCharsets.UTF_8)){
            String s=row.strip();int i=s.indexOf('=');if(i>0&&!s.startsWith("#"))map.put(s.substring(0,i).trim(),s.substring(i+1).trim());
        }}catch(Exception ignored){}
        enabled=Boolean.parseBoolean(map.getOrDefault("auto_food","false"));
        try{threshold=Math.max(1,Math.min(59,Integer.parseInt(map.getOrDefault("auto_food_threshold","30"))));}catch(Exception ignored){}
        try{cooldown=Math.max(10000,Math.min(60000,Integer.parseInt(map.getOrDefault("auto_food_cooldown_ms","10000"))));}catch(Exception ignored){}
        String proposed=map.getOrDefault("auto_food_hotkey","1");hotkey=AutoFoodSupport.validHotkey(proposed)?proposed:"1";
    }
}
