package miragelab;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MirageLab advanced route/combat controller for the user's local/test environment.
 *
 * Goals:
 *  - replay a recorded tile route;
 *  - never advance the route while an eligible monster is nearby;
 *  - select/keep one target at a time;
 *  - prefer one-tile combat distance;
 *  - remember every tile displacement during combat and retrace it back to the route;
 *  - resume the exact route index after combat;
 *  - expose behavior through simple text config/status files.
 */
final class CaveBotEngine {
    private static final String VERSION = "8.0";

    private static final String WORLD_GETTER = "P17VtM6bkJVXJB65ftLh7dMon";
    private static final String PLAYER_ENTITY_GETTER = "eACqBMENic3YHQLgkQTnYXFlcXmJ";

    private static final String IDENTITY_CLASS =
            "uk.co.harveydogs.mirage.shared.yY8x1yOsuoav.D4yZmsobaWAL";
    private static final String TYPE_FIELD = "eACqBMENic3YHQLgkQTnYXFlcXmJ";
    private static final String ID_FIELD = "FIeR9ZxfSOyX";
    private static final String NAME_FIELD = "WVfgbdbcCRRmDq1CfPzsI1qtv";

    private static final String POSITION_CLASS =
            "uk.co.harveydogs.mirage.shared.yY8x1yOsuoav.CzFlqirRObv";
    private static final String POS_X_FIELD = "eACqBMENic3YHQLgkQTnYXFlcXmJ";
    private static final String POS_Y_FIELD = "FIeR9ZxfSOyX";

    private static final String ACTION_BASE_CLASS =
            "uk.co.harveydogs.mirage.shared.ppGE4fatBnKfcPyX4Kmtne6Lgsjv.NT6CLfh1EFZKAgt882tQ6RNRZLmx";
    private static final String ACTION_FACTORY_METHOD = "eACqBMENic3YHQLgkQTnYXFlcXmJ";
    private static final String SEND_ACTION_METHOD = "eACqBMENic3YHQLgkQTnYXFlcXmJ";

    private static final String MOVE_ACTION_CLASS =
            "uk.co.harveydogs.mirage.shared.ppGE4fatBnKfcPyX4Kmtne6Lgsjv.yY8x1yOsuoav.ppGE4fatBnKfcPyX4Kmtne6Lgsjv.AjEyNvkjjLUdT";
    private static final String MOVE_FROM_X_FIELD = "eACqBMENic3YHQLgkQTnYXFlcXmJ";
    private static final String MOVE_FROM_Y_FIELD = "FIeR9ZxfSOyX";
    private static final String MOVE_DIRECTION_FIELD = "WVfgbdbcCRRmDq1CfPzsI1qtv";
    private static final String MOVE_DIRECTION_ENUM =
            "uk.co.harveydogs.mirage.shared.VlQcXfgpCFhm0FC60ki4o.hvodiLOEjqktduzhy0jtsVYZUcAU";

    private static final String SET_TARGET_CLASS =
            "uk.co.harveydogs.mirage.shared.ppGE4fatBnKfcPyX4Kmtne6Lgsjv.yY8x1yOsuoav.yY8x1yOsuoav.BqDbTihL38.NT6CLfh1EFZKAgt882tQ6RNRZLmx";
    private static final String SET_TARGET_MODE_CLASS = SET_TARGET_CLASS + "$NT6CLfh1EFZKAgt882tQ6RNRZLmx";
    private static final String SET_TARGET_CONFIGURE_METHOD = "eACqBMENic3YHQLgkQTnYXFlcXmJ";
    private static final String AGGRESSIVE_FIELD = "FIeR9ZxfSOyX";

    private static final String LOOT_TOKEN_COMPONENT_CLASS = AutoLootSupport.LOOT_TOKEN_COMPONENT_CLASS;
    private static final String TARGET_COMPONENT_CLASS =
            "uk.co.harveydogs.mirage.client.GEcHnxVxWL24.yY8x1yOsuoav.yY8x1yOsuoav.hvodiLOEjqktduzhy0jtsVYZUcAU";
    private static final String TARGET_COMPONENT_GETTER = "FIeR9ZxfSOyX";

    private static final Path LAB_DIR = Path.of(System.getProperty("user.home"), "Downloads", "MirageLab");
    private static final Path STATE_DIR = LAB_DIR.resolve("state");
    private static final Path RUNTIME_DIR = LAB_DIR.resolve("runtime");
    private static final Path ROUTE_FILE = LAB_DIR.resolve("routes").resolve("route_current.txt");
    private static final Path CONTROL_FILE = STATE_DIR.resolve("cavebot.txt");
    private static final Path CONFIG_FILE = STATE_DIR.resolve("cavebot_config.txt");
    private static final Path STATUS_FILE = RUNTIME_DIR.resolve("cavebot_status.txt");
    private static final Path EVENTS_FILE = RUNTIME_DIR.resolve("cavebot_events.txt");

    private static boolean active = false;
    private static String lastCommand = null;
    private static State state = State.STOPPED;
    private static Config config = new Config();
    private static long nextConfigReadAt = 0L;
    private static long nextWorldReadAt = 0L;
    private static long nextStatusWriteAt = 0L;
    private static WorldState cachedWorld = null;

    private static List<Position> route = List.of();
    private static int routeIndex = 0;
    private static int completedLoops = 0;

    private static PendingMove pendingMove = null;
    private static PendingRouteBurst pendingRouteBurst = null;
    private static long lastMoveSentAt = 0L;
    private static long nextFastPlayerReadAt = 0L;
    private static FastPositionAccess fastPositionAccess = null;

    private static Integer targetId = null;
    private static String targetName = null;
    private static Position combatAnchor = null;
    private static final List<Position> combatTrail = new ArrayList<>();
    private static Position lastCombatObservedPosition = null;
    private static long targetSelectedAt = 0L;
    private static long lastCombatProgressAt = 0L;
    private static int lastTargetDistance = Integer.MAX_VALUE;
    private static int targetBlockedMoves = 0;
    private static final Set<Position> blockedCombatSteps = new HashSet<>();
    private static final Map<Integer, Long> unreachableUntil = new HashMap<>();
    private static final Map<Integer, Position> unreachableAtPosition = new HashMap<>();
    private static final Map<Integer, Long> contestedUntil = new HashMap<>();
    private static final Map<Position, Long> learnedBlockedUntil = new HashMap<>();
    private static long routeNoPathSince = 0L;
    private static int routeReplans = 0;

    // v6.4 smooth navigation: retain planned paths until something actually invalidates them.
    private static List<Position> routePathCache = List.of();
    private static int routePathTargetIndex = -1;
    private static List<Position> lootPathCache = List.of();
    private static List<Position> loopPathCache = List.of();
    private static boolean loopClosing = false;

    // v6.4 AutoLoot: corpse queue -> loot entity -> CALLBACK_LOOT_INFO -> one guarded SPACE.
    private static final Deque<LootRequest> lootQueue = new ArrayDeque<>();
    private static LootRequest currentLoot = null;
    private static Position lastTargetPosition = null;
    private static long lootOpenedAt = 0L;
    private static long lootSpaceSentAt = 0L;
    private static int lootAttempts = 0;
    private static boolean lootSpaceSent = false;
    private static boolean autoLootSessionDisabled = false;
    private static int lootedBags = 0;
    private static int skippedLootBags = 0;
    private static String autoFoodSummary = "food=INIT";

    private static String lastStatus = "";
    private static String lastEvent = "";

    private CaveBotEngine() {}

    static boolean isActive() {
        return active;
    }

    static boolean isInCombat() {
        return active && (state == State.COMBAT || targetId != null);
    }

    static Integer activeTargetId() { return targetId; }

    static void tick(Object game, long now) {
        try {
            ensureFiles();
            processControl(game);
            if (!active) {
                return;
            }
            if (LabHook.isRecordingActiveForCaveBot()) {
                stop("ABORTADO - Recorder foi ativado durante o CaveBot");
                return;
            }

            if (now >= nextConfigReadAt) {
                nextConfigReadAt = now + 1000L;
                config = readConfig();
            }
            if (now >= nextWorldReadAt || cachedWorld == null) {
                nextWorldReadAt = now + Math.max(35L, config.worldPollMs);
                cachedWorld = scanWorld(game);
            }
            if (cachedWorld != null && cachedWorld.playerEntity != null && now >= nextFastPlayerReadAt) {
                nextFastPlayerReadAt = now + Math.max(8L, config.fastPlayerPollMs);
                Position fast = readPlayerPositionFast(cachedWorld.playerEntity, game.getClass().getClassLoader());
                if (fast != null && !fast.equals(cachedWorld.player)) cachedWorld = withPlayer(cachedWorld, fast);
            }
            WorldState world = cachedWorld;
            if (world == null || world.player == null) {
                updateStatus(now, "AGUARDANDO PLAYER - estado=" + state);
                return;
            }

            autoFoodSummary = AutoFoodEngine.summary();

            if (state == State.COMBAT || state == State.RETURNING) {
                trackCombatDisplacement(world.player);
            }

            if (pendingRouteBurst != null) {
                // Combat can preempt a route burst immediately; do not finish an old movement
                // queue while an eligible monster has appeared.
                if (state == State.ROUTE && config.combatEnabled
                        && nearestEligibleMonster(world, world.player, null, now) != null) {
                    pendingRouteBurst = null; routePathCache = List.of(); routePathTargetIndex = -1; loopPathCache = List.of();
                } else {
                    if (!processPendingRouteBurst(game, world, now)) return;
                    Position fast = readPlayerPositionFast(world.playerEntity, game.getClass().getClassLoader());
                    if (fast != null) { cachedWorld = withPlayer(world, fast); world = cachedWorld; }
                }
            }
            if (pendingMove != null) {
                if (!processPendingMove(game, world, now)) {
                    return;
                }
                Position fast = readPlayerPositionFast(world.playerEntity, game.getClass().getClassLoader());
                if (fast != null) { cachedWorld = withPlayer(world, fast); world = cachedWorld; }
                if (world == null || world.player == null) return;
            }

            switch (state) {
                case ROUTE -> processRoute(game, world, now);
                case COMBAT -> processCombat(game, world, now);
                case LOOTING -> processLooting(game, world, now);
                case RETURNING -> processReturning(game, world, now);
                case STOPPED -> { }
            }
        } catch (Throwable t) {
            event("ERRO FATAL: " + t);
            stop("ERRO FATAL - " + t.getClass().getSimpleName() + ": " + safeMessage(t));
        }
    }

    private static void processControl(Object game) throws Exception {
        String command = normalize(Files.readString(CONTROL_FILE, StandardCharsets.UTF_8));
        if (command.equals(lastCommand)) return;
        lastCommand = command;

        if (command.startsWith("start") || command.startsWith("on") || command.startsWith("play")) {
            start(game);
        } else if (command.startsWith("stop") || command.startsWith("off")) {
            stop("PARADO PELO USUARIO");
        }
    }

    private static void start(Object game) throws Exception {
        if (LabHook.isRecordingActiveForCaveBot()) {
            writeStatus("NAO INICIADO - pare a gravacao primeiro");
            return;
        }

        config = readConfig();
        List<Position> loaded = loadRoute();
        if (loaded.size() < 2) {
            writeStatus("NAO INICIADO - route_current.txt precisa ter pelo menos 2 pontos");
            return;
        }

        WorldState world = scanWorld(game);
        if (world == null || world.player == null) {
            writeStatus("NAO INICIADO - posicao do player indisponivel");
            return;
        }

        int index = findNearestRouteIndex(loaded, world.player, config.routeJoinRadius);
        if (index < 0) {
            writeStatus("NAO INICIADO - player precisa estar ate " + config.routeJoinRadius
                    + " tiles da rota; atual=" + fmt(world.player) + " inicio=" + fmt(loaded.get(0)));
            return;
        }

        route = loaded;
        routeIndex = index;
        completedLoops = 0;
        pendingMove = null;
        pendingRouteBurst = null;
        loopClosing = false; loopPathCache = List.of();
        targetId = null;
        targetName = null;
        combatAnchor = null;
        combatTrail.clear();
        blockedCombatSteps.clear();
        unreachableUntil.clear();
        unreachableAtPosition.clear();
        contestedUntil.clear();
        learnedBlockedUntil.clear();
        routeNoPathSince = 0L;
        routeReplans = 0;
        routePathCache = List.of();
        routePathTargetIndex = -1;
        lootPathCache = List.of();
        lootQueue.clear();
        currentLoot = null;
        lastTargetPosition = null;
        lootOpenedAt = 0L;
        lootSpaceSentAt = 0L;
        lootAttempts = 0;
        lootSpaceSent = false;
        autoLootSessionDisabled = false;
        lootedBags = 0;
        skippedLootBags = 0;
        targetBlockedMoves = 0;
        lastTargetDistance = Integer.MAX_VALUE;
        lastCombatObservedPosition = null;
        state = State.ROUTE;
        active = true;
        cachedWorld = world;
        event("START - pontos=" + route.size() + " indice=" + routeIndex + " player=" + fmt(world.player)
                + " detection=" + config.detectionRange + " combat_distance=" + config.combatDistance
                + " loop=" + config.loopRoute);
        writeStatus("INICIADO - ROUTE " + (routeIndex + 1) + "/" + route.size()
                + " player=" + fmt(world.player));
    }

