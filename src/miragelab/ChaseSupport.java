package miragelab;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Optional obstacle-aware pursuit for Mirage Realms 0.8.59.
 * Plans A* on short local cardinal paths, never walks onto the target tile,
 * learns rejected steps, and keeps a fixed leash around the initial position.
 * Never runs while CaveBot is active. The game has final authority over moves.
 */
final class ChaseSupport {
    private static int targetId = Integer.MIN_VALUE;
    private static int anchorX, anchorY, previousX, previousY;
    private static Pathfinder.Pos attemptedStep;
    private static int failures;
    private static long lastStepAt = -1, blockedUntil;
    private static final Map<Pathfinder.Pos, Long> rejected = new HashMap<>();
    private ChaseSupport() { }

    // Retained for compatibility with older unit tests.
    static int[] direction(int px, int py, int tx, int ty) {
        int dx = tx - px, dy = ty - py;
        if (Math.max(Math.abs(dx), Math.abs(dy)) <= 1) return null;
        if (Math.abs(dx) >= Math.abs(dy)) return new int[]{Integer.signum(dx), 0};
        return new int[]{0, Integer.signum(dy)};
    }

    private static int cheb(int x1, int y1, int x2, int y2) {
        return Math.max(Math.abs(x1-x2), Math.abs(y1-y2));
    }

    /** Pure planner, also used for regression tests without launching the game. */
    static Pathfinder.Pos planNext(int px, int py, int tx, int ty, int ax, int ay,
                                   int leash, Set<Pathfinder.Pos> blocked) {
        int bound = Math.max(2, Math.min(14, leash));
        if (cheb(px,py,tx,ty) <= 1 || cheb(px,py,ax,ay) > bound) return null;
        Pathfinder.Pos start = new Pathfinder.Pos(px,py);
        Pathfinder.Pos enemy = new Pathfinder.Pos(tx,ty);
        Set<Pathfinder.Pos> occupied = new HashSet<>(blocked == null ? Set.of() : blocked);
        occupied.add(enemy);
        occupied.remove(start);
        Set<Pathfinder.Pos> goals = new HashSet<>();
        for (int x=tx-1;x<=tx+1;x++) for (int y=ty-1;y<=ty+1;y++) {
            Pathfinder.Pos p = new Pathfinder.Pos(x,y);
            if (!p.equals(enemy) && !occupied.contains(p)
                    && cheb(ax,ay,x,y) <= bound) goals.add(p);
        }
        if (goals.isEmpty()) return null;
        List<Pathfinder.Pos> route = Pathfinder.find(start, goals, occupied, 3, 560,
                p -> cheb(ax,ay,p.x(),p.y())<=bound);
        // Reject excessive detours even if technically inside the leash.
        if (route.size()<2 || route.size()>Math.max(7,2*bound+4)) return null;
        Pathfinder.Pos step=route.get(1);
        return Math.abs(step.x()-px)+Math.abs(step.y()-py)==1 ? step : null;
    }

    static void tick(Object game, int id, int px, int py, int tx, int ty,
                     int leash, long now) throws Exception {
        if (game==null || CaveBotEngine.isActive()) return;
        int boundedLeash=Math.max(2,Math.min(14,leash));
        if (targetId!=id) {
            reset();targetId=id;anchorX=px;anchorY=py;
        }
        if (cheb(px,py,tx,ty)<=1 || now<blockedUntil) return;
        if (cheb(anchorX,anchorY,px,py)>boundedLeash) {
            blockedUntil=now+7000L;return;
        }
        rejected.entrySet().removeIf(e->e.getValue()<=now);
        if (lastStepAt>=0) {
            if (now-lastStepAt<800L) return;
            if (px==previousX && py==previousY && attemptedStep!=null) {
                rejected.put(attemptedStep,now+20000L);
                if (++failures>=7) { blockedUntil=now+12000L;failures=0;return; }
            } else {
                failures=0;
            }
        }
        // Read-only local obstacle snapshot; do not run the full CaveBot/loot scanner.
        Set<Pathfinder.Pos> occupied = CaveBotEngine.chaseObstacles(game,px,py,boundedLeash+3,now);
        occupied.addAll(rejected.keySet());
        Pathfinder.Pos next=planNext(px,py,tx,ty,anchorX,anchorY,boundedLeash,occupied);
        if(next==null){blockedUntil=now+1500L;return;}
        CaveBotEngine.chaseSingleStep(game,px,py,next.x()-px,next.y()-py);
        attemptedStep=next;previousX=px;previousY=py;lastStepAt=now;
    }

    static void reset() {
        targetId=Integer.MIN_VALUE;lastStepAt=-1;blockedUntil=0;failures=0;
        attemptedStep=null;rejected.clear();
    }
}
