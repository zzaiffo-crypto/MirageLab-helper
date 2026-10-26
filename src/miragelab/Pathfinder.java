package miragelab;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Predicate;

/** Small deterministic cardinal A* used by MirageLab v7.0. */
final class Pathfinder {
    record Pos(int x, int y) { }

    private record Node(Pos pos, int g, int f) { }

    private Pathfinder() { }

    static List<Pos> find(Pos start,
                          Set<Pos> goals,
                          Set<Pos> blocked,
                          int margin,
                          int maxNodes,
                          Predicate<Pos> allowed) {
        if (start == null || goals == null || goals.isEmpty()) return List.of();
        if (goals.contains(start)) return List.of(start);

        int minX = start.x, maxX = start.x, minY = start.y, maxY = start.y;
        for (Pos g : goals) {
            minX = Math.min(minX, g.x);
            maxX = Math.max(maxX, g.x);
            minY = Math.min(minY, g.y);
            maxY = Math.max(maxY, g.y);
        }
        final int loX = minX - Math.max(1, margin);
        final int hiX = maxX + Math.max(1, margin);
        final int loY = minY - Math.max(1, margin);
        final int hiY = maxY + Math.max(1, margin);

        Set<Pos> blockedSafe = blocked == null ? Set.of() : blocked;
        Predicate<Pos> allowedSafe = allowed == null ? p -> true : allowed;

        PriorityQueue<Node> open = new PriorityQueue<>(Comparator
                .comparingInt(Node::f)
                .thenComparingInt(Node::g)
                .thenComparingInt(n -> n.pos.x)
                .thenComparingInt(n -> n.pos.y));
        Map<Pos, Integer> gScore = new HashMap<>();
        Map<Pos, Pos> cameFrom = new HashMap<>();
        Set<Pos> closed = new HashSet<>();

        gScore.put(start, 0);
        open.add(new Node(start, 0, heuristic(start, goals)));

        int expanded = 0;
        while (!open.isEmpty() && expanded < Math.max(64, maxNodes)) {
            Node node = open.poll();
            Pos current = node.pos;
            if (!closed.add(current)) continue;
            expanded++;

            if (goals.contains(current)) {
                return reconstruct(cameFrom, current);
            }

            for (Pos next : neighbors(current)) {
                if (next.x < loX || next.x > hiX || next.y < loY || next.y > hiY) continue;
                if (!allowedSafe.test(next)) continue;
                if (!next.equals(start) && blockedSafe.contains(next)) continue;
                if (closed.contains(next)) continue;

                int tentative = node.g + 1;
                int old = gScore.getOrDefault(next, Integer.MAX_VALUE);
                if (tentative >= old) continue;

                cameFrom.put(next, current);
                gScore.put(next, tentative);
                open.add(new Node(next, tentative, tentative + heuristic(next, goals)));
            }
        }
        return List.of();
    }

    private static int heuristic(Pos p, Set<Pos> goals) {
        int best = Integer.MAX_VALUE;
        for (Pos g : goals) {
            int h = Math.abs(p.x - g.x) + Math.abs(p.y - g.y);
            if (h < best) best = h;
        }
        return best == Integer.MAX_VALUE ? 0 : best;
    }

    private static List<Pos> neighbors(Pos p) {
        // Stable order makes tests/replays deterministic.
        return List.of(
                new Pos(p.x, p.y - 1),
                new Pos(p.x + 1, p.y),
                new Pos(p.x, p.y + 1),
                new Pos(p.x - 1, p.y));
    }

    private static List<Pos> reconstruct(Map<Pos, Pos> cameFrom, Pos end) {
        List<Pos> out = new ArrayList<>();
        Pos p = end;
        out.add(p);
        while (cameFrom.containsKey(p)) {
            p = cameFrom.get(p);
            out.add(p);
        }
        Collections.reverse(out);
        return out;
    }
}
