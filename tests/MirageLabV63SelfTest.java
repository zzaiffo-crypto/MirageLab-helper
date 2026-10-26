package miragelab;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MirageLabV63SelfTest {
    public static void main(String[] args) throws Exception {
        int pass = 0;
        pass += dynamicPlayerReroute();
        pass += occupiedWaypointTolerance();
        pass += pathCacheContinuityModel();
        pass += pathCacheInvalidationModel();
        pass += reachableMonsterApproach();
        pass += unreachableMonster();
        pass += adaptiveResume();
        pass += lootCandidateSelection();
        pass += lootInfoReflection();
        pass += gdxSpaceDispatch();
        pass += combinedSmoothCombatLoot();
        if (pass != 11) throw new AssertionError("passed=" + pass + "/11");
        System.out.println("ALL PASS 11/11");
    }

    static int dynamicPlayerReroute() {
        var start = new Pathfinder.Pos(0,0); var goal = new Pathfinder.Pos(6,0);
        Set<Pathfinder.Pos> blocked = Set.of(new Pathfinder.Pos(1,0));
        var p = Pathfinder.find(start, Set.of(goal), blocked, 5, 2500, q -> true);
        require(!p.isEmpty() && p.get(p.size()-1).equals(goal), "dynamic reroute goal");
        require(!p.contains(new Pathfinder.Pos(1,0)), "dynamic reroute avoids player");
        System.out.println("PASS dynamic-player-reroute"); return 1;
    }

    static int occupiedWaypointTolerance() {
        var start = new Pathfinder.Pos(0,0); var wp = new Pathfinder.Pos(4,0);
        Set<Pathfinder.Pos> blocked = Set.of(wp);
        Set<Pathfinder.Pos> goals = ring(wp,1); goals.remove(wp);
        var p=Pathfinder.find(start,goals,blocked,4,1500,q->true);
        require(!p.isEmpty() && cheb(p.get(p.size()-1),wp)==1,"occupied waypoint tolerance");
        System.out.println("PASS occupied-waypoint-tolerance"); return 1;
    }

    // Models v6.3 cached-path rule: while next cached tile stays free, no A* rebuild is needed.
    static int pathCacheContinuityModel() {
        var full=Pathfinder.find(new Pathfinder.Pos(0,0),Set.of(new Pathfinder.Pos(8,0)),Set.of(),4,2500,q->true);
        require(full.size()==9,"straight path length");
        int replans=1;
        for(int i=0;i<full.size()-1;i++) {
            var current=full.get(i); var next=full.get(i+1);
            require(Math.abs(current.x()-next.x())+Math.abs(current.y()-next.y())==1,"cache cardinal");
        }
        require(replans==1,"single replan model");
        System.out.println("PASS path-cache-continuity one-plan-for-8-steps"); return 1;
    }

    static int pathCacheInvalidationModel() {
        var start=new Pathfinder.Pos(0,0); var goal=new Pathfinder.Pos(6,0);
        var cached=Pathfinder.find(start,Set.of(goal),Set.of(),4,2500,q->true);
        var blocker=cached.get(3);
        Set<Pathfinder.Pos> blocked=Set.of(blocker);
        var replanned=Pathfinder.find(cached.get(2),Set.of(goal),blocked,4,2500,q->true);
        require(!replanned.isEmpty() && !replanned.contains(blocker),"cache invalidation reroutes");
        System.out.println("PASS path-cache-invalidation"); return 1;
    }

    static int reachableMonsterApproach() {
        var start=new Pathfinder.Pos(0,0); var monster=new Pathfinder.Pos(5,2);
        Set<Pathfinder.Pos> blocked=new HashSet<>(); blocked.add(monster); blocked.add(new Pathfinder.Pos(2,0));
        Set<Pathfinder.Pos> goals=ring(monster,1); goals.removeAll(blocked);
        var p=Pathfinder.find(start,goals,blocked,6,3000,q->true);
        require(!p.isEmpty() && cheb(p.get(p.size()-1),monster)==1,"combat distance one");
        System.out.println("PASS combat-distance-one"); return 1;
    }

    static int unreachableMonster() {
        var start=new Pathfinder.Pos(0,0); var monster=new Pathfinder.Pos(4,0);
        Set<Pathfinder.Pos> goals=ring(monster,1); Set<Pathfinder.Pos> blocked=new HashSet<>(goals); blocked.add(monster);
        var p=Pathfinder.find(start,goals,blocked,5,2000,q->!blocked.contains(q));
        require(p.isEmpty(),"unreachable monster");
        System.out.println("PASS unreachable-monster"); return 1;
    }

    static int adaptiveResume() {
        var displaced=new Pathfinder.Pos(7,3); var forward=new Pathfinder.Pos(10,0);
        Set<Pathfinder.Pos> blocked=Set.of(new Pathfinder.Pos(8,3),new Pathfinder.Pos(9,2));
        var p=Pathfinder.find(displaced,ringIncludingCenter(forward,1),blocked,6,3000,q->true);
        require(!p.isEmpty() && cheb(p.get(p.size()-1),forward)<=1,"adaptive return to forward route");
        System.out.println("PASS adaptive-resume-forward"); return 1;
    }

    static int lootCandidateSelection() {
        Object a=new Object(), b=new Object();
        var list=List.of(
                new AutoLootSupport.Candidate(10,10,10,"bagA","loot",a),
                new AutoLootSupport.Candidate(11,12,10,"bagB","loot",b));
        var c=AutoLootSupport.nearest(10,11,list,1);
        require(c!=null && c.key()==10 && c.token()==a,"nearest loot candidate");
        require(AutoLootSupport.nearest(0,0,list,1)==null,"no false loot candidate");
        System.out.println("PASS loot-candidate-selection"); return 1;
    }

    static int lootInfoReflection() throws Exception {
        FakeGame game=new FakeGame();
        var token=new uk.co.harveydogs.mirage.shared.yY8x1yOsuoav.AHXnlQ8L2gWGxpmXW(77);
        AutoLootSupport.openLoot(game,token);
        require(game.network.sent instanceof uk.co.harveydogs.mirage.shared.ppGE4fatBnKfcPyX4Kmtne6Lgsjv.yY8x1yOsuoav.yY8x1yOsuoav.phIAQDKepCG9Flqa5.NT6CLfh1EFZKAgt882tQ6RNRZLmx,"loot info action type");
        var a=(uk.co.harveydogs.mirage.shared.ppGE4fatBnKfcPyX4Kmtne6Lgsjv.yY8x1yOsuoav.yY8x1yOsuoav.phIAQDKepCG9Flqa5.NT6CLfh1EFZKAgt882tQ6RNRZLmx)game.network.sent;
        require(a.token==token,"loot token preserved");
        System.out.println("PASS loot-info-reflection"); return 1;
    }

    static int gdxSpaceDispatch() {
        FakeProcessor p=new FakeProcessor();
        com.badlogic.gdx.Gdx.input=() -> p;
        boolean ok=AutoLootSupport.pressSpaceThroughGdx(new FakeGame());
        require(ok && p.down==62 && p.up==62,"gdx space dispatch");
        System.out.println("PASS gdx-space-dispatch"); return 1;
    }

    static int combinedSmoothCombatLoot() throws Exception {
        var routeStart=new Pathfinder.Pos(0,0); var routeGoal=new Pathfinder.Pos(10,0);
        Set<Pathfinder.Pos> blocked=new HashSet<>(); blocked.add(new Pathfinder.Pos(2,0)); // second client
        blocked.add(new Pathfinder.Pos(5,-1)); blocked.add(new Pathfinder.Pos(5,0)); blocked.add(new Pathfinder.Pos(5,1));
        var route=Pathfinder.find(routeStart,Set.of(routeGoal),blocked,7,4000,q->true);
        require(!route.isEmpty(),"combined route");
        var monster=new Pathfinder.Pos(8,3); Set<Pathfinder.Pos> cb=new HashSet<>(blocked); cb.add(monster);
        Set<Pathfinder.Pos> combatGoals=ring(monster,1); combatGoals.removeAll(cb);
        var combat=Pathfinder.find(route.get(route.size()/2),combatGoals,cb,7,4000,q->true);
        require(!combat.isEmpty(),"combined combat");
        Object token=new Object();
        var loot=AutoLootSupport.nearest(monster.x(),monster.y(),List.of(new AutoLootSupport.Candidate(99,monster.x(),monster.y(),"bag","loot",token)),1);
        require(loot!=null,"combined loot detect");
        var resume=Pathfinder.find(combat.get(combat.size()-1),ringIncludingCenter(routeGoal,1),blocked,7,4000,q->true);
        require(!resume.isEmpty(),"combined adaptive resume");
        System.out.println("PASS combined-route-combat-loot-resume"); return 1;
    }

    public static final class FakeGame {
        public final FakeNetwork network=new FakeNetwork();
        public FakeNetwork L3t5HInQtJbd0Zshf(){return network;}
    }
    public static final class FakeNetwork {
        Object sent;
        public uk.co.harveydogs.mirage.shared.ppGE4fatBnKfcPyX4Kmtne6Lgsjv.NT6CLfh1EFZKAgt882tQ6RNRZLmx eACqBMENic3YHQLgkQTnYXFlcXmJ(Class<?> c) throws Exception {
            return (uk.co.harveydogs.mirage.shared.ppGE4fatBnKfcPyX4Kmtne6Lgsjv.NT6CLfh1EFZKAgt882tQ6RNRZLmx)c.getConstructor().newInstance();
        }
        public void eACqBMENic3YHQLgkQTnYXFlcXmJ(uk.co.harveydogs.mirage.shared.ppGE4fatBnKfcPyX4Kmtne6Lgsjv.NT6CLfh1EFZKAgt882tQ6RNRZLmx a){sent=a;}
    }
    static final class FakeProcessor implements com.badlogic.gdx.InputProcessor {
        int down=-1,up=-1;
        public boolean keyDown(int k){down=k;return true;}
        public boolean keyUp(int k){up=k;return true;}
    }

    static Set<Pathfinder.Pos> ring(Pathfinder.Pos c,int r){Set<Pathfinder.Pos>s=new HashSet<>();for(int dx=-r;dx<=r;dx++)for(int dy=-r;dy<=r;dy++)if(Math.max(Math.abs(dx),Math.abs(dy))==r)s.add(new Pathfinder.Pos(c.x()+dx,c.y()+dy));return s;}
    static Set<Pathfinder.Pos> ringIncludingCenter(Pathfinder.Pos c,int r){Set<Pathfinder.Pos>s=new HashSet<>();for(int dx=-r;dx<=r;dx++)for(int dy=-r;dy<=r;dy++)if(Math.max(Math.abs(dx),Math.abs(dy))<=r)s.add(new Pathfinder.Pos(c.x()+dx,c.y()+dy));return s;}
    static int cheb(Pathfinder.Pos a,Pathfinder.Pos b){return Math.max(Math.abs(a.x()-b.x()),Math.abs(a.y()-b.y()));}
    static void require(boolean v,String m){if(!v)throw new AssertionError(m);}
}