    private static void stop(String reason) {
        boolean wasActive = active;
        active = false;
        state = State.STOPPED;
        pendingMove = null;
        pendingRouteBurst = null;
        loopClosing = false; loopPathCache = List.of();
        targetId = null;
        targetName = null;
        combatAnchor = null;
        combatTrail.clear();
        blockedCombatSteps.clear();
        unreachableUntil.clear();
        unreachableAtPosition.clear();
        routePathCache = List.of();
        routePathTargetIndex = -1;
        lootPathCache = List.of();
        lootQueue.clear();
        currentLoot = null;
        lastTargetPosition = null;
        lootSpaceSent = false;
        targetBlockedMoves = 0;
        lastTargetDistance = Integer.MAX_VALUE;
        lastCombatObservedPosition = null;
        writeStatus(reason);
        if (wasActive) event(reason);
    }

    private static void processRoute(Object game, WorldState world, long now) throws Exception {
        Position current = world.player;
        cleanupTemporaryMaps(now);

        // Combat always has priority over route progression.
        Monster monster = nearestEligibleMonster(world, current, null, now);
        if (config.combatEnabled && monster != null) {
            beginCombat(game, world, monster, now, true);
            return;
        }

        int last = route.size() - 1;
        if (last < 1) {
            stop("ROTA INVALIDA - menos de 2 pontos");
            return;
        }

        // If we are close to forward route anchors, advance without demanding the exact recorded tile.
        int advanced = bestForwardRouteIndex(current, routeIndex, config.routeLookahead, config.routeWaypointTolerance);
        if (advanced > routeIndex) {
            routeIndex = advanced;
            routeNoPathSince = 0L;
        }

        if (routeIndex >= last) {
            if (config.loopRoute) {
                if (cheb(current, route.get(0)) <= config.routeWaypointTolerance) {
                    routeIndex = 0; loopClosing = false; loopPathCache = List.of();
                    completedLoops++;
                    event("LOOP " + completedLoops + " concluido; reiniciando rota");
                } else {
                    loopClosing = true;
                    processLoopClosure(game, world, now);
                    return;
                }
            } else {
                stop("CONCLUIDO - fim da rota em " + fmt(current));
                return;
            }
        }

        Position next = cachedRouteStep(world, current, now);
        int targetIndex = routePathTargetIndex;
        boolean replanned = false;
        if (next == null) {
            PathChoice choice = chooseRoutePath(world, current, now);
            if (choice == null || choice.path.size() < 2) {
                routePathCache = List.of();
                routePathTargetIndex = -1;
                if (routeNoPathSince == 0L) routeNoPathSince = now;
                updateStatus(now, "ROUTE AGUARDANDO CAMINHO - indice=" + routeIndex
                        + " player=" + fmt(current)
                        + " bloqueios_dinamicos=" + world.occupied.size()
                        + " aprendidos=" + learnedBlockedUntil.size()
                        + " aguardando=" + (now - routeNoPathSince) + "ms");
                return;
            }
            routeNoPathSince = 0L;
            routeReplans++;
            routePathCache = List.copyOf(choice.path);
            routePathTargetIndex = choice.targetIndex;
            targetIndex = choice.targetIndex;
            next = choice.path.get(1);
            replanned = true;
        }

        List<Position> burst = buildRouteBurst(world, current, now);
        if (burst.isEmpty()) burst = List.of(next);
        sendRouteBurst(game, current, burst);
        pendingRouteBurst = new PendingRouteBurst(current, List.copyOf(burst), now, now, 0);
        lastMoveSentAt = now;
        updateStatus(now, (replanned ? "ROUTE A*" : "ROUTE CACHE") + " idx=" + routeIndex + " alvo=" + targetIndex
                + " waypoint=" + (targetIndex >= 0 && targetIndex < route.size() ? fmt(route.get(targetIndex)) : "-")
                + " path=" + routePathCache.size() + " burst=" + burst.size()
                + " " + fmt(current) + " -> " + fmt(burst.get(burst.size()-1)));
    }

    private static List<Position> buildRouteBurst(WorldState world, Position current, long now) {
        if (routePathCache == null || routePathCache.size() < 2) return List.of();
        int idx = indexOfPosition(routePathCache, current);
        if (idx < 0) return List.of();
        int max = Math.max(1, config.routeBurstSize);
        List<Position> out = new ArrayList<>();
        Position from = current;
        for (int i = idx + 1; i < routePathCache.size() && out.size() < max; i++) {
            Position p = routePathCache.get(i);
            if (isBlockedForPath(world, p, from, now)) break;
            if (Direction.from(from, p) == null) break;
            out.add(p);
            from = p;
        }
        return out;
    }

    private static void sendRouteBurst(Object game, Position from, List<Position> steps) throws Exception {
        Position cursor = from;
        for (Position step : steps) {
            Direction d = Direction.from(cursor, step);
            if (d == null) throw new IllegalStateException("burst invalido " + fmt(cursor) + " -> " + fmt(step));
            sendMove(game, d, cursor);
            cursor = step;
        }
    }

    private static boolean processPendingRouteBurst(Object game, WorldState world, long now) throws Exception {
        PendingRouteBurst b = pendingRouteBurst;
        if (b == null) return true;
        Position current = world.player;
        if (current == null) return false;
        Position finalStep = b.steps.get(b.steps.size() - 1);
        if (current.equals(finalStep)) {
            for (Position p : b.steps) learnedBlockedUntil.remove(p);
            pendingRouteBurst = null;
            routeNoPathSince = 0L;
            return true;
        }
        int at = indexOfPosition(b.steps, current);
        if (at >= 0) {
            // We reached part of the predictive burst. Give the already-queued tail a brief
            // chance, then resend only what remains instead of waiting for a full timeout.
            if (now - b.lastProgressAtMs < config.routeBurstWaitMs) return false;
            List<Position> remaining = new ArrayList<>();
            for (int i = at + 1; i < b.steps.size(); i++) remaining.add(b.steps.get(i));
            if (remaining.isEmpty()) { pendingRouteBurst = null; return true; }
            sendRouteBurst(game, current, remaining);
            pendingRouteBurst = new PendingRouteBurst(current, List.copyOf(remaining), now, now, b.retries);
            lastMoveSentAt = now;
            return false;
        }
        if (!current.equals(b.from)) {
            // Native/external movement: accept the real position and replan from there.
            pendingRouteBurst = null;
            routePathCache = List.of(); routePathTargetIndex = -1; loopPathCache = List.of();
            return true;
        }
        Position first = b.steps.get(0);
        if (world.occupied.contains(first) && now - b.sentAtMs >= config.dynamicBlockGraceMs) {
            learnedBlockedUntil.put(first, now + config.dynamicBlockedMs);
            pendingRouteBurst = null;
            routePathCache = List.of(); routePathTargetIndex = -1; loopPathCache = List.of();
            updateStatus(now, "ROUTE BURST REPLAN - ocupado=" + fmt(first));
            return true;
        }
        if (now - b.sentAtMs < config.routeBurstTimeoutMs) return false;
        if (b.retries < config.routeBurstRetries) {
            sendRouteBurst(game, current, b.steps);
            pendingRouteBurst = new PendingRouteBurst(current, b.steps, now, now, b.retries + 1);
            lastMoveSentAt = now;
            return false;
        }
        // A dropped movement should not poison the tile for 15 seconds. Learn it only briefly;
        // repeated failures will make A* choose another path naturally.
        learnedBlockedUntil.put(first, now + Math.min(config.learnedBlockedMs, 1800));
        pendingRouteBurst = null;
        routePathCache = List.of(); routePathTargetIndex = -1; loopPathCache = List.of();
        updateStatus(now, "ROUTE BURST RECOVERY - " + fmt(first));
        return true;
    }

    private static void processLoopClosure(Object game, WorldState world, long now) throws Exception {
        Position current = world.player;
        Position start = route.get(0);
        if (cheb(current, start) <= config.routeWaypointTolerance) {
            loopClosing = false; loopPathCache = List.of(); routeIndex = 0; completedLoops++;
            event("LOOP " + completedLoops + " concluido; retorno ao inicio finalizado");
            return;
        }
        int idx = indexOfPosition(loopPathCache, current);
        if (idx < 0 || idx + 1 >= loopPathCache.size()
                || isBlockedForPath(world, loopPathCache.get(idx + 1), current, now)) {
            Set<Position> goals = goalArea(start, config.routeWaypointTolerance, world, current, null);
            List<Position> path = planPath(world, current, goals, null, now, config.pathDetourRadius);
            if (path.size() < 2) {
                loopPathCache = List.of();
                updateStatus(now, "LOOP CONNECT - aguardando caminho para inicio " + fmt(start));
                return;
            }
            loopPathCache = List.copyOf(path);
            idx = 0;
        }
        List<Position> steps = new ArrayList<>();
        Position from = current;
        for (int i = idx + 1; i < loopPathCache.size() && steps.size() < Math.max(1, config.routeBurstSize); i++) {
            Position q = loopPathCache.get(i);
            if (isBlockedForPath(world, q, from, now) || Direction.from(from, q) == null) break;
            steps.add(q); from = q;
        }
        if (steps.isEmpty()) { loopPathCache = List.of(); return; }
        sendRouteBurst(game, current, steps);
        pendingRouteBurst = new PendingRouteBurst(current, List.copyOf(steps), now, now, 0);
        lastMoveSentAt = now;
        updateStatus(now, "LOOP CONNECT " + fmt(current) + " -> " + fmt(steps.get(steps.size()-1))
                + " inicio=" + fmt(start) + " burst=" + steps.size());
    }

    private static Position cachedRouteStep(WorldState world, Position current, long now) {
        if (routePathCache == null || routePathCache.size() < 2) return null;
        if (routePathTargetIndex <= routeIndex || routePathTargetIndex >= route.size()) {
            routePathCache = List.of();
            routePathTargetIndex = -1;
            return null;
        }
        int idx = indexOfPosition(routePathCache, current);
        if (idx < 0 || idx + 1 >= routePathCache.size()) {
            routePathCache = List.of();
            routePathTargetIndex = -1;
            return null;
        }
        Position next = routePathCache.get(idx + 1);
        if (isBlockedForPath(world, next, current, now)) {
            routePathCache = List.of();
            routePathTargetIndex = -1;
            return null;
        }
        return next;
    }

    private static int indexOfPosition(List<Position> path, Position p) {
        for (int i = 0; i < path.size(); i++) if (path.get(i).equals(p)) return i;
        return -1;
    }

    private static boolean isBlockedForPath(WorldState world, Position p, Position start, long now) {
        if (p.equals(start)) return false;
        if (world.occupied.contains(p)) return true;
        Long until = learnedBlockedUntil.get(p);
        return until != null && until > now;
    }

    private static PathChoice chooseRoutePath(WorldState world, Position current, long now) {
        int last = route.size() - 1;
        int maxTarget = Math.min(last, routeIndex + Math.max(1, config.routeLookahead));
        for (int idx = maxTarget; idx > routeIndex; idx--) {
            Position waypoint = route.get(idx);
            Set<Position> goals = goalArea(waypoint, config.routeWaypointTolerance, world, current, null);
            List<Position> path = planPath(world, current, goals, null, now, config.pathDetourRadius);
            if (path.size() >= 2 || goals.contains(current)) {
                return new PathChoice(idx, path.isEmpty() ? List.of(current) : path);
            }
        }
        return null;
    }

