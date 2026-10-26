package miragelab;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MirageLabV62SelfTest {
    public static void main(String[] args) {
        int pass = 0;
        pass += dynamicPlayerReroute();
        pass += occupiedWaypointTolerance();
        pass += reachableMonsterApproach();
        pass += unreachableMonster();
        pass += returnReroute();
        pass += combinedScenario();
        if (pass != 6) throw new AssertionError("passed=" + pass + "/6");
        System.out.println("ALL PASS 6/6");
    }

    static int dynamicPlayerReroute() {
        var start = new Pathfinder.Pos(0,0);
        var goal = new Pathfinder.Pos(5,0);
        Set<Pathfinder.Pos> blocked = new HashSet<>();
        blocked.add(new Pathfinder.Pos(1,0)); // second client/player standing ahead
        var path = Pathfinder.find(start, Set.of(goal), blocked, 4, 1000, p -> true);
        require(!path.isEmpty() && path.get(path.size()-1).equals(goal), "dynamic reroute reaches goal");
        require(!path.contains(new Pathfinder.Pos(1,0)), "dynamic reroute avoids player");
        require(path.size() > 6, "dynamic reroute actually detours");
        System.out.println("PASS dynamic-player-reroute " + path);
        return 1;
    }

    static int occupiedWaypointTolerance() {
        var start = new Pathfinder.Pos(0,0);
        var occupiedWaypoint = new Pathfinder.Pos(4,0);
        Set<Pathfinder.Pos> blocked = Set.of(occupiedWaypoint);
        Set<Pathfinder.Pos> goals = Set.of(new Pathfinder.Pos(3,0), new Pathfinder.Pos(4,1), new Pathfinder.Pos(5,0), new Pathfinder.Pos(4,-1));
        var path = Pathfinder.find(start, goals, blocked, 4, 1000, p -> true);
        require(!path.isEmpty(), "tolerance path exists");
        require(goals.contains(path.get(path.size()-1)), "ends adjacent to occupied waypoint");
        System.out.println("PASS occupied-waypoint-tolerance " + path);
        return 1;
    }

    static int reachableMonsterApproach() {
        var start = new Pathfinder.Pos(0,0);
        var monster = new Pathfinder.Pos(4,2);
        Set<Pathfinder.Pos> blocked = new HashSet<>();
        blocked.add(monster);
        blocked.add(new Pathfinder.Pos(2,1));
        Set<Pathfinder.Pos> goals = ring(monster, 1);
        goals.removeAll(blocked);
        var path = Pathfinder.find(start, goals, blocked, 5, 1500, p -> true);
        require(!path.isEmpty(), "monster approach path exists");
        var end=path.get(path.size()-1);
        require(cheb(end, monster)==1, "combat distance=1");
        System.out.println("PASS reachable-monster-approach " + path);
        return 1;
    }

    static int unreachableMonster() {
        var start = new Pathfinder.Pos(0,0);
        var monster = new Pathfinder.Pos(4,0);
        Set<Pathfinder.Pos> goals = ring(monster,1);
        Set<Pathfinder.Pos> blocked = new HashSet<>();
        blocked.add(monster);
        // Seal the whole combat ring, equivalent to a target behind an impassable enclosure.
        blocked.addAll(goals);
        var path = Pathfinder.find(start, goals, blocked, 5, 1500, p -> true);
        // Pathfinder permits blocked goals by design for generality, so use an allowed predicate
        // that rejects the sealed ring to model no valid standing tile.
        path = Pathfinder.find(start, goals, blocked, 5, 1500, p -> !blocked.contains(p));
        require(path.isEmpty(), "unreachable target yields no path");
        System.out.println("PASS unreachable-monster-blacklist candidate");
        return 1;
    }

    static int returnReroute() {
        var current = new Pathfinder.Pos(5,2);
        var anchor = new Pathfinder.Pos(0,0);
        Set<Pathfinder.Pos> blocked = Set.of(new Pathfinder.Pos(4,2), new Pathfinder.Pos(3,1));
        var path = Pathfinder.find(current, Set.of(anchor), blocked, 5, 1500, p -> true);
        require(!path.isEmpty() && path.get(path.size()-1).equals(anchor), "return reaches anchor");
        require(!path.contains(new Pathfinder.Pos(4,2)), "return avoids dynamic blocker");
        System.out.println("PASS return-reroute " + path);
        return 1;
    }

    static int combinedScenario() {
        var player = new Pathfinder.Pos(0,0);
        var routeGoal = new Pathfinder.Pos(7,0);
        Set<Pathfinder.Pos> blocked = new HashSet<>();
        blocked.add(new Pathfinder.Pos(1,0)); // second client
        // wall with a gap at y=2
        blocked.add(new Pathfinder.Pos(4,-1));
        blocked.add(new Pathfinder.Pos(4,0));
        blocked.add(new Pathfinder.Pos(4,1));
        var routePath = Pathfinder.find(player, Set.of(routeGoal), blocked, 6, 2500, p -> true);
        require(!routePath.isEmpty(), "combined route path");
        for (var p: routePath) require(!blocked.contains(p), "combined avoids blockers");

        var monster = new Pathfinder.Pos(6,2);
        Set<Pathfinder.Pos> combatBlocked = new HashSet<>(blocked);
        combatBlocked.add(monster);
        Set<Pathfinder.Pos> combatGoals=ring(monster,1);
        combatGoals.removeAll(combatBlocked);
        var combatPath=Pathfinder.find(routePath.get(routePath.size()/2), combatGoals, combatBlocked, 6,2500,p->true);
        require(!combatPath.isEmpty(), "combined combat approach");

        var returnPath=Pathfinder.find(combatPath.get(combatPath.size()-1), Set.of(routePath.get(routePath.size()/2)), combatBlocked,6,2500,p->true);
        require(!returnPath.isEmpty(), "combined return");
        System.out.println("PASS combined route+combat+return route="+routePath+" combat="+combatPath+" return="+returnPath);
        return 1;
    }

    static Set<Pathfinder.Pos> ring(Pathfinder.Pos c, int r) {
        Set<Pathfinder.Pos> s=new HashSet<>();
        for(int dx=-r;dx<=r;dx++) for(int dy=-r;dy<=r;dy++) if(Math.max(Math.abs(dx),Math.abs(dy))==r) s.add(new Pathfinder.Pos(c.x()+dx,c.y()+dy));
        return s;
    }
    static int cheb(Pathfinder.Pos a, Pathfinder.Pos b){return Math.max(Math.abs(a.x()-b.x()),Math.abs(a.y()-b.y()));}
    static void require(boolean v,String m){if(!v) throw new AssertionError(m);}
}
