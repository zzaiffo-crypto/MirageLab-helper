package miragelab;

import java.util.*;

/** Reproducible A* chase and XP-hook safety regression for V10.0.1 portable final. */
public final class MirageLabV101FinalFixTest {
    static int checks;
    static void ok(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    static Pathfinder.Pos pos(int x,int y){return new Pathfinder.Pos(x,y);}
    static Pathfinder.Pos next(int px,int py,int tx,int ty,int ax,int ay,int leash,Set<Pathfinder.Pos> blocked){
        return ChaseSupport.planNext(px,py,tx,ty,ax,ay,leash,blocked);
    }
    static void chaseCases(){
        ok(next(0,0,4,0,0,0,6,Set.of()).equals(pos(1,0)),"shortest straight chase");
        var walls=Set.of(pos(1,0),pos(2,0),pos(3,0));
        Pathfinder.Pos detour=next(0,0,4,0,0,0,6,walls);
        ok(detour!=null && Math.abs(detour.x())+Math.abs(detour.y())==1 && !walls.contains(detour),"detour around wall");
        ok(next(0,0,1,1,0,0,6,Set.of())==null,"already adjacent diagonal");
        ok(next(0,0,0,0,0,0,6,Set.of())==null,"never step onto target");
        ok(next(5,0,8,0,0,0,4,Set.of())==null,"fixed leash cap");
        ok(next(0,0,4,0,0,0,6,Set.of(pos(1,0),pos(-1,0),pos(0,1),pos(0,-1)))==null,"surrounded cannot path");
        ok(next(0,0,6,0,0,0,3,Set.of())==null,"target beyond fixed origin leash");
        var occupied=new HashSet<Pathfinder.Pos>();
        for(int x=1;x<=4;x++)occupied.add(pos(x,0));
        var step=next(0,0,6,0,0,0,9,occupied);
        ok(step!=null && !occupied.contains(step),"moving around dynamic occupants");
        for(int seed=0;seed<4000;seed++){
            Random r=new Random(seed*19L+7);
            int tx=2+r.nextInt(6), ty=r.nextInt(7)-3;
            Set<Pathfinder.Pos> b=new HashSet<>();
            for(int i=0;i<40;i++)b.add(pos(r.nextInt(15)-7,r.nextInt(15)-7));
            b.remove(pos(0,0));
            Pathfinder.Pos selected=next(0,0,tx,ty,0,0,8,b);
            if(selected!=null){
                ok(Math.abs(selected.x())+Math.abs(selected.y())==1,"cardinal only "+seed);
                ok(!b.contains(selected),"must not walk into obstacle "+seed);
                ok(!selected.equals(pos(tx,ty)),"never stand on target "+seed);
                ok(Math.max(Math.abs(selected.x()),Math.abs(selected.y()))<=8,"bounded step "+seed);
            }
        }
    }
    static void xpBridge(){
        AnalyzerEngine.reset();
        LabHook.recordExperienceEvent(15);
        LabHook.recordExperienceEvent(-20);
        LabHook.recordExperienceEvent(25);
        var s=AnalyzerEngine.snapshot(System.currentTimeMillis());
        ok(s.gainedXp()!=null && s.gainedXp()==40,"public XP bridge updates analyser");
        ok(s.xpEvents()==2,"negative XP ignored");
        try{
            var m=LabHook.class.getMethod("recordExperienceEvent",int.class);
            ok(java.lang.reflect.Modifier.isPublic(m.getModifiers()),"bridge is public for foreign packages");
            ok(java.lang.reflect.Modifier.isPublic(LabHook.class.getModifiers()),"bridge owner is public");
            ok(java.lang.reflect.Modifier.isPublic(LabHook.class.getMethod("safeTick",Object.class).getModifiers()),"render safety boundary public");
        }catch(Exception ex){throw new AssertionError(ex);}
    }
    public static void main(String[] args){chaseCases();xpBridge();System.out.println("V10.0.1 portable crash/chase PASS "+checks+" assertions");}
}