    private static void beginCombat(Object game,
                                    WorldState world,
                                    Monster monster,
                                    long now,
                                    boolean newAnchor) throws Exception {
        if (newAnchor || combatAnchor == null) {
            combatAnchor = world.player;
            combatTrail.clear();
            combatTrail.add(world.player);
            lastCombatObservedPosition = world.player;
        }
        state = State.COMBAT;
        routePathCache = List.of();
        routePathTargetIndex = -1;
        lootPathCache = List.of();
        lastTargetPosition = monster.pos;
        selectTarget(game, monster, now, world.player);
        event("COMBAT - anchor=" + fmt(combatAnchor) + " target=" + monster.id + " " + monster.name
                + " range=" + cheb(world.player, monster.pos));
        updateStatus(now, "COMBAT target=" + monster.name + " id=" + monster.id
                + " dist=" + cheb(world.player, monster.pos) + " anchor=" + fmt(combatAnchor));
    }

    private static void processCombat(Object game, WorldState world, long now) throws Exception {
        Monster target = targetId == null ? null : world.monsters.get(targetId);
        if (target == null) {
            Integer deadId = targetId;
            String deadName = targetName;
            Position deadPos = lastTargetPosition;
            if (deadId != null && deadPos != null) queueLoot(deadId, deadName, deadPos, world.player, now);
            targetId = null;
            targetName = null;
            lastTargetPosition = null;
            Monster replacement = nearestEligibleMonster(world, world.player, combatAnchor, now);
            if (replacement != null) {
                selectTarget(game, replacement, now, world.player);
                target = replacement;
                event("NOVO ALVO - id=" + replacement.id + " name=" + replacement.name);
            } else {
                if (config.autoLootEnabled && !autoLootSessionDisabled && !lootQueue.isEmpty()) {
                    state = State.LOOTING;
                    pendingMove = null;
                    event("COMBATE LIMPO - iniciando AutoLoot fila=" + lootQueue.size());
                    updateStatus(now, "LOOTING fila=" + lootQueue.size());
                } else {
                    resumeRouteAdaptively(world, now, "COMBATE LIMPO");
                }
                return;
            }
        }

        if (config.antiKsEnabled && isContestedTarget(world, target, now)) {
            markTargetContested(game, target, now, "outro PLAYER esta mirando/ocupando o mob");
            updateStatus(now, "ANTI-KS - liberando " + target.name + " e procurando outro alvo");
            return;
        }

        Position previousTargetPosition = lastTargetPosition;
        boolean targetMoved = previousTargetPosition != null && !previousTargetPosition.equals(target.pos);
        lastTargetPosition = target.pos;
        int dist = cheb(world.player, target.pos);
        int anchorDist = combatAnchor == null ? 0 : cheb(world.player, combatAnchor);

        if (targetMoved) {
            lastCombatProgressAt = now;
            targetBlockedMoves = 0;
            blockedCombatSteps.clear();
        }

        if (dist < lastTargetDistance) {
            lastTargetDistance = dist;
            lastCombatProgressAt = now;
            targetBlockedMoves = 0;
        }

        if (dist <= config.combatDistance) {
            lastCombatProgressAt = now;
            targetBlockedMoves = 0;
            blockedCombatSteps.clear();
            updateStatus(now, "FIGHTING " + target.name + " id=" + target.id
                    + " dist=" + dist + " (segurando " + config.combatDistance + " tile)"
                    + " leash=" + anchorDist + "/" + config.combatLeash);
            return;
        }

        if (now - lastCombatProgressAt >= config.unreachableTimeoutMs) {
            markTargetUnreachable(game, now, "sem progresso por " + (now - lastCombatProgressAt) + "ms");
            updateStatus(now, "ALVO INACESSIVEL - " + target.name + " blacklist temporaria; retomando rota/novo alvo");
            return;
        }

        // Give the game's native aggressive-target behavior a chance to approach first.
        // If it does not, assist with one cardinal step at a time.
        if (!config.assistMovement) {
            updateStatus(now, "FIGHTING " + target.name + " dist=" + dist + " - aguardando movimento nativo");
            return;
        }
        if (now - targetSelectedAt < config.nativeChaseGraceMs) {
            updateStatus(now, "APPROACH " + target.name + " dist=" + dist + " - grace nativa");
            return;
        }
        if (now - lastMoveSentAt < config.assistMoveCooldownMs) {
            return;
        }

        Position step = chooseCombatPathStep(world, world.player, target, now);
        if (step == null) {
            updateStatus(now, "COMBAT A* SEM CAMINHO - " + target.name
                    + " dist=" + dist + " sem_progresso=" + (now - lastCombatProgressAt)
                    + "ms anchor=" + fmt(combatAnchor)
                    + " aprendidos=" + learnedBlockedUntil.size());
            return;
        }

        Direction d = Direction.from(world.player, step);
        if (d == null) return;
        sendMove(game, d, world.player);
        pendingMove = new PendingMove(MovePurpose.COMBAT, world.player, step, d, now, 0);
        lastMoveSentAt = now;
        updateStatus(now, "APPROACH " + target.name + " dist=" + dist + " "
                + fmt(world.player) + " -> " + fmt(step));
    }

    private static void processLooting(Object game, WorldState world, long now) throws Exception {
        cleanupTemporaryMaps(now);

        // Combat remains higher priority than looting. Corpses stay queued.
        Monster monster = config.combatEnabled ? nearestEligibleMonster(world, world.player, combatAnchor, now) : null;
        if (monster != null) {
            beginCombat(game, world, monster, now, false);
            return;
        }

        if (!config.autoLootEnabled || autoLootSessionDisabled || lootQueue.isEmpty()) {
            currentLoot = null;
            resumeRouteAdaptively(world, now, "AUTOLOOT FINALIZADO");
            return;
        }

        if (currentLoot == null) {
            currentLoot = chooseNextLootRequest(world, now);
            lootOpenedAt = 0L;
            lootSpaceSentAt = 0L;
            lootAttempts = 0;
            lootSpaceSent = false;
            lootPathCache = List.of();
        }
        if (currentLoot == null) {
            resumeRouteAdaptively(world, now, "AUTOLOOT FILA VAZIA");
            return;
        }

        AutoLootSupport.Candidate candidate = AutoLootSupport.nearest(
                currentLoot.corpse.x, currentLoot.corpse.y, world.lootables, config.lootSearchRadius);
        if (candidate == null) {
            if (now - currentLoot.diedAtMs > config.lootTaskTimeoutMs) {
                skippedLootBags++;
                event("AUTOLOOT SKIP - timeout sem bag corpo=" + fmt(currentLoot.corpse));
                finishCurrentLoot();
                return;
            }
            updateStatus(now, "LOOTING aguardando bag em " + fmt(currentLoot.corpse)
                    + " fila=" + lootQueue.size() + " elapsed=" + (now - currentLoot.diedAtMs) + "ms");
            return;
        }

        Position lootPos = new Position(candidate.x(), candidate.y());
        int dist = cheb(world.player, lootPos);
        if (dist > config.lootOpenRange) {
            Position next = cachedLootStep(world, world.player, lootPos, now);
            if (next == null) {
                Set<Position> goals = goalArea(lootPos, config.lootOpenRange, world, world.player, null);
                List<Position> path = planPath(world, world.player, goals, null, now, config.pathDetourRadius);
                if (path.size() < 2) {
                    updateStatus(now, "LOOTING sem caminho para bag " + fmt(lootPos));
                    return;
                }
                lootPathCache = List.copyOf(path);
                next = path.get(1);
            }
            Direction d = Direction.from(world.player, next);
            if (d != null) {
                sendMove(game, d, world.player);
                pendingMove = new PendingMove(MovePurpose.LOOT, world.player, next, d, now, 0);
                lastMoveSentAt = now;
                updateStatus(now, "LOOTING aproximando " + fmt(world.player) + " -> " + fmt(next)
                        + " bag=" + fmt(lootPos));
            }
            return;
        }

        if (lootOpenedAt == 0L) {
            try {
                AutoLootSupport.openLoot(game, candidate.token());
                lootOpenedAt = now;
                lootAttempts++;
                event("AUTOLOOT OPEN - bag=" + candidate.key() + " pos=" + fmt(lootPos));
                updateStatus(now, "LOOTING abriu bag em " + fmt(lootPos) + " tentativa=" + lootAttempts);
            } catch (Throwable t) {
                autoLootSessionDisabled = true;
                skippedLootBags += lootQueue.size();
                event("AUTOLOOT DESATIVADO NA SESSAO - CALLBACK_LOOT_INFO falhou: "
                        + t.getClass().getSimpleName() + ": " + safeMessage(t));
                lootQueue.clear();
                currentLoot = null;
                resumeRouteAdaptively(world, now, "AUTOLOOT DESATIVADO");
            }
            return;
        }

        if (!lootSpaceSent && now - lootOpenedAt >= config.lootOpenDelayMs) {
            AutoLootSupport.Candidate beforeSpace = AutoLootSupport.nearest(
                    currentLoot.corpse.x, currentLoot.corpse.y, world.lootables, config.lootSearchRadius);
            if (beforeSpace == null) {
                lootedBags++;
                event("AUTOLOOT OK - bag desapareceu antes do SPACE corpo=" + fmt(currentLoot.corpse));
                finishCurrentLoot();
                return;
            }
            boolean sent = AutoLootSupport.pressSpaceThroughGdx(game, now);
            if (!sent) {
                if (InputArbiter.isCoolingDown(now)) {
                    updateStatus(now, "LOOTING aguardando InputBus para SPACE");
                    return;
                }
                autoLootSessionDisabled = true;
                skippedLootBags += lootQueue.size();
                event("AUTOLOOT DESATIVADO NA SESSAO - InputProcessor/SPACE indisponivel");
                lootQueue.clear();
                currentLoot = null;
                resumeRouteAdaptively(world, now, "AUTOLOOT SPACE INDISPONIVEL");
                return;
            }
            lootSpaceSent = true;
            lootSpaceSentAt = now;
            event("AUTOLOOT SPACE - pegar selecionados em " + fmt(lootPos));
            updateStatus(now, "LOOTING pegando selecionados em " + fmt(lootPos));
            return;
        }

        if (lootSpaceSent && now - lootSpaceSentAt >= config.lootConfirmMs) {
            // v6.4: SPACE is sent at most once per bag. Retrying can race the closing
            // loot dialog and open chat instead.
            AutoLootSupport.Candidate stillThere = AutoLootSupport.nearest(
                    currentLoot.corpse.x, currentLoot.corpse.y, world.lootables, config.lootSearchRadius);
            lootedBags++;
            event((stillThere == null ? "AUTOLOOT OK" : "AUTOLOOT OK/PERSISTENTE")
                    + " - corpo=" + fmt(currentLoot.corpse)
                    + " fila_restante=" + Math.max(0, lootQueue.size() - 1));
            finishCurrentLoot();
            return;
        }
    }

    private static Position cachedLootStep(WorldState world, Position current, Position lootPos, long now) {
        if (lootPathCache == null || lootPathCache.size() < 2) return null;
        int idx = indexOfPosition(lootPathCache, current);
        if (idx < 0 || idx + 1 >= lootPathCache.size()) {
            lootPathCache = List.of();
            return null;
        }
        Position next = lootPathCache.get(idx + 1);
        if (isBlockedForPath(world, next, current, now) || cheb(next, lootPos) > cheb(current, lootPos) + 2) {
            lootPathCache = List.of();
            return null;
        }
        return next;
    }

    private static void queueLoot(int monsterId, String monsterName, Position corpse, Position player, long now) {
        if (!config.autoLootEnabled || autoLootSessionDisabled || corpse == null) return;
        if (player != null && cheb(player, corpse) > config.lootCorpseMaxDistance) return;
        for (LootRequest q : lootQueue) {
            if (q.corpse.equals(corpse) && now - q.diedAtMs < 5000L) return;
        }
        lootQueue.addLast(new LootRequest(monsterId, monsterName == null ? "<monster>" : monsterName, corpse, now));
        AnalyzerEngine.corpseObserved(monsterId,now);
        event("AUTOLOOT QUEUE - " + monsterName + " corpo=" + fmt(corpse) + " fila=" + lootQueue.size());
    }

    private static LootRequest chooseNextLootRequest(WorldState world, long now) {
        if (lootQueue.isEmpty()) return null;
        LootRequest bestVisible = null;
        int bestDist = Integer.MAX_VALUE;
        for (LootRequest q : lootQueue) {
            AutoLootSupport.Candidate c = AutoLootSupport.nearest(
                    q.corpse.x, q.corpse.y, world.lootables, config.lootSearchRadius);
            if (c == null) continue;
            int d = world.player == null ? 0 : cheb(world.player, new Position(c.x(), c.y()));
            if (bestVisible == null || d < bestDist) {
                bestVisible = q;
                bestDist = d;
            }
        }
        if (bestVisible != null) return bestVisible;
        return lootQueue.peekFirst();
    }

    private static void finishCurrentLoot() {
        if (currentLoot != null) lootQueue.remove(currentLoot);
        else if (!lootQueue.isEmpty()) lootQueue.removeFirst();
        currentLoot = null;
        lootOpenedAt = 0L;
        lootSpaceSentAt = 0L;
        lootAttempts = 0;
        lootSpaceSent = false;
        lootPathCache = List.of();
    }

    private static void resumeRouteAdaptively(WorldState world, long now, String reason) {
        pendingMove = null;
        state = State.ROUTE;
        targetId = null;
        targetName = null;
        lastTargetPosition = null;
        combatTrail.clear();
        lastCombatObservedPosition = null;
        blockedCombatSteps.clear();
        targetBlockedMoves = 0;
        lastTargetDistance = Integer.MAX_VALUE;
        routePathCache = List.of();
        routePathTargetIndex = -1;
        routeNoPathSince = 0L;
        int advanced = bestForwardRouteIndex(world.player, routeIndex, Math.max(config.routeLookahead * 2, 8), config.routeJoinRadius);
        if (advanced > routeIndex) routeIndex = advanced;
        event(reason + " - retomada adaptativa indice=" + routeIndex + " player=" + fmt(world.player));
        updateStatus(now, "ROUTE RETOMADA ADAPTATIVA " + (routeIndex + 1) + "/" + route.size());
    }

    private static void processReturning(Object game, WorldState world, long now) throws Exception {
        cleanupTemporaryMaps(now);
        Monster monster = config.combatEnabled ? nearestEligibleMonster(world, world.player, combatAnchor, now) : null;
        if (monster != null) {
            beginCombat(game, world, monster, now, false);
            return;
        }

        if (combatAnchor == null || cheb(world.player, combatAnchor) <= config.returnTolerance) {
            resumeRouteAdaptively(world, now, "ROTA RETOMADA");
            return;
        }

        Set<Position> goals = goalArea(combatAnchor, config.returnTolerance, world, world.player, null);
        List<Position> path = planPath(world, world.player, goals, null, now, config.pathDetourRadius);
        if (path.size() < 2) {
            updateStatus(now, "RETURNING A* AGUARDANDO - atual=" + fmt(world.player)
                    + " anchor=" + fmt(combatAnchor)
                    + " dinamicos=" + world.occupied.size()
                    + " aprendidos=" + learnedBlockedUntil.size());
            return;
        }

        Position current = world.player;
        Position next = path.get(1);
        Direction d = Direction.from(current, next);
        if (d == null) return;
        sendMove(game, d, current);
        pendingMove = new PendingMove(MovePurpose.RETURN, current, next, d, now, 0);
        lastMoveSentAt = now;
        updateStatus(now, "RETURNING A* " + fmt(current) + " -> " + fmt(next)
                + " anchor=" + fmt(combatAnchor) + " path=" + path.size());
    }

    /** @return true when caller may continue the state machine in the same tick. */
    private static boolean processPendingMove(Object game, WorldState world, long now) throws Exception {
        Position current = world.player;
        PendingMove p = pendingMove;
        if (p == null) return true;

        if (current.equals(p.to)) {
            pendingMove = null;
            learnedBlockedUntil.remove(current);
            switch (p.purpose) {
                case ROUTE -> routeNoPathSince = 0L;
                case COMBAT -> {
                    appendCombatTrailPosition(current);
                    lastCombatProgressAt = now;
                    targetBlockedMoves = 0;
                }
                case RETURN -> normalizeTrailToCurrent(current);
                case LOOT -> { }
            }
            return true;
        }

        if (!current.equals(p.from)) {
            // Any external/native displacement is accepted. The adaptive planner simply replans
            // from the real position instead of stopping the cavebot.
            if (p.purpose == MovePurpose.COMBAT) {
                appendCombatTrailPosition(current);
                // Native aggressive chase frequently moves the player before our own pending
                // step is confirmed. That is progress, not evidence that the target is unreachable.
                lastCombatProgressAt = now;
                targetBlockedMoves = 0;
                blockedCombatSteps.clear();
            }
            if (p.purpose == MovePurpose.ROUTE) { routePathCache = List.of(); routePathTargetIndex = -1; }
            if (p.purpose == MovePurpose.LOOT) lootPathCache = List.of();
            pendingMove = null;
            updateStatus(now, "REPLAN LEVE - purpose=" + p.purpose
                    + " esperado=" + fmt(p.to) + " atual=" + fmt(current));
            return true;
        }

        if (world.occupied.contains(p.to) && now - p.sentAtMs >= config.dynamicBlockGraceMs) {
            pendingMove = null;
            learnedBlockedUntil.put(p.to, now + Math.min(config.learnedBlockedMs, config.dynamicBlockedMs));
            if (p.purpose == MovePurpose.ROUTE) { routePathCache = List.of(); routePathTargetIndex = -1; }
            if (p.purpose == MovePurpose.LOOT) lootPathCache = List.of();
            updateStatus(now, p.purpose + " REPLAN DINAMICO - ocupado=" + fmt(p.to));
            return true;
        }

        if (now - p.sentAtMs < config.moveTimeoutMs) return false;

        // Allow late server/world confirmation before learning a false wall.
        if (shouldWaitLateArrival(world.occupied.contains(p.to), now - p.sentAtMs,
                config.moveTimeoutMs, config.lateArrivalGraceMs)) {
            return false;
        }

        if (p.retries < config.maxMoveRetries) {
            // Do not retry if a dynamic entity has visibly occupied the tile meanwhile.
            if (!world.occupied.contains(p.to)) {
                sendMove(game, p.direction, current);
                pendingMove = new PendingMove(p.purpose, current, p.to, p.direction, now, p.retries + 1);
                lastMoveSentAt = now;
                updateStatus(now, "RETRY " + p.purpose + " " + p.direction + " -> " + fmt(p.to));
                return false;
            }
        }

        pendingMove = null;
        learnedBlockedUntil.put(p.to, now + config.learnedBlockedMs);

        if (p.purpose == MovePurpose.COMBAT) {
            blockedCombatSteps.add(p.to);
            targetBlockedMoves++;
            boolean hostile = targetId != null && world.hostileMonsterIds.contains(targetId);
            if (targetId != null && shouldBlacklistFromBlockedSteps(hostile, targetBlockedMoves, config.unreachableFailedSteps)) {
                markTargetUnreachable(game, now, "passos bloqueados=" + targetBlockedMoves);
                updateStatus(now, "ALVO INACESSIVEL - passos bloqueados; procurando outro/retomando rota");
            } else {
                updateStatus(now, "COMBAT REPLAN - tile bloqueado " + fmt(p.to)
                        + " (" + targetBlockedMoves + "/" + config.unreachableFailedSteps + ")"
                        + (hostile ? " agressor=SIM" : ""));
            }
            return true;
        }

        if (p.purpose == MovePurpose.ROUTE) {
            routePathCache = List.of();
            routePathTargetIndex = -1;
            routeNoPathSince = routeNoPathSince == 0L ? now : routeNoPathSince;
            updateStatus(now, "ROUTE HARD-REPLAN - bloqueado=" + fmt(p.to));
            return true;
        }
        if (p.purpose == MovePurpose.LOOT) {
            lootPathCache = List.of();
            updateStatus(now, "LOOT REPLAN - bloqueado=" + fmt(p.to));
            return true;
        }

        event("RETURN TILE BLOQUEADO - " + fmt(p.to) + "; A* vai replanejar");
        updateStatus(now, "RETURN REPLAN - bloqueado=" + fmt(p.to));
        return true;
    }

    static boolean shouldWaitLateArrival(boolean visiblyOccupied, long elapsedMs, int timeoutMs, int graceMs) {
        return !visiblyOccupied && elapsedMs >= timeoutMs && elapsedMs < (long)timeoutMs + Math.max(0, graceMs);
    }

    static int plannedBurstCountForTests(int availableSteps, int configuredBurst) {
        return Math.min(Math.max(0, availableSteps), Math.max(1, Math.min(3, configuredBurst)));
    }

    static boolean loopNeedsConnectionForTests(boolean loopEnabled, int distanceToStart, int tolerance) {
        return loopEnabled && distanceToStart > Math.max(0, tolerance);
    }

    static boolean burstRetryDueForTests(long elapsedMs, int timeoutMs, int retries, int maxRetries) {
        return elapsedMs >= timeoutMs && retries < maxRetries;
    }

    private static Monster nearestEligibleMonster(WorldState world, Position player, Position anchor, long now) {
        if (world == null || player == null) return null;
        return world.monsters.values().stream()
                .filter(m -> !isTargetBlacklisted(m, world, now))
                .filter(m -> !isTargetContested(m.id, now))
                .filter(m -> !config.antiKsEnabled || !isContestedTarget(world, m, now))
                .filter(m -> {
                    int d = cheb(player, m.pos);
                    return d <= Math.max(config.detectionRange, config.rangedDetectionRange)
                            || (world.hostileMonsterIds.contains(m.id) && d <= config.aggroDetectionRange);
                })
                .filter(m -> anchor == null || cheb(anchor, m.pos) <= config.combatLeash + Math.max(config.rangedDetectionRange, config.aggroDetectionRange))
                .min(monsterComparator(world, player))
                .orElse(null);
    }

    private static Comparator<Monster> monsterComparator(WorldState world, Position player) {
        // v8.4: a ranged aggressor outside the normal scan is an urgent self-defense case.
        // Inside the normal scan, the user's selected criterion is primary and hostility is a tie-breaker.
        Comparator<Monster> urgent = Comparator.comparingInt((Monster m) ->
                world.hostileMonsterIds.contains(m.id) && cheb(player,m.pos) > config.detectionRange ? 0 : 1);
        Comparator<Monster> hostileTie = Comparator.comparingInt((Monster m) -> world.hostileMonsterIds.contains(m.id) ? 0 : 1);
        Comparator<Monster> criterion;
        if (config.attackPriority == HelperConfig.AttackPriority.FARTHEST) {
            criterion = Comparator.comparingInt((Monster m) -> cheb(player, m.pos)).reversed();
        } else if (config.attackPriority == HelperConfig.AttackPriority.LOWEST_HP_ABSOLUTE) {
            criterion = Comparator.comparingDouble((Monster m) -> {
                Double hp = VitalSensorSupport.monsterHpCurrent(m.id);
                return hp == null || hp <= 0 ? Double.POSITIVE_INFINITY : hp;
            }).thenComparingInt(m -> cheb(player,m.pos));
        } else if (config.attackPriority == HelperConfig.AttackPriority.LOWEST_HP_PERCENT) {
            criterion = Comparator.comparingDouble((Monster m) -> {
                Double hp = VitalSensorSupport.monsterHpPercent(m.id);
                return hp == null || hp <= 0 ? 101.0 : hp;
            }).thenComparingInt(m -> cheb(player,m.pos));
        } else {
            criterion = Comparator.comparingInt((Monster m) -> cheb(player,m.pos)).thenComparingInt(m -> manhattan(player,m.pos));
        }
        return urgent.thenComparing(criterion).thenComparing(hostileTie).thenComparingInt(m -> m.id);
    }    private static boolean isTargetContested(int id, long now) {
        Long until = contestedUntil.get(id);
        if (until == null) return false;
        if (until <= now) {
            contestedUntil.remove(id);
            return false;
        }
        return true;
    }

    private static boolean isContestedTarget(WorldState world, Monster monster, long now) {
        if (!config.antiKsEnabled || world == null || monster == null) return false;
        if (world.claimedMonsterIds.contains(monster.id)) return true;
        if (!world.remoteTargetTelemetry) {
            for (Occupant o : world.occupants.values()) {
                if (!"PLAYER".equals(o.type)) continue;
                if (cheb(o.pos, monster.pos) <= config.antiKsPlayerRadius) return true;
            }
        }
        return false;
    }

    private static void markTargetContested(Object game, Monster monster, long now, String reason) {
        if (monster == null) return;
        contestedUntil.put(monster.id, now + config.antiKsBlacklistMs);
        try { sendTargetNone(game, monster.id); } catch (Throwable ignored) { }
        event("ANTI-KS - id=" + monster.id + " name=" + monster.name + " motivo=" + reason
                + " blacklist_ms=" + config.antiKsBlacklistMs);
        targetId = null;
        targetName = null;
        targetSelectedAt = 0L;
        lastTargetPosition = null;
        lastTargetDistance = Integer.MAX_VALUE;
        targetBlockedMoves = 0;
        blockedCombatSteps.clear();
        pendingMove = null;
        Monster replacement = cachedWorld == null ? null
                : nearestEligibleMonster(cachedWorld, cachedWorld.player, combatAnchor, now);
        if (replacement == null && cachedWorld != null) {
            resumeRouteAdaptively(cachedWorld, now, "ANTI-KS");
        }
    }

    private static Position chooseCombatPathStep(WorldState world, Position player, Monster target, long now) {
        Set<Position> goals = new HashSet<>();
        int r = Math.max(1, config.combatDistance);
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                Position p = new Position(target.pos.x + dx, target.pos.y + dy);
                if (cheb(p, target.pos) != r) continue;
                if (p.equals(target.pos)) continue;
                if (combatAnchor != null && cheb(p, combatAnchor) > config.combatLeash) continue;
                if (!p.equals(player) && world.occupied.contains(p)) continue;
                goals.add(p);
            }
        }
        if (goals.isEmpty()) return null;
        List<Position> path = planPath(world, player, goals, target.id, now, config.pathDetourRadius,
                p -> combatAnchor == null || cheb(p, combatAnchor) <= config.combatLeash);
        return path.size() >= 2 ? path.get(1) : null;
    }

    private static Set<Position> goalArea(Position center,
                                          int tolerance,
                                          WorldState world,
                                          Position current,
                                          Integer allowOccupiedEntityId) {
        Set<Position> goals = new HashSet<>();
        int r = Math.max(0, tolerance);
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                Position p = new Position(center.x + dx, center.y + dy);
                if (cheb(p, center) > r) continue;
                if (p.equals(current) || !world.occupied.contains(p)) goals.add(p);
            }
        }
        if (goals.isEmpty() && current.equals(center)) goals.add(current);
        return goals;
    }

    private static List<Position> planPath(WorldState world,
                                           Position start,
                                           Set<Position> goals,
                                           Integer targetMonsterId,
                                           long now,
                                           int margin) {
        return planPath(world, start, goals, targetMonsterId, now, margin, p -> true);
    }

    private static List<Position> planPath(WorldState world,
                                           Position start,
                                           Set<Position> goals,
                                           Integer targetMonsterId,
                                           long now,
                                           int margin,
                                           java.util.function.Predicate<Position> allowed) {
        cleanupTemporaryMaps(now);
        Set<Position> blocked = new HashSet<>(world.occupied);
        blocked.remove(start);
        if (targetMonsterId != null) {
            Monster target = world.monsters.get(targetMonsterId);
            if (target != null) blocked.add(target.pos); // never step onto the target's tile
        }
        for (Map.Entry<Position, Long> e : learnedBlockedUntil.entrySet()) {
            if (e.getValue() > now && !e.getKey().equals(start)) blocked.add(e.getKey());
        }
        for (Position p : blockedCombatSteps) {
            if (!p.equals(start)) blocked.add(p);
        }

        Set<Pathfinder.Pos> pg = new HashSet<>();
        for (Position g : goals) pg.add(new Pathfinder.Pos(g.x, g.y));
        Set<Pathfinder.Pos> pb = new HashSet<>();
        for (Position b : blocked) pb.add(new Pathfinder.Pos(b.x, b.y));

        List<Pathfinder.Pos> raw = Pathfinder.find(
                new Pathfinder.Pos(start.x, start.y), pg, pb,
                Math.max(2, margin), config.pathMaxNodes,
                q -> allowed.test(new Position(q.x(), q.y())));
        if (raw.isEmpty()) return List.of();
        List<Position> result = new ArrayList<>(raw.size());
        for (Pathfinder.Pos q : raw) result.add(new Position(q.x(), q.y()));
        return result;
    }

    private static int bestForwardRouteIndex(Position current, int fromIndex, int lookahead, int tolerance) {
        int last = route.size() - 1;
        int max = Math.min(last, fromIndex + Math.max(1, lookahead));
        int best = fromIndex;
        for (int i = fromIndex; i <= max; i++) {
            if (cheb(current, route.get(i)) <= Math.max(0, tolerance)) best = i;
        }
        return best;
    }

    private static void cleanupTemporaryMaps(long now) {
        unreachableUntil.entrySet().removeIf(e -> e.getValue() <= now);
        unreachableAtPosition.keySet().removeIf(id -> !unreachableUntil.containsKey(id));
        contestedUntil.entrySet().removeIf(e -> e.getValue() <= now);
        learnedBlockedUntil.entrySet().removeIf(e -> e.getValue() <= now);
    }

    private static void selectTarget(Object game, Monster monster, long now, Position player) throws Exception {
        sendAggressiveTarget(game, monster.id);
        targetId = monster.id;
        targetName = monster.name;
        lastTargetPosition = monster.pos;
        targetSelectedAt = now;
        lastCombatProgressAt = now;
        lastTargetDistance = player == null ? Integer.MAX_VALUE : cheb(player, monster.pos);
        targetBlockedMoves = 0;
        blockedCombatSteps.clear();
    }

    private static boolean isTargetBlacklisted(Monster monster, WorldState world, long now) {
        if (monster == null) return false;
        int id = monster.id;
        Long until = unreachableUntil.get(id);
        if (until == null) return false;
        if (now >= until) {
            unreachableUntil.remove(id);
            unreachableAtPosition.remove(id);
            return false;
        }

        // A moving monster is a materially different reachability problem. Do not keep an old
        // "behind a wall" decision after the target has changed tiles.
        Position markedAt = unreachableAtPosition.get(id);
        if (markedAt != null && !markedAt.equals(monster.pos)) {
            unreachableUntil.remove(id);
            unreachableAtPosition.remove(id);
            return false;
        }
        return true;
    }

    static boolean shouldBlacklistFromBlockedSteps(boolean hostile, int blockedMoves, int threshold) {
        // Mobs that are actively targeting us are allowed to keep the native chase alive.
        // They can still be abandoned by the no-progress timeout, but not merely because
        // two assisted cardinal steps disagreed with the native chase/path.
        return !hostile && blockedMoves >= threshold;
    }

    static long unreachableBlacklistDurationForTests(boolean hostile, int normalMs, int hostileMs) {
        return hostile ? hostileMs : normalMs;
    }

    private static void markTargetUnreachable(Object game, long now, String reason) {
        Integer oldId = targetId;
        String oldName = targetName;
        if (oldId == null) return;
        boolean hostile = cachedWorld != null && cachedWorld.hostileMonsterIds.contains(oldId);
        long duration = unreachableBlacklistDurationForTests(
                hostile, config.unreachableBlacklistMs, config.hostileUnreachableBlacklistMs);
        long until = now + duration;
        unreachableUntil.put(oldId, until);
        if (lastTargetPosition != null) unreachableAtPosition.put(oldId, lastTargetPosition);
        try {
            sendTargetNone(game, oldId);
        } catch (Throwable t) {
            event("CLEAR TARGET falhou para " + oldId + ": " + t.getClass().getSimpleName());
        }
        if (pendingMove != null && pendingMove.purpose == MovePurpose.COMBAT) pendingMove = null;
        targetId = null;
        targetName = null;
        targetSelectedAt = 0L;
        lastCombatProgressAt = now;
        lastTargetDistance = Integer.MAX_VALUE;
        targetBlockedMoves = 0;
        blockedCombatSteps.clear();
        event("ALVO INACESSIVEL - id=" + oldId + " name=" + oldName + " motivo=" + reason
                + " blacklist_ms=" + duration + (hostile ? " agressor=SIM" : ""));
    }

    private static void trackCombatDisplacement(Position current) {
        if (current == null) return;
        if (lastCombatObservedPosition == null) {
            lastCombatObservedPosition = current;
            appendCombatTrailPosition(current);
            return;
        }
        if (current.equals(lastCombatObservedPosition)) return;

        // Any real displacement while in combat means either our assist or the game's native
        // aggressive chase is making progress. Reset false "blocked target" evidence.
        if (state == State.COMBAT) {
            lastCombatProgressAt = System.currentTimeMillis();
            targetBlockedMoves = 0;
            blockedCombatSteps.clear();
        }

        Position from = lastCombatObservedPosition;
        int dx = current.x - from.x;
        int dy = current.y - from.y;
        if ((Math.abs(dx) + Math.abs(dy)) == 1) {
            appendCombatTrailPosition(current);
        } else if (dx == 0 || dy == 0) {
            int sx = Integer.compare(dx, 0);
            int sy = Integer.compare(dy, 0);
            Position p = from;
            int guard = 0;
            while (!p.equals(current) && guard++ < 16) {
                p = new Position(p.x + sx, p.y + sy);
                appendCombatTrailPosition(p);
            }
        } else {
            // We deliberately do not invent a diagonal path. Preserve the actual observed point;
            // return logic will stop rather than guess if it cannot retrace it safely.
            appendCombatTrailPosition(current);
        }
        lastCombatObservedPosition = current;
    }

    private static void appendCombatTrailPosition(Position p) {
        if (p == null || combatAnchor == null) return;
        if (combatTrail.isEmpty()) {
            combatTrail.add(combatAnchor);
        }
        Position last = combatTrail.get(combatTrail.size() - 1);
        if (last.equals(p)) return;

        // If native chase walked back over the path, collapse the tail immediately.
        if (combatTrail.size() >= 2 && combatTrail.get(combatTrail.size() - 2).equals(p)) {
            combatTrail.remove(combatTrail.size() - 1);
            return;
        }
        combatTrail.add(p);
        if (combatTrail.size() > 128) {
            event("TRAIL >128 tiles; protegendo contra chase excessivo");
        }
    }

    private static void normalizeTrailToCurrent(Position current) {
        if (current == null || combatTrail.isEmpty()) return;
        int found = -1;
        for (int i = combatTrail.size() - 1; i >= 0; i--) {
            if (combatTrail.get(i).equals(current)) {
                found = i;
                break;
            }
        }
        if (found >= 0) {
            while (combatTrail.size() > found + 1) combatTrail.remove(combatTrail.size() - 1);
        }
    }

    private static Position readPlayerPositionFast(Object playerEntity, ClassLoader loader) {
        if (playerEntity == null) return null;
        try {
            FastPositionAccess a = fastPositionAccess;
            if (a == null || a.entityClass != playerEntity.getClass()) {
                Class<?> pc = Class.forName(POSITION_CLASS, false, loader);
                Method gc = playerEntity.getClass().getMethod("getComponent", Class.class);
                Field fx = pc.getField(POS_X_FIELD), fy = pc.getField(POS_Y_FIELD);
                a = new FastPositionAccess(playerEntity.getClass(), pc, gc, fx, fy);
                fastPositionAccess = a;
            }
            Object p = a.getComponent.invoke(playerEntity, a.positionClass);
            if (p == null) return null;
            return new Position(a.xField.getInt(p), a.yField.getInt(p));
        } catch (Throwable t) {
            fastPositionAccess = null;
            return null;
        }
    }

    private static WorldState withPlayer(WorldState w, Position p) {
        if (w == null || p == null) return w;
        return new WorldState(p, w.playerEntity, w.monsters, w.occupants, w.occupied, w.lootables,
                w.claimedMonsterIds, w.hostileMonsterIds, w.localTargetId, w.remoteTargetTelemetry);
    }

    private static WorldState scanWorld(Object game) throws Exception {
        ClassLoader loader = game.getClass().getClassLoader();
        Class<?> identityClass = Class.forName(IDENTITY_CLASS, false, loader);
        Class<?> positionClass = Class.forName(POSITION_CLASS, false, loader);
        Class<?> lootTokenClass = Class.forName(LOOT_TOKEN_COMPONENT_CLASS, false, loader);
        Class<?> targetComponentClass = null;
        Method targetEntityGetter = null;
        try {
            targetComponentClass = Class.forName(TARGET_COMPONENT_CLASS, false, loader);
            targetEntityGetter = targetComponentClass.getMethod(TARGET_COMPONENT_GETTER);
        } catch (Throwable ignored) { }
        Field typeField = identityClass.getField(TYPE_FIELD);
        Field idField = identityClass.getField(ID_FIELD);
        Field nameField = identityClass.getField(NAME_FIELD);
        Field xField = positionClass.getField(POS_X_FIELD);
        Field yField = positionClass.getField(POS_Y_FIELD);

        Method worldGetter = game.getClass().getMethod(WORLD_GETTER);
        Object world = worldGetter.invoke(game);
        if (world == null) return null;

        Method playerGetter = world.getClass().getMethod(PLAYER_ENTITY_GETTER);
        Object playerEntity = playerGetter.invoke(world);
        Position player = readPosition(playerEntity, positionClass, xField, yField);

        Method getEntities = world.getClass().getMethod("getEntities");
        Object entitiesObj = getEntities.invoke(world);
        if (!(entitiesObj instanceof Iterable<?> entities)) {
            return new WorldState(player, playerEntity, Map.of(), Map.of(), Set.of(), List.of(), Set.of(), Set.of(), null, false);
        }

        Map<Integer, Monster> monsters = new HashMap<>();
        long partyScanNow = System.currentTimeMillis();
        HelperConfig.Config helperPartyConfig = HelperConfig.get(partyScanNow);
        PartySupport.beginScan();
        Map<Integer, Occupant> occupants = new HashMap<>();
        Set<Position> occupied = new HashSet<>();
        List<AutoLootSupport.Candidate> lootables = new ArrayList<>();
        Set<Integer> claimedMonsterIds = new HashSet<>();
        Set<Integer> hostileMonsterIds = new HashSet<>();
        Integer localTargetId = null;
        boolean remoteTargetTelemetry = false;

        if (targetComponentClass != null && targetEntityGetter != null && playerEntity != null) {
            try {
                Method pc = playerEntity.getClass().getMethod("getComponent", Class.class);
                Object tc = pc.invoke(playerEntity, targetComponentClass);
                Object te = tc == null ? null : targetEntityGetter.invoke(tc);
                localTargetId = entityIdentityId(te, identityClass, idField);
            } catch (Throwable ignored) { }
        }

        for (Object entity : entities) {
            if (entity == playerEntity) continue;
            Method getComponent = entity.getClass().getMethod("getComponent", Class.class);
            Object posObj = getComponent.invoke(entity, positionClass);
            if (posObj == null) continue;
            Position pos = new Position(xField.getInt(posObj), yField.getInt(posObj));

            Object identity = getComponent.invoke(entity, identityClass);
            int id = System.identityHashCode(entity);
            String type = "<sem identity>";
            String name = "<sem nome>";
            if (identity != null) {
                Object typeObj = typeField.get(identity);
                type = typeObj instanceof Enum<?> e ? e.name() : String.valueOf(typeObj);
                id = idField.getInt(identity);
                Object n = nameField.get(identity);
                name = n == null ? "<sem nome>" : n.toString();

                // Every positioned identity entity other than the local player is a dynamic obstacle for route A*.
                occupied.add(pos);
                occupants.put(id, new Occupant(id, name, type, pos));
                if ("NPC_MONSTER".equals(type)) {
                    monsters.put(id, new Monster(id, name, pos));
                    VitalSensorSupport.observeMonster(entity, id, java.util.Objects.equals(localTargetId, id), System.currentTimeMillis());
                    if (targetComponentClass != null && targetEntityGetter != null && playerEntity != null) {
                        try {
                            Object tc = getComponent.invoke(entity, targetComponentClass);
                            Object te = tc == null ? null : targetEntityGetter.invoke(tc);
                            if (te == playerEntity) hostileMonsterIds.add(id);
                        } catch (Throwable ignored) { }
                    }
                }
                if ("PLAYER".equals(type) && targetComponentClass != null && targetEntityGetter != null) {
                    try {
                        Object tc = getComponent.invoke(entity, targetComponentClass);
                        if (tc != null) {
                            remoteTargetTelemetry = true;
                            Object te = targetEntityGetter.invoke(tc);
                            Integer claimed = entityIdentityId(te, identityClass, idField);
                            if (claimed != null) {
                                boolean party = PartySupport.isPartyMember(entity, id, name, helperPartyConfig, partyScanNow);
                                if (!party) claimedMonsterIds.add(claimed);
                            }
                        }
                    } catch (Throwable ignored) { }
                }
            }

            Object lootToken = getComponent.invoke(entity, lootTokenClass);
            if (lootToken != null) {
                lootables.add(new AutoLootSupport.Candidate(id, pos.x, pos.y, name, type, lootToken));
            }
        }
        return new WorldState(player, playerEntity, monsters, occupants, occupied, List.copyOf(lootables), Set.copyOf(claimedMonsterIds), Set.copyOf(hostileMonsterIds), localTargetId, remoteTargetTelemetry);
    }

    private static Integer entityIdentityId(Object entity, Class<?> identityClass, Field idField) {
        if (entity == null) return null;
        try {
            Method getComponent = entity.getClass().getMethod("getComponent", Class.class);
            Object identity = getComponent.invoke(entity, identityClass);
            return identity == null ? null : idField.getInt(identity);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Position readPosition(Object entity, Class<?> positionClass, Field xField, Field yField) throws Exception {
        if (entity == null) return null;
        Method getComponent = entity.getClass().getMethod("getComponent", Class.class);
        Object position = getComponent.invoke(entity, positionClass);
        if (position == null) return null;
        return new Position(xField.getInt(position), yField.getInt(position));
    }

    /** Lightweight local ECS obstacle scan for optional Chase (no HP or loot probes).
     * Learnt blocked tiles from navigation are shared between CaveBot and Chase.
     * Only reads client objects; it does not mutate world entities.
     */
    static Set<Pathfinder.Pos> chaseObstacles(Object game,int px,int py,int radius,long now) throws Exception {
        if(game==null) throw new IllegalArgumentException("game ausente");
        ClassLoader loader=game.getClass().getClassLoader();
        Class<?> pc=Class.forName(POSITION_CLASS,false,loader);
        Field fx=pc.getField(POS_X_FIELD),fy=pc.getField(POS_Y_FIELD);
        Object world=game.getClass().getMethod(WORLD_GETTER).invoke(game);
        if(world==null) throw new IllegalStateException("world indisponivel");
        Object self=world.getClass().getMethod(PLAYER_ENTITY_GETTER).invoke(world);
        Object entities=world.getClass().getMethod("getEntities").invoke(world);
        if(!(entities instanceof Iterable<?> iterator)) throw new IllegalStateException("entities indisponiveis");
        Set<Pathfinder.Pos> result=new HashSet<>();
        int reach=Math.max(3,Math.min(20,radius));
        for(Object entity:iterator){
            if(entity==self || entity==null)continue;
            Method get=entity.getClass().getMethod("getComponent",Class.class);
            Object position=get.invoke(entity,pc);
            if(position==null)continue;
            int x=fx.getInt(position), y=fy.getInt(position);
            if(Math.abs(x-px)<=reach && Math.abs(y-py)<=reach)
                result.add(new Pathfinder.Pos(x,y));
        }
        for(Map.Entry<Position,Long> e:learnedBlockedUntil.entrySet()){
            if(e.getValue()>now && Math.abs(e.getKey().x-px)<=reach && Math.abs(e.getKey().y-py)<=reach)
                result.add(new Pathfinder.Pos(e.getKey().x,e.getKey().y));
        }
        return result;
    }

    /** Single cardinal step for optional target chase, sharing client action format.
     * No path hacking, does not run when CaveBot owns navigation. Caller verifies progress.
     */
    static void chaseSingleStep(Object game,int fromX,int fromY,int dx,int dy) throws Exception {
        if (isActive() || Math.abs(dx)+Math.abs(dy)!=1) return;
        Position from = new Position(fromX,fromY);
        Direction dir=Direction.from(from,new Position(fromX+dx,fromY+dy));
        if(dir!=null) sendMove(game,dir,from);
    }

    private static void sendMove(Object game, Direction logicalDirection, Position from) throws Exception {
        ClassLoader loader = game.getClass().getClassLoader();
        Class<?> actionBaseClass = Class.forName(ACTION_BASE_CLASS, false, loader);
        Class<?> moveClass = Class.forName(MOVE_ACTION_CLASS, false, loader);
        Class<?> directionClass = Class.forName(MOVE_DIRECTION_ENUM, false, loader);

        Method actionFactory = game.getClass().getMethod(ACTION_FACTORY_METHOD, Class.class);
        Object action = actionFactory.invoke(game, moveClass);
        if (action == null) throw new IllegalStateException("REQUEST_PLAYER_MOVE factory retornou null");

        Field fx = moveClass.getDeclaredField(MOVE_FROM_X_FIELD);
        Field fy = moveClass.getDeclaredField(MOVE_FROM_Y_FIELD);
        Field fd = moveClass.getDeclaredField(MOVE_DIRECTION_FIELD);
        fx.setAccessible(true);
        fy.setAccessible(true);
        fd.setAccessible(true);
        fx.setInt(action, from.x);
        fy.setInt(action, from.y);

        String runtimeName = switch (logicalDirection) {
            // Confirmed empirically by move_learning.txt from this client build.
            case NORTH -> "SOUTH";
            case SOUTH -> "NORTH";
            case EAST -> "EAST";
            case WEST -> "WEST";
        };
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object runtimeDirection = Enum.valueOf((Class<? extends Enum>) directionClass.asSubclass(Enum.class), runtimeName);
        fd.set(action, runtimeDirection);

        Method sendAction = game.getClass().getMethod(SEND_ACTION_METHOD, actionBaseClass);
        sendAction.invoke(game, action);
    }

    private static void sendAggressiveTarget(Object game, int id) throws Exception {
        ClassLoader loader = game.getClass().getClassLoader();
        Class<?> actionBaseClass = Class.forName(ACTION_BASE_CLASS, false, loader);
        Class<?> setTargetClass = Class.forName(SET_TARGET_CLASS, false, loader);
        Class<?> modeClass = Class.forName(SET_TARGET_MODE_CLASS, false, loader);
        Method actionFactory = game.getClass().getMethod(ACTION_FACTORY_METHOD, Class.class);
        Object action = actionFactory.invoke(game, setTargetClass);
        Field aggressiveField = modeClass.getField(AGGRESSIVE_FIELD);
        Object aggressive = aggressiveField.get(null);
        Method configure = setTargetClass.getMethod(SET_TARGET_CONFIGURE_METHOD, int.class, modeClass);
        configure.invoke(action, id, aggressive);
        Method sendAction = game.getClass().getMethod(SEND_ACTION_METHOD, actionBaseClass);
        sendAction.invoke(game, action);
    }


    private static void sendTargetNone(Object game, int id) throws Exception {
        ClassLoader loader = game.getClass().getClassLoader();
        Class<?> actionBaseClass = Class.forName(ACTION_BASE_CLASS, false, loader);
        Class<?> setTargetClass = Class.forName(SET_TARGET_CLASS, false, loader);
        Class<?> modeClass = Class.forName(SET_TARGET_MODE_CLASS, false, loader);
        Method actionFactory = game.getClass().getMethod(ACTION_FACTORY_METHOD, Class.class);
        Object action = actionFactory.invoke(game, setTargetClass);
        Object[] modes = modeClass.getEnumConstants();
        if (modes == null || modes.length < 3) throw new IllegalStateException("TargetMode NONE indisponivel");
        Object none = modes[2]; // Confirmado anteriormente: NEUTRAL=0, AGGRESSIVE=1, NONE=2.
        Method configure = setTargetClass.getMethod(SET_TARGET_CONFIGURE_METHOD, int.class, modeClass);
        configure.invoke(action, id, none);
        Method sendAction = game.getClass().getMethod(SEND_ACTION_METHOD, actionBaseClass);
        sendAction.invoke(game, action);
    }

    private static List<Position> loadRoute() throws IOException {
        if (!Files.exists(ROUTE_FILE)) return List.of();
        List<Position> result = new ArrayList<>();
        for (String line : Files.readAllLines(ROUTE_FILE, StandardCharsets.UTF_8)) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            String[] parts = t.split(",");
            if (parts.length != 2) continue;
            try {
                result.add(new Position(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())));
            } catch (NumberFormatException ignored) { }
        }
        return result;
    }

    private static int findRouteIndex(List<Position> r, Position p) {
        for (int i = 0; i < r.size(); i++) if (r.get(i).equals(p)) return i;
        return -1;
    }

    private static int findNearestRouteIndex(List<Position> r, Position p, int radius) {
        int best = -1;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < r.size(); i++) {
            int d = cheb(r.get(i), p);
            if (d <= Math.max(0, radius) && d < bestDist) {
                best = i;
                bestDist = d;
            }
        }
        return best;
    }

    private static int findRouteIndexNear(List<Position> r, Position p, int preferred) {
        int best = -1;
        int bestDelta = Integer.MAX_VALUE;
        for (int i = 0; i < r.size(); i++) {
            if (r.get(i).equals(p)) {
                int delta = Math.abs(i - preferred);
                if (delta < bestDelta) {
                    best = i;
                    bestDelta = delta;
                }
            }
        }
        return best;
    }

    private static Config readConfig() {
        Config c = new Config();
        try {
            if (!Files.exists(CONFIG_FILE)) {
                writeDefaultConfig();
                return c;
            }
            for (String line : Files.readAllLines(CONFIG_FILE, StandardCharsets.UTF_8)) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#") || !t.contains("=")) continue;
                int eq = t.indexOf('=');
                String k = t.substring(0, eq).trim().toLowerCase();
                String v = t.substring(eq + 1).trim();
                try {
                    switch (k) {
                        case "combat_enabled" -> c.combatEnabled = parseBool(v, c.combatEnabled);
                        case "detection_range" -> c.detectionRange = clamp(Integer.parseInt(v), 1, 20);
                        case "ranged_detection_range" -> c.rangedDetectionRange = clamp(Integer.parseInt(v), 1, 20);
                        case "aggro_detection_range" -> c.aggroDetectionRange = clamp(Integer.parseInt(v), 1, 30);
                        case "combat_distance" -> c.combatDistance = clamp(Integer.parseInt(v), 1, 4);
                        case "combat_leash" -> c.combatLeash = clamp(Integer.parseInt(v), 2, 20);
                        case "loop_route" -> c.loopRoute = parseBool(v, c.loopRoute);
                        case "assist_movement" -> c.assistMovement = parseBool(v, c.assistMovement);
                        case "native_chase_grace_ms" -> c.nativeChaseGraceMs = clamp(Integer.parseInt(v), 0, 5000);
                        case "assist_move_cooldown_ms" -> c.assistMoveCooldownMs = clamp(Integer.parseInt(v), 50, 5000);
                        case "move_timeout_ms" -> c.moveTimeoutMs = clamp(Integer.parseInt(v), 350, 5000);
                        case "late_arrival_grace_ms" -> c.lateArrivalGraceMs = clamp(Integer.parseInt(v), 0, 1500);
                        case "max_move_retries" -> c.maxMoveRetries = clamp(Integer.parseInt(v), 0, 3);
                        case "world_poll_ms" -> c.worldPollMs = clamp(Integer.parseInt(v), 20, 500);
                        case "fast_player_poll_ms" -> c.fastPlayerPollMs = clamp(Integer.parseInt(v), 8, 100);
                        case "route_burst_size" -> c.routeBurstSize = clamp(Integer.parseInt(v), 1, 3);
                        case "route_burst_wait_ms" -> c.routeBurstWaitMs = clamp(Integer.parseInt(v), 80, 1000);
                        case "route_burst_timeout_ms" -> c.routeBurstTimeoutMs = clamp(Integer.parseInt(v), 150, 3000);
                        case "route_burst_retries" -> c.routeBurstRetries = clamp(Integer.parseInt(v), 0, 3);
                        case "unreachable_timeout_ms" -> c.unreachableTimeoutMs = clamp(Integer.parseInt(v), 500, 15000);
                        case "unreachable_failed_steps" -> c.unreachableFailedSteps = clamp(Integer.parseInt(v), 1, 4);
                        case "unreachable_blacklist_ms" -> c.unreachableBlacklistMs = clamp(Integer.parseInt(v), 2000, 900000);
                        case "hostile_unreachable_blacklist_ms" -> c.hostileUnreachableBlacklistMs = clamp(Integer.parseInt(v), 1000, 60000);
                        case "route_lookahead" -> c.routeLookahead = clamp(Integer.parseInt(v), 1, 20);
                        case "route_waypoint_tolerance" -> c.routeWaypointTolerance = clamp(Integer.parseInt(v), 0, 3);
                        case "route_join_radius" -> c.routeJoinRadius = clamp(Integer.parseInt(v), 0, 10);
                        case "path_detour_radius" -> c.pathDetourRadius = clamp(Integer.parseInt(v), 2, 20);
                        case "path_max_nodes" -> c.pathMaxNodes = clamp(Integer.parseInt(v), 256, 10000);
                        case "learned_blocked_ms" -> c.learnedBlockedMs = clamp(Integer.parseInt(v), 1000, 600000);
                        case "return_tolerance" -> c.returnTolerance = clamp(Integer.parseInt(v), 0, 3);
                        case "dynamic_block_grace_ms" -> c.dynamicBlockGraceMs = clamp(Integer.parseInt(v), 50, 1000);
                        case "dynamic_blocked_ms" -> c.dynamicBlockedMs = clamp(Integer.parseInt(v), 250, 10000);
                        case "anti_ks" -> c.antiKsEnabled = parseBool(v, c.antiKsEnabled);
                        case "anti_ks_player_radius" -> c.antiKsPlayerRadius = clamp(Integer.parseInt(v), 1, 8);
                        case "anti_ks_blacklist_ms" -> c.antiKsBlacklistMs = clamp(Integer.parseInt(v), 1000, 120000);
                        case "auto_food" -> c.autoFoodEnabled = parseBool(v, c.autoFoodEnabled);
                        case "auto_food_threshold" -> c.autoFoodThreshold = clamp(Integer.parseInt(v), 1, 99);
                        case "auto_food_cooldown_ms" -> c.autoFoodCooldownMs = clamp(Integer.parseInt(v), 1000, 60000);
                        case "auto_loot" -> c.autoLootEnabled = parseBool(v, c.autoLootEnabled);
                        case "loot_search_radius" -> c.lootSearchRadius = clamp(Integer.parseInt(v), 0, 3);
                        case "loot_open_range" -> c.lootOpenRange = clamp(Integer.parseInt(v), 1, 4);
                        case "loot_open_delay_ms" -> c.lootOpenDelayMs = clamp(Integer.parseInt(v), 50, 2000);
                        case "loot_confirm_ms" -> c.lootConfirmMs = clamp(Integer.parseInt(v), 100, 3000);
                        case "loot_task_timeout_ms" -> c.lootTaskTimeoutMs = clamp(Integer.parseInt(v), 500, 60000);
                        case "loot_max_attempts" -> c.lootMaxAttempts = clamp(Integer.parseInt(v), 1, 3);
                        case "loot_corpse_max_distance" -> c.lootCorpseMaxDistance = clamp(Integer.parseInt(v), 2, 12);
                        case "attack_priority" -> c.attackPriority = HelperConfig.AttackPriority.parse(v);
                    }
                } catch (Exception ignored) { }
            }
        } catch (IOException e) {
            event("CONFIG ERRO - usando defaults: " + e);
        }
        return c;
    }

    private static void ensureFiles() throws IOException {
        Files.createDirectories(LAB_DIR);
        Files.createDirectories(STATE_DIR);
        Files.createDirectories(RUNTIME_DIR);
        Files.createDirectories(ROUTE_FILE.getParent());
        Path oldConfig = LAB_DIR.resolve("cavebot_config.txt");
        if (!Files.exists(CONFIG_FILE) && Files.exists(oldConfig)) {
            Files.copy(oldConfig, CONFIG_FILE);
        }
        if (!Files.exists(CONTROL_FILE)) {
            Files.writeString(CONTROL_FILE, "stop\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
        if (!Files.exists(CONFIG_FILE)) writeDefaultConfig();
        migrateConfigV64();
    }

    private static void migrateConfigV64() throws IOException {
        if (!Files.exists(CONFIG_FILE)) return;
        String raw = Files.readString(CONFIG_FILE, StandardCharsets.UTF_8);
        String updated = raw;
        updated = updated.replace("# MirageLab CaveBot v8.3", "# MirageLab CaveBot v8.4");
        updated = updated.replace("# MirageLab CaveBot v8.0", "# MirageLab CaveBot v8.4");
        updated = updated.replace("# MirageLab CaveBot v7.0", "# MirageLab CaveBot v8.4");
        updated = updated.replace("# MirageLab CaveBot v6.2", "# MirageLab CaveBot v8.4");
        updated = updated.replace("# MirageLab CaveBot v6.3.1", "# MirageLab CaveBot v8.4");
        updated = updated.replace("# MirageLab CaveBot v6.3", "# MirageLab CaveBot v8.4");
        updated = replaceExactConfig(updated, "native_chase_grace_ms", "700", "250");
        updated = replaceExactConfig(updated, "assist_move_cooldown_ms", "450", "120");
        updated = replaceExactConfig(updated, "move_timeout_ms", "1600", "650");
        updated = replaceExactConfig(updated, "max_move_retries", "1", "0");
        updated = replaceExactConfig(updated, "world_poll_ms", "60", "25");
        updated = replaceExactConfig(updated, "route_lookahead", "5", "8");
        updated = replaceExactConfig(updated, "path_detour_radius", "6", "7");
        updated = replaceExactConfig(updated, "path_max_nodes", "2500", "3500");
        updated = replaceExactConfig(updated, "learned_blocked_ms", "30000", "15000");
        updated = appendConfigIfMissing(updated, "dynamic_block_grace_ms", "140");
        updated = appendConfigIfMissing(updated, "dynamic_blocked_ms", "1200");
        updated = appendConfigIfMissing(updated, "late_arrival_grace_ms", "1400");
        updated = updated.replace("# MirageLab CaveBot v6.4.1", "# MirageLab CaveBot v8.4");
        updated = updated.replace("# MirageLab CaveBot v6.4", "# MirageLab CaveBot v8.4");
        updated = replaceExactConfig(updated, "unreachable_timeout_ms", "3500", "8000");
        updated = replaceExactConfig(updated, "unreachable_failed_steps", "2", "4");
        updated = replaceExactConfig(updated, "unreachable_blacklist_ms", "120000", "15000");
        updated = appendConfigIfMissing(updated, "hostile_unreachable_blacklist_ms", "3000");
        updated = appendConfigIfMissing(updated, "ranged_detection_range", "7");
        updated = appendConfigIfMissing(updated, "aggro_detection_range", "12");
        updated = appendConfigIfMissing(updated, "anti_ks", "true");
        updated = appendConfigIfMissing(updated, "anti_ks_player_radius", "2");
        updated = appendConfigIfMissing(updated, "anti_ks_blacklist_ms", "15000");
        updated = appendConfigIfMissing(updated, "auto_food", "true");
        updated = appendConfigIfMissing(updated, "auto_food_threshold", "30");
        updated = appendConfigIfMissing(updated, "auto_food_cooldown_ms", "10000");
        updated = appendConfigIfMissing(updated, "auto_food_hotkey", "1");
        updated = appendConfigIfMissing(updated, "auto_loot", "true");
        updated = appendConfigIfMissing(updated, "loot_search_radius", "1");
        updated = appendConfigIfMissing(updated, "loot_open_range", "4");
        updated = replaceExactConfig(updated, "loot_open_delay_ms", "250", "700");
        updated = replaceExactConfig(updated, "loot_open_delay_ms", "400", "700");
        updated = appendConfigIfMissing(updated, "loot_open_delay_ms", "700");
        updated = replaceExactConfig(updated, "loot_confirm_ms", "450", "800");
        updated = replaceExactConfig(updated, "loot_confirm_ms", "500", "800");
        updated = appendConfigIfMissing(updated, "loot_confirm_ms", "800");
        updated = replaceExactConfig(updated, "loot_task_timeout_ms", "2500", "45000");
        updated = replaceExactConfig(updated, "loot_task_timeout_ms", "20000", "45000");
        updated = appendConfigIfMissing(updated, "loot_task_timeout_ms", "45000");
        updated = replaceExactConfig(updated, "loot_max_attempts", "2", "1");
        updated = appendConfigIfMissing(updated, "loot_max_attempts", "1");
        updated = appendConfigIfMissing(updated, "loot_corpse_max_distance", "8");
        updated = appendConfigIfMissing(updated, "attack_priority", "NEAREST");
        updated = replaceExactConfig(updated, "native_chase_grace_ms", "350", "250");
        updated = replaceExactConfig(updated, "assist_move_cooldown_ms", "180", "120");
        updated = replaceExactConfig(updated, "move_timeout_ms", "1000", "650");
        updated = replaceExactConfig(updated, "late_arrival_grace_ms", "500", "1400");
        updated = replaceExactConfig(updated, "world_poll_ms", "30", "25");
        updated = replaceExactConfig(updated, "world_poll_ms", "25", "60");
        updated = appendConfigIfMissing(updated, "fast_player_poll_ms", "12");
        updated = appendConfigIfMissing(updated, "route_burst_size", "2");
        updated = appendConfigIfMissing(updated, "route_burst_wait_ms", "170");
        updated = appendConfigIfMissing(updated, "route_burst_timeout_ms", "480");
        updated = appendConfigIfMissing(updated, "route_burst_retries", "1");

        if (!updated.equals(raw)) {
            Files.writeString(CONFIG_FILE, updated, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    private static String replaceExactConfig(String text, String key, String oldValue, String newValue) {
        return text.replace(key + "=" + oldValue, key + "=" + newValue);
    }

    private static String appendConfigIfMissing(String text, String key, String value) {
        if (text.matches("(?s).*?(?:^|\\R)\\Q" + key + "\\E\\s*=.*")) return text;
        String sep = text.endsWith("\n") || text.endsWith("\r") ? "" : System.lineSeparator();
        return text + sep + key + "=" + value + System.lineSeparator();
    }

    private static void writeDefaultConfig() throws IOException {
        String text = "# MirageLab CaveBot v" + VERSION + "\n"
                + "# Distancias usam max(|dx|,|dy|).\n"
                + "combat_enabled=true\n"
                + "attack_priority=NEAREST\n"
                + "detection_range=4\n"
                + "ranged_detection_range=7\n"
                + "aggro_detection_range=12\n"
                + "combat_distance=1\n"
                + "combat_leash=6\n"
                + "loop_route=true\n"
                + "# Primeiro deixa o comportamento AGGRESSIVE nativo aproximar; depois auxilia se necessario.\n"
                + "assist_movement=true\n"
                + "native_chase_grace_ms=250\n"
                + "assist_move_cooldown_ms=120\n"
                + "move_timeout_ms=650\n"
                + "late_arrival_grace_ms=1400\n"
                + "max_move_retries=0\n"
                + "world_poll_ms=60\n"
                + "fast_player_poll_ms=12\n"
                + "route_burst_size=2\n"
                + "route_burst_wait_ms=170\n"
                + "route_burst_timeout_ms=480\n"
                + "route_burst_retries=1\n"
                + "dynamic_block_grace_ms=140\n"
                + "dynamic_blocked_ms=1200\n"
                + "# Se um alvo nao puder ser alcancado, abandona e ignora temporariamente.\n"
                + "unreachable_timeout_ms=8000\n"
                + "unreachable_failed_steps=4\n"
                + "unreachable_blacklist_ms=15000\n"
                + "hostile_unreachable_blacklist_ms=3000\n"
                + "route_lookahead=8\n"
                + "route_waypoint_tolerance=1\n"
                + "route_join_radius=3\n"
                + "path_detour_radius=7\n"
                + "path_max_nodes=3500\n"
                + "learned_blocked_ms=15000\n"
                + "return_tolerance=1\n"
                + "anti_ks=true\n"
                + "anti_ks_player_radius=2\n"
                + "anti_ks_blacklist_ms=15000\n"
                + "auto_food=true\n"
                + "auto_food_threshold=30\n"
                + "auto_food_cooldown_ms=10000\n"
                + "auto_food_hotkey=1\n"
                + "auto_loot=true\n"
                + "loot_search_radius=1\n"
                + "loot_open_range=4\n"
                + "loot_open_delay_ms=700\n"
                + "loot_confirm_ms=800\n"
                + "loot_task_timeout_ms=45000\n"
                + "loot_max_attempts=1\n"
                + "loot_corpse_max_distance=8\n";
        Files.writeString(CONFIG_FILE, text, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static boolean parseBool(String s, boolean fallback) {
        String v = s.trim().toLowerCase();
        if (v.equals("true") || v.equals("1") || v.equals("on") || v.equals("yes") || v.equals("sim")) return true;
        if (v.equals("false") || v.equals("0") || v.equals("off") || v.equals("no") || v.equals("nao")) return false;
        return fallback;
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.replace("\u0000", "").replace("\uFEFF", "").trim().toLowerCase();
    }

    private static void updateStatus(long now, String message) {
        if (!message.equals(lastStatus) || now >= nextStatusWriteAt) {
            nextStatusWriteAt = now + 500L;
            writeStatus(message);
        }
    }

    private static void writeStatus(String message) {
        lastStatus = message;
        try {
            String target = targetId == null ? "-" : targetId + ":" + (targetName == null ? "" : targetName);
            String anchor = combatAnchor == null ? "-" : fmt(combatAnchor);
            String text = LocalDateTime.now() + "  MirageLab v" + VERSION + "\n"
                    + "state=" + state + " active=" + active + "\n"
                    + "route_index=" + routeIndex + "/" + Math.max(0, route.size() - 1)
                    + " loops=" + completedLoops + "\n"
                    + "target=" + target + " anchor=" + anchor + " trail=" + combatTrail.size()
                    + " blacklist=" + unreachableUntil.size() + " contested=" + contestedUntil.size()
                    + " loot_queue=" + lootQueue.size() + " looted=" + lootedBags + " loot_skip=" + skippedLootBags
                    + " autoloot=" + (config.autoLootEnabled && !autoLootSessionDisabled ? "ON" : "OFF") + "\n"
                    + autoFoodSummary + " anti_ks=" + (config.antiKsEnabled ? "ON" : "OFF")
                    + " ranged=" + config.rangedDetectionRange + " smooth=" + config.routeBurstSize + "x@" + config.fastPlayerPollMs + "ms\n"
                    + message + "\n";
            Files.writeString(STATUS_FILE, text, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ignored) { }
    }

    private static long blockageWindowStart; private static int blockageBurst;
    private static void event(String message) {
        if (message.equals(lastEvent)) return;
        // Adaptive fail-safe: do not retry blocked moves forever.
        if(active && message.startsWith("ROUTE TILE BLOQUEADO")){
            long now=System.currentTimeMillis();
            if(now-blockageWindowStart>30000){blockageWindowStart=now;blockageBurst=0;}
            if(++blockageBurst>=12){blockageBurst=0;stop("PAUSA SEGURANCA: 12 tiles bloqueados em 30s");return;}
        }
        lastEvent = message;
        try {
            Files.writeString(EVENTS_FILE, LocalDateTime.now() + "  " + message + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) { }
        LabHook.status("v" + VERSION + " cavebot: " + message);
    }

    private static String safeMessage(Throwable t) {
        String s = t.getMessage();
        return s == null ? t.toString() : s;
    }

    private static int cheb(Position a, Position b) {
        return Math.max(Math.abs(a.x - b.x), Math.abs(a.y - b.y));
    }

    private static int manhattan(Position a, Position b) {
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
    }

    private static String fmt(Position p) { return p == null ? "<null>" : p.x + "," + p.y; }

    private enum State { STOPPED, ROUTE, COMBAT, LOOTING, RETURNING }
    private enum MovePurpose { ROUTE, COMBAT, LOOT, RETURN }
    private enum Direction {
        NORTH, SOUTH, EAST, WEST;
        static Direction from(Position from, Position to) {
            if (from == null || to == null) return null;
            int dx = to.x - from.x;
            int dy = to.y - from.y;
            if (dx == 1 && dy == 0) return EAST;
            if (dx == -1 && dy == 0) return WEST;
            if (dx == 0 && dy == -1) return NORTH;
            if (dx == 0 && dy == 1) return SOUTH;
            return null;
        }
    }

    private record Position(int x, int y) { }
    private record Monster(int id, String name, Position pos) { }
    private record Occupant(int id, String name, String type, Position pos) { }
    private record WorldState(Position player, Object playerEntity, Map<Integer, Monster> monsters,
                              Map<Integer, Occupant> occupants, Set<Position> occupied,
                              List<AutoLootSupport.Candidate> lootables,
                              Set<Integer> claimedMonsterIds, Set<Integer> hostileMonsterIds, Integer localTargetId,
                              boolean remoteTargetTelemetry) { }
    private record LootRequest(int monsterId, String monsterName, Position corpse, long diedAtMs) { }
    private record PendingMove(MovePurpose purpose, Position from, Position to,
                               Direction direction, long sentAtMs, int retries) { }
    private record PendingRouteBurst(Position from, List<Position> steps, long sentAtMs, long lastProgressAtMs, int retries) { }
    private record PathChoice(int targetIndex, List<Position> path) { }

    private static final class FastPositionAccess {
        final Class<?> entityClass, positionClass;
        final Method getComponent;
        final Field xField, yField;
        FastPositionAccess(Class<?> entityClass, Class<?> positionClass, Method getComponent, Field xField, Field yField) {
            this.entityClass=entityClass; this.positionClass=positionClass; this.getComponent=getComponent;
            this.xField=xField; this.yField=yField;
        }
    }

    private static final class Config {
        boolean combatEnabled = true;
        HelperConfig.AttackPriority attackPriority = HelperConfig.AttackPriority.NEAREST;
        int detectionRange = 4;
        int rangedDetectionRange = 7;
        int aggroDetectionRange = 12;
        int combatDistance = 1;
        int combatLeash = 6;
        boolean loopRoute = true;
        boolean assistMovement = true;
        int nativeChaseGraceMs = 250;
        int assistMoveCooldownMs = 120;
        int moveTimeoutMs = 650;
        int lateArrivalGraceMs = 1400;
        int maxMoveRetries = 0;
        int worldPollMs = 60;
        int fastPlayerPollMs = 12;
        int routeBurstSize = 2;
        int routeBurstWaitMs = 170;
        int routeBurstTimeoutMs = 480;
        int routeBurstRetries = 1;
        int dynamicBlockGraceMs = 140;
        int dynamicBlockedMs = 1200;
        int unreachableTimeoutMs = 8000;
        int unreachableFailedSteps = 4;
        int unreachableBlacklistMs = 15000;
        int hostileUnreachableBlacklistMs = 3000;
        int routeLookahead = 8;
        int routeWaypointTolerance = 1;
        int routeJoinRadius = 3;
        int pathDetourRadius = 7;
        int pathMaxNodes = 3500;
        int learnedBlockedMs = 15000;
        int returnTolerance = 1;
        boolean antiKsEnabled = true;
        int antiKsPlayerRadius = 2;
        int antiKsBlacklistMs = 15000;
        boolean autoFoodEnabled = true;
        int autoFoodThreshold = 30;
        int autoFoodCooldownMs = 5000;
        boolean autoLootEnabled = true;
        int lootSearchRadius = 1;
        int lootOpenRange = 4;
        int lootOpenDelayMs = 700;
        int lootConfirmMs = 800;
        int lootTaskTimeoutMs = 45000;
        int lootMaxAttempts = 1;
        int lootCorpseMaxDistance = 8;
    }
}
