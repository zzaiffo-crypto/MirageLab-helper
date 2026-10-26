package miragelab;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class LabHook {
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
    private static final String SET_TARGET_CLASS =
            "uk.co.harveydogs.mirage.shared.ppGE4fatBnKfcPyX4Kmtne6Lgsjv.yY8x1yOsuoav.yY8x1yOsuoav.BqDbTihL38.NT6CLfh1EFZKAgt882tQ6RNRZLmx";
    private static final String SET_TARGET_MODE_CLASS = SET_TARGET_CLASS + "$NT6CLfh1EFZKAgt882tQ6RNRZLmx";
    private static final String ACTION_FACTORY_METHOD = "eACqBMENic3YHQLgkQTnYXFlcXmJ";
    private static final String SET_TARGET_CONFIGURE_METHOD = "eACqBMENic3YHQLgkQTnYXFlcXmJ";
    private static final String SEND_ACTION_METHOD = "eACqBMENic3YHQLgkQTnYXFlcXmJ";
    private static final String AGGRESSIVE_FIELD = "FIeR9ZxfSOyX";

    private static final String MOVE_ACTION_CLASS =
            "uk.co.harveydogs.mirage.shared.ppGE4fatBnKfcPyX4Kmtne6Lgsjv.yY8x1yOsuoav.ppGE4fatBnKfcPyX4Kmtne6Lgsjv.AjEyNvkjjLUdT";

    private static final Path LAB_DIR = Path.of(
            System.getProperty("user.home"), "Downloads", "MirageLab");
    private static final Path STATE_DIR = LAB_DIR.resolve("state");
    private static final Path RUNTIME_DIR = LAB_DIR.resolve("runtime");
    private static final Path STATUS_FILE = RUNTIME_DIR.resolve("agent_status.txt");
    private static final Path MONSTERS_FILE = RUNTIME_DIR.resolve("monsters_live.txt");
    private static final Path SPATIAL_FILE = RUNTIME_DIR.resolve("spatial_live.txt");
    private static final Path ATTACK_TARGET_FILE = STATE_DIR.resolve("attack_target.txt");
    private static final Path ATTACK_STATUS_FILE = RUNTIME_DIR.resolve("attack_status.txt");
    private static final Path MAX_RANGE_FILE = STATE_DIR.resolve("max_range.txt");
    private static final Path AUTO_ATTACK_FILE = STATE_DIR.resolve("auto_attack.txt");
    private static final Path AUTO_STATUS_FILE = RUNTIME_DIR.resolve("auto_attack_status.txt");

    private static final Path ROUTES_DIR = LAB_DIR.resolve("routes");
    private static final Path RECORD_CONTROL_FILE = STATE_DIR.resolve("recording.txt");
    private static final Path RECORD_STATUS_FILE = RUNTIME_DIR.resolve("recording_status.txt");
    private static final Path CURRENT_ROUTE_FILE = ROUTES_DIR.resolve("route_current.txt");
    private static final Path CURRENT_ROUTE_TMP_FILE = ROUTES_DIR.resolve("route_current.tmp");

    private static final Path PLAYBACK_CONTROL_FILE = STATE_DIR.resolve("playback.txt");
    private static final Path PLAYBACK_STATUS_FILE = RUNTIME_DIR.resolve("playback_status.txt");
    private static final Path MOVE_LEARNING_FILE = RUNTIME_DIR.resolve("move_learning.txt");

    private static long nextScanAtMs = 0L;
    private static long nextRecorderPollAtMs = 0L;
    private static Map<Integer, String> lastSnapshot = Map.of();
    private static boolean initialized = false;
    private static boolean failed = false;
    private static String lastAttackCommand = null;
    private static Integer currentAutoTargetId = null;
    private static String currentAutoTargetName = null;
    private static String lastAutoEvent = null;

    private static boolean recordingActive = false;
    private static String lastRecordingCommand = null;
    private static final List<Position> recordedRoute = new ArrayList<>();
    private static Position lastRecordedPosition = null;
    private static LocalDateTime recordingStartedAt = null;
    private static int recordingGapCount = 0;

    private static final Object MOVE_LOCK = new Object();
    private static final Deque<MoveSnapshot> pendingMoveActions = new ArrayDeque<>();
    private static final List<MoveObservation> moveObservations = new ArrayList<>();
    private static long moveActionSequence = 0L;
    private static boolean sendingPlaybackAction = false;
    private static int lastObservedActionIdentity = 0;
    private static long lastObservedActionAtMs = 0L;

    private static boolean playbackActive = false;
    private static String lastPlaybackCommand = null;
    private static List<Position> playbackRoute = List.of();
    private static int playbackIndex = 0;
    private static Position playbackWaitingFor = null;
    private static Position playbackSentFrom = null;
    private static long playbackSentAtMs = 0L;
    private static int playbackRetries = 0;

    private LabHook() {}

    static boolean isRecordingActiveForCaveBot() {
        return recordingActive;
    }

    /** Never let a MirageLab callback abort the game render or networking pipeline. */
    public static void safeTick(Object game) {
        try { tick(game); }
        catch (Throwable failure) { reportHookFailure("render",failure); }
    }

    public static void safeObserveAction(Object action) {
        try { observeAction(action); }
        catch (Throwable failure) { reportHookFailure("action",failure); }
    }

    private static volatile long lastHookFailureAt;
    private static void reportHookFailure(String origin,Throwable failure) {
        long now=System.currentTimeMillis();
        if(now-lastHookFailureAt<5000L)return;
        lastHookFailureAt=now;
        try {
            System.err.println("[MirageLab] Hook " + origin + " failed: " + failure);
            failure.printStackTrace(System.err);
        } catch (Throwable ignored) { }
        try { status("Hook "+origin+" error: "+failure); }catch(Throwable ignored){}
    }

    public static void tick(Object game) {
        long now = System.currentTimeMillis();
        if (failed) {
            return;
        }

        if (now >= nextRecorderPollAtMs) {
            nextRecorderPollAtMs = now + 16L;
            try {
                processRecorder(game);
            } catch (Throwable t) {
                recordingStatus("ERRO NO RECORDER: " + t);
                status("recorder error: " + t);
            }
            // Priority order for helper-generated actions:
            // release any held key pulse first; then healing > cave/loot/food > combat skills > mana training > target selection.
            try { InputArbiter.tick(game, now); } catch (Throwable t) { status("v8.4 input pulse tick error: " + t); }
            try {
                AutoHealEngine.tick(game, now);
            } catch (Throwable t) {
                status("v8.4 autoheal tick error: " + t);
            }
            try { AutoFoodEngine.tick(game, now); } catch (Throwable t) { status("autofood tick error: " + t); }
            try { AnalyzerEngine.tick(now); } catch (Throwable ignored) { }
            try { SecurityMonitor.tick(game,now); } catch (Throwable ignored) { }
            try {
                CaveBotEngine.tick(game, now);
            } catch (Throwable t) {
                status("v8.4 cavebot tick error: " + t);
            }
            try {
                SkillEngine.tick(game, now);
            } catch (Throwable t) {
                status("v8.4 skills tick error: " + t);
            }
            try {
                ManaTrainingEngine.tick(game, now);
            } catch (Throwable t) {
                status("v8.4 mana training tick error: " + t);
            }
            try {
                AutoAttackEngine.tick(game, now);
            } catch (Throwable t) {
                status("v8.4 autoattack tick error: " + t);
            }
            if (!CaveBotEngine.isActive()) {
                try {
                    processPlayback(game, now);
                } catch (Throwable t) {
                    playbackStatus("ERRO NO PLAYBACK: " + t);
                    status("playback error: " + t);
                    playbackActive = false;
                }
            }
        }

        if (now < nextScanAtMs) {
            return;
        }
        nextScanAtMs = now + 1000L;

        try {
            ClassLoader loader = game.getClass().getClassLoader();
            Class<?> identityClass = Class.forName(IDENTITY_CLASS, false, loader);
            Class<?> positionClass = Class.forName(POSITION_CLASS, false, loader);

            Method worldGetter = game.getClass().getMethod(WORLD_GETTER);
            Object world = worldGetter.invoke(game);
            if (world == null) {
                return;
            }

            Method getEntities = world.getClass().getMethod("getEntities");
            Object entitiesObject = getEntities.invoke(world);
            if (!(entitiesObject instanceof Iterable<?> entities)) {
                throw new IllegalStateException("Ashley entity collection is not Iterable: "
                        + entitiesObject.getClass().getName());
            }

            Field typeField = identityClass.getField(TYPE_FIELD);
            Field idField = identityClass.getField(ID_FIELD);
            Field nameField = identityClass.getField(NAME_FIELD);
            Field xField = positionClass.getField(POS_X_FIELD);
            Field yField = positionClass.getField(POS_Y_FIELD);

            Method playerGetter = world.getClass().getMethod(PLAYER_ENTITY_GETTER);
            Object playerEntity = playerGetter.invoke(world);
            Position playerPos = readPosition(playerEntity, positionClass, xField, yField);

            Map<Integer, String> monsters = new TreeMap<>();
            List<MonsterInfo> spatial = new ArrayList<>();

            for (Object entity : entities) {
                Method getComponent = entity.getClass().getMethod("getComponent", Class.class);
                Object identity = getComponent.invoke(entity, identityClass);
                if (identity == null) {
                    continue;
                }

                Object type = typeField.get(identity);
                if (!(type instanceof Enum<?> entityType)
                        || !"NPC_MONSTER".equals(entityType.name())) {
                    continue;
                }

                int id = idField.getInt(identity);
                Object rawName = nameField.get(identity);
                String name = rawName == null ? "<sem nome>" : rawName.toString();
                monsters.put(id, name);

                Position pos = readPosition(entity, positionClass, xField, yField);
                if (playerPos != null && pos != null) {
                    int dx = pos.x - playerPos.x;
                    int dy = pos.y - playerPos.y;
                    int manhattan = Math.abs(dx) + Math.abs(dy);
                    int chebyshev = Math.max(Math.abs(dx), Math.abs(dy));
                    spatial.add(new MonsterInfo(id, name, pos.x, pos.y, dx, dy, manhattan, chebyshev));
                }
            }

            spatial.sort(Comparator
                    .comparingInt((MonsterInfo m) -> m.manhattan)
                    .thenComparingInt(m -> m.chebyshev)
                    .thenComparingInt(m -> m.id));

            if (!initialized || !monsters.equals(lastSnapshot)) {
                initialized = true;
                lastSnapshot = Map.copyOf(monsters);
                writeSnapshot(monsters);
            }

            int maxRange = readMaxRange();
            // v8.4: legacy file-based autoattack is superseded by the modular AutoAttackEngine.
            boolean autoEnabled = false;
            writeSpatial(playerPos, spatial, maxRange, autoEnabled);
            if (currentAutoTargetId != null) {
                autoEvent("LEGACY OFF - controle transferido ao Auto Attack do Helper");
            }
            currentAutoTargetId = null;
            currentAutoTargetName = null;
            processAttackCommand(game, loader, monsters, spatial, maxRange);
        } catch (Throwable t) {
            failed = true;
            status("hook failed: " + t);
            attackStatus("ERRO: " + t);
        }
    }

    private static void processRecorder(Object game) throws Exception {
        ensureLabDirs();
        Files.createDirectories(ROUTES_DIR);

        if (!Files.exists(RECORD_CONTROL_FILE)) {
            Files.writeString(RECORD_CONTROL_FILE, "stop" + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }

        String command = normalizeControl(Files.readString(RECORD_CONTROL_FILE, StandardCharsets.UTF_8));
        if (!command.equals(lastRecordingCommand)) {
            lastRecordingCommand = command;
            handleRecordingCommand(command, game);
        }

        if (!recordingActive) {
            return;
        }

        Position current = readPlayerPosition(game);
        if (current == null) {
            recordingStatus("GRAVANDO - aguardando posicao do player");
            return;
        }

        appendRecordedPosition(current);
    }

    private static String normalizeControl(String raw) {
        return raw == null ? "" : raw.replace("\u0000", "").trim().toLowerCase();
    }

    private static void handleRecordingCommand(String command, Object game) throws Exception {
        if (command == null || command.isBlank()) return;
        if (command.startsWith("start") || command.startsWith("record") || command.startsWith("on")) {
            startRecording(game);
        } else if (command.startsWith("stop") || command.startsWith("off")) {
            stopRecording();
        } else if (command.startsWith("clear") || command.startsWith("limpar")) {
            clearRecording();
        } else {
            recordingStatus("COMANDO INVALIDO - use start, stop ou clear: " + command);
        }
    }

    private static void startRecording(Object game) throws Exception {
        recordedRoute.clear();
        lastRecordedPosition = null;
        recordingGapCount = 0;
        recordingStartedAt = LocalDateTime.now();
        recordingActive = true;
        synchronized (MOVE_LOCK) {
            pendingMoveActions.clear();
            moveObservations.clear();
        }
        writeMoveLearningFile();

        Position current = readPlayerPosition(game);
        if (current != null) {
            appendRecordedPosition(current);
        } else {
            writeRouteFile();
            recordingStatus("GRAVANDO - iniciado; aguardando posicao do player");
        }
        status("v8.4 route recorder START");
    }

    private static void stopRecording() throws IOException {
        if (!recordingActive) {
            recordingStatus(recordedRoute.isEmpty()
                    ? "PARADO - nenhuma rota gravada"
                    : "PARADO - rota preservada com " + recordedRoute.size() + " pontos em " + CURRENT_ROUTE_FILE);
            return;
        }

        recordingActive = false;
        writeMoveLearningFile();
        writeRouteFile();
        String end = recordedRoute.isEmpty() ? "<sem pontos>"
                : recordedRoute.get(recordedRoute.size() - 1).x + "," + recordedRoute.get(recordedRoute.size() - 1).y;
        recordingStatus("PARADO - rota salva: pontos=" + recordedRoute.size()
                + ", gaps=" + recordingGapCount + ", fim=" + end);
        int learnedCount;
        synchronized (MOVE_LOCK) { learnedCount = moveObservations.size(); }
        status("v8.4 route recorder STOP points=" + recordedRoute.size() + " gaps=" + recordingGapCount
                + " moveObservations=" + learnedCount);
    }

    private static void clearRecording() throws IOException {
        recordingActive = false;
        recordedRoute.clear();
        lastRecordedPosition = null;
        recordingStartedAt = null;
        recordingGapCount = 0;
        synchronized (MOVE_LOCK) {
            pendingMoveActions.clear();
            moveObservations.clear();
        }
        Files.deleteIfExists(CURRENT_ROUTE_FILE);
        Files.deleteIfExists(MOVE_LEARNING_FILE);
        Files.deleteIfExists(CURRENT_ROUTE_TMP_FILE);
        recordingStatus("LIMPO - route_current.txt removido");
        status("route recorder CLEAR");
    }

    private static Position readPlayerPosition(Object game) throws Exception {
        ClassLoader loader = game.getClass().getClassLoader();
        Class<?> positionClass = Class.forName(POSITION_CLASS, false, loader);
        Field xField = positionClass.getField(POS_X_FIELD);
        Field yField = positionClass.getField(POS_Y_FIELD);

        Method worldGetter = game.getClass().getMethod(WORLD_GETTER);
        Object world = worldGetter.invoke(game);
        if (world == null) {
            return null;
        }

        Method playerGetter = world.getClass().getMethod(PLAYER_ENTITY_GETTER);
        Object playerEntity = playerGetter.invoke(world);
        return readPosition(playerEntity, positionClass, xField, yField);
    }

    private static void appendRecordedPosition(Position current) throws IOException {
        if (lastRecordedPosition == null) {
            recordedRoute.add(current);
            lastRecordedPosition = current;
            writeRouteFile();
            recordingStatus("GRAVANDO - pontos=1 atual=" + current.x + "," + current.y);
            return;
        }

        if (current.equals(lastRecordedPosition)) {
            return;
        }

        int dx = current.x - lastRecordedPosition.x;
        int dy = current.y - lastRecordedPosition.y;
        int manhattan = Math.abs(dx) + Math.abs(dy);

        Position cursor = lastRecordedPosition;
        if (dx == 0 && Math.abs(dy) > 1) {
            int step = Integer.signum(dy);
            for (int y = lastRecordedPosition.y + step; y != current.y + step; y += step) {
                Position next = new Position(current.x, y);
                learnMoveForStep(cursor, next);
                recordedRoute.add(next);
                cursor = next;
            }
        } else if (dy == 0 && Math.abs(dx) > 1) {
            int step = Integer.signum(dx);
            for (int x = lastRecordedPosition.x + step; x != current.x + step; x += step) {
                Position next = new Position(x, current.y);
                learnMoveForStep(cursor, next);
                recordedRoute.add(next);
                cursor = next;
            }
        } else {
            if (manhattan > 1) {
                recordingGapCount++;
            }
            learnMoveForStep(lastRecordedPosition, current);
            recordedRoute.add(current);
        }

        lastRecordedPosition = current;
        writeMoveLearningFile();
        writeRouteFile();
        recordingStatus("GRAVANDO - pontos=" + recordedRoute.size()
                + " atual=" + current.x + "," + current.y
                + " gaps=" + recordingGapCount);
    }

    private static void writeRouteFile() throws IOException {
        Files.createDirectories(ROUTES_DIR);
        StringBuilder out = new StringBuilder();
        out.append("# MirageLab Route v1").append(System.lineSeparator());
        out.append("# recording=").append(recordingActive ? "ON" : "OFF").append(System.lineSeparator());
        out.append("# updated=").append(LocalDateTime.now()).append(System.lineSeparator());
        out.append("# started=").append(recordingStartedAt == null ? "-" : recordingStartedAt).append(System.lineSeparator());
        out.append("# points=").append(recordedRoute.size()).append(System.lineSeparator());
        out.append("# gaps=").append(recordingGapCount).append(System.lineSeparator());
        if (!recordedRoute.isEmpty()) {
            Position start = recordedRoute.get(0);
            Position end = recordedRoute.get(recordedRoute.size() - 1);
            out.append("# start=").append(start.x).append(',').append(start.y).append(System.lineSeparator());
            out.append("# end=").append(end.x).append(',').append(end.y).append(System.lineSeparator());
        }
        out.append(System.lineSeparator());
        for (Position p : recordedRoute) {
            out.append(p.x).append(',').append(p.y).append(System.lineSeparator());
        }

        Files.writeString(CURRENT_ROUTE_TMP_FILE, out.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        try {
            Files.move(CURRENT_ROUTE_TMP_FILE, CURRENT_ROUTE_FILE,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicNotSupported) {
            Files.move(CURRENT_ROUTE_TMP_FILE, CURRENT_ROUTE_FILE,
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void recordingStatus(String message) {
        try {
            ensureLabDirs();
            String text = LocalDateTime.now() + "  " + message + System.lineSeparator();
            Files.writeString(RECORD_STATUS_FILE, text, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ignored) {
        }
    }

    /** Public, non-throwing bridge for XP packets injected into the game's package.
     *  Do not invoke package-private AnalyzerEngine directly from game bytecode.
     */
    public static void recordExperienceEvent(int delta) {
        try {
            AnalyzerEngine.gainedXpEvent(delta);
        } catch (Throwable failure) {
            try { status("XP bridge error: " + failure); } catch (Throwable ignored) { }
        }
    }

    public static void observeAction(Object action) {
        if (action == null) {
            return;
        }

        // v8.4: SetTarget is observed at the send boundary even when route recording is OFF.
        // This lets AutoAttack distinguish a real manual click from its own retarget action.
        try {
            AutoAttackEngine.observeSetTargetAction(action, System.currentTimeMillis());
        } catch (Throwable ignored) {
        }

        if (sendingPlaybackAction || !recordingActive) {
            return;
        }
        if (!MOVE_ACTION_CLASS.equals(action.getClass().getName())) {
            return;
        }

        try {
            long now = System.currentTimeMillis();
            int identity = System.identityHashCode(action);
            synchronized (MOVE_LOCK) {
                if (identity == lastObservedActionIdentity && now - lastObservedActionAtMs < 25L) {
                    return;
                }
                lastObservedActionIdentity = identity;
                lastObservedActionAtMs = now;
            }
            MoveSnapshot snapshot = captureMoveSnapshot(action);
            synchronized (MOVE_LOCK) {
                pendingMoveActions.addLast(snapshot);
                while (pendingMoveActions.size() > 32) {
                    pendingMoveActions.removeFirst();
                }
            }
        } catch (Throwable t) {
            status("move observe error: " + t);
        }
    }

    private static MoveSnapshot captureMoveSnapshot(Object action) throws Exception {
        Map<String, CapturedValue> values = new LinkedHashMap<>();
        Class<?> c = action.getClass();
        while (c != null && !ACTION_BASE_CLASS.equals(c.getName()) && c != Object.class) {
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(action);
                CapturedValue captured = CapturedValue.of(value);
                if (captured != null) {
                    values.put(c.getName() + "#" + field.getName(), captured);
                }
            }
            c = c.getSuperclass();
        }
        return new MoveSnapshot(++moveActionSequence, System.currentTimeMillis(), values);
    }

    private static void learnMoveForStep(Position from, Position to) {
        Direction direction = Direction.from(from, to);
        if (direction == null) {
            return;
        }

        MoveSnapshot snapshot = null;
        long now = System.currentTimeMillis();
        synchronized (MOVE_LOCK) {
            while (!pendingMoveActions.isEmpty()
                    && now - pendingMoveActions.peekFirst().capturedAtMs > 1800L) {
                pendingMoveActions.removeFirst();
            }
            if (!pendingMoveActions.isEmpty()) {
                snapshot = pendingMoveActions.removeFirst();
            }
            if (snapshot != null) {
                moveObservations.add(new MoveObservation(direction, from, to, snapshot));
            }
        }
    }

    private static void processPlayback(Object game, long now) throws Exception {
        ensureLabDirs();
        if (!Files.exists(PLAYBACK_CONTROL_FILE)) {
            Files.writeString(PLAYBACK_CONTROL_FILE, "stop" + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }

        String rawCommand = normalizeControl(Files.readString(PLAYBACK_CONTROL_FILE, StandardCharsets.UTF_8));
        if (!rawCommand.equals(lastPlaybackCommand)) {
            lastPlaybackCommand = rawCommand;
            if (rawCommand.startsWith("start") || rawCommand.startsWith("play") || rawCommand.startsWith("on")) {
                startPlayback(game);
            } else if (rawCommand.startsWith("stop") || rawCommand.startsWith("off")) {
                stopPlayback("PARADO PELO USUARIO");
            }
        }

        if (!playbackActive) {
            return;
        }
        if (recordingActive) {
            stopPlayback("ABORTADO - recorder foi ativado durante o playback");
            return;
        }
        if (readAutoAttackEnabled()) {
            stopPlayback("ABORTADO - deixe AutoAttack OFF neste teste da v5.1");
            return;
        }

        Position current = readPlayerPosition(game);
        if (current == null) {
            playbackStatus("PLAY - aguardando posicao do player");
            return;
        }

        if (playbackWaitingFor != null) {
            if (current.equals(playbackWaitingFor)) {
                playbackIndex++;
                playbackWaitingFor = null;
                playbackSentFrom = null;
                playbackRetries = 0;
                if (playbackIndex >= playbackRoute.size() - 1) {
                    finishPlayback();
                    return;
                }
            } else if (playbackSentFrom != null && !current.equals(playbackSentFrom)) {
                stopPlayback("DESVIO - esperado " + format(playbackWaitingFor)
                        + " mas player foi para " + format(current));
                return;
            } else if (now - playbackSentAtMs >= 1400L) {
                if (playbackRetries >= 1) {
                    stopPlayback("BLOQUEADO/TIMEOUT - nao chegou em " + format(playbackWaitingFor));
                    return;
                }
                Direction retryDirection = Direction.from(current, playbackWaitingFor);
                if (retryDirection == null) {
                    stopPlayback("ERRO - retry nao adjacente: " + format(current)
                            + " -> " + format(playbackWaitingFor));
                    return;
                }
                sendMoveAction(game, retryDirection, current, playbackWaitingFor);
                playbackSentAtMs = now;
                playbackRetries++;
                playbackStatus("RETRY " + retryDirection + " -> " + format(playbackWaitingFor));
                return;
            } else {
                return;
            }
        }

        if (playbackIndex >= playbackRoute.size() - 1) {
            finishPlayback();
            return;
        }

        Position expectedCurrent = playbackRoute.get(playbackIndex);
        if (!current.equals(expectedCurrent)) {
            stopPlayback("DESVIO - rota esperava " + format(expectedCurrent)
                    + " mas player esta em " + format(current));
            return;
        }

        Position next = playbackRoute.get(playbackIndex + 1);
        Direction direction = Direction.from(current, next);
        if (direction == null) {
            stopPlayback("ROTA INVALIDA - pontos nao adjacentes: " + format(current)
                    + " -> " + format(next));
            return;
        }
        if (!canSynthesize(direction)) {
            stopPlayback("SEM CALIBRACAO PARA " + direction
                    + " - grave uma rota nesta sessao passando por essa direcao antes do PLAY");
            return;
        }

        sendMoveAction(game, direction, current, next);
        playbackWaitingFor = next;
        playbackSentFrom = current;
        playbackSentAtMs = now;
        playbackRetries = 0;
        playbackStatus("PLAY " + (playbackIndex + 1) + "/" + (playbackRoute.size() - 1)
                + "  " + format(current) + " -> " + format(next) + "  " + direction);
    }

    private static void startPlayback(Object game) throws Exception {
        if (recordingActive) {
            playbackStatus("NAO INICIADO - pare a gravacao primeiro");
            return;
        }
        if (readAutoAttackEnabled()) {
            playbackStatus("NAO INICIADO - deixe AutoAttack OFF para o teste v5.1");
            return;
        }

        List<Position> route = loadRoute();
        if (route.size() < 2) {
            playbackStatus("NAO INICIADO - route_current.txt precisa ter pelo menos 2 pontos");
            return;
        }

        Position current = readPlayerPosition(game);
        if (current == null) {
            playbackStatus("NAO INICIADO - posicao do player indisponivel");
            return;
        }
        if (!current.equals(route.get(0))) {
            playbackStatus("NAO INICIADO - va manualmente ao inicio da rota "
                    + format(route.get(0)) + "; atual=" + format(current));
            return;
        }

        for (int i = 0; i < route.size() - 1; i++) {
            Direction d = Direction.from(route.get(i), route.get(i + 1));
            if (d == null) {
                playbackStatus("NAO INICIADO - rota invalida entre "
                        + format(route.get(i)) + " e " + format(route.get(i + 1)));
                return;
            }
            if (!canSynthesize(d)) {
                playbackStatus("NAO INICIADO - movimento " + d
                        + " ainda nao foi aprendido nesta sessao. Confira move_learning.txt (v5.2).");
                return;
            }
        }

        playbackRoute = route;
        playbackIndex = 0;
        playbackWaitingFor = null;
        playbackSentFrom = null;
        playbackRetries = 0;
        playbackActive = true;
        playbackStatus("INICIADO - pontos=" + route.size() + " inicio=" + format(route.get(0))
                + " fim=" + format(route.get(route.size() - 1)));
        status("v5.2 route playback START points=" + route.size());
    }

    private static void stopPlayback(String reason) {
        boolean wasActive = playbackActive;
        playbackActive = false;
        playbackWaitingFor = null;
        playbackSentFrom = null;
        playbackRetries = 0;
        playbackStatus(reason);
        if (wasActive) {
            status("route playback STOP: " + reason);
        }
    }

    private static void finishPlayback() {
        playbackActive = false;
        playbackWaitingFor = null;
        playbackSentFrom = null;
        playbackRetries = 0;
        Position end = playbackRoute.isEmpty() ? null : playbackRoute.get(playbackRoute.size() - 1);
        playbackStatus("CONCLUIDO - chegou ao fim da rota " + (end == null ? "" : format(end)));
        status("v5.2 route playback FINISHED");
    }

    private static List<Position> loadRoute() throws IOException {
        if (!Files.exists(CURRENT_ROUTE_FILE)) {
            return List.of();
        }
        List<Position> route = new ArrayList<>();
        for (String line : Files.readAllLines(CURRENT_ROUTE_FILE, StandardCharsets.UTF_8)) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            String[] parts = t.split(",");
            if (parts.length != 2) {
                continue;
            }
            try {
                route.add(new Position(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())));
            } catch (NumberFormatException ignored) {
            }
        }
        return route;
    }

    private static boolean canSynthesize(Direction direction) {
        synchronized (MOVE_LOCK) {
            return moveObservations.stream().anyMatch(o -> o.direction == direction)
                    && !moveObservations.isEmpty();
        }
    }

    private static void sendMoveAction(Object game,
                                       Direction direction,
                                       Position from,
                                       Position to) throws Exception {
        ClassLoader loader = game.getClass().getClassLoader();
        Class<?> actionBaseClass = Class.forName(ACTION_BASE_CLASS, false, loader);
        Class<?> moveClass = Class.forName(MOVE_ACTION_CLASS, false, loader);

        Method actionFactory = game.getClass().getMethod(ACTION_FACTORY_METHOD, Class.class);
        Object action = actionFactory.invoke(game, moveClass);
        if (action == null) {
            throw new IllegalStateException("action factory retornou null para REQUEST_PLAYER_MOVE");
        }

        int applied = applyLearnedMoveFields(action, direction, from, to);
        if (applied == 0) {
            throw new IllegalStateException("nenhum campo do REQUEST_PLAYER_MOVE pode ser sintetizado; veja move_learning.txt");
        }

        Method sendAction = game.getClass().getMethod(SEND_ACTION_METHOD, actionBaseClass);
        sendingPlaybackAction = true;
        try {
            sendAction.invoke(game, action);
        } finally {
            sendingPlaybackAction = false;
        }
    }

    private static int applyLearnedMoveFields(Object action,
                                              Direction direction,
                                              Position from,
                                              Position to) throws Exception {
        List<MoveObservation> observations;
        synchronized (MOVE_LOCK) {
            observations = new ArrayList<>(moveObservations);
        }
        if (observations.isEmpty()) {
            return 0;
        }

        Map<String, CapturedValue> latestForDirection = null;
        for (int i = observations.size() - 1; i >= 0; i--) {
            if (observations.get(i).direction == direction) {
                latestForDirection = observations.get(i).snapshot.values;
                break;
            }
        }
        if (latestForDirection == null) {
            return 0;
        }

        int applied = 0;
        for (Map.Entry<String, CapturedValue> entry : latestForDirection.entrySet()) {
            String key = entry.getKey();
            CapturedValue desired = deriveValueForField(key, direction, from, to, observations);
            if (desired == null) {
                continue;
            }
            int split = key.lastIndexOf('#');
            if (split <= 0) {
                continue;
            }
            String className = key.substring(0, split);
            String fieldName = key.substring(split + 1);
            Class<?> declaring = Class.forName(className, false, action.getClass().getClassLoader());
            Field field = declaring.getDeclaredField(fieldName);
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            Object value = desired.toRuntimeValue(field.getType(), action.getClass().getClassLoader());
            if (value == CapturedValue.UNSUPPORTED) {
                continue;
            }
            field.set(action, value);
            applied++;
        }
        return applied;
    }

    private static CapturedValue deriveValueForField(String key,
                                                      Direction direction,
                                                      Position from,
                                                      Position to,
                                                      List<MoveObservation> observations) {
        List<MoveObservation> withField = observations.stream()
                .filter(o -> o.snapshot.values.containsKey(key))
                .toList();
        if (withField.isEmpty()) {
            return null;
        }

        CapturedValue.Relation relation = CapturedValue.Relation.detect(key, withField);
        if (relation != null) {
            long v = switch (relation) {
                case FROM_X -> from.x;
                case FROM_Y -> from.y;
                case TO_X -> to.x;
                case TO_Y -> to.y;
                case DX -> to.x - from.x;
                case DY -> to.y - from.y;
            };
            return CapturedValue.numberLike(withField.get(0).snapshot.values.get(key), v);
        }

        List<CapturedValue> sameDirection = withField.stream()
                .filter(o -> o.direction == direction)
                .map(o -> o.snapshot.values.get(key))
                .toList();
        if (!sameDirection.isEmpty() && allEqual(sameDirection)) {
            return sameDirection.get(sameDirection.size() - 1);
        }

        List<CapturedValue> allValues = withField.stream()
                .map(o -> o.snapshot.values.get(key))
                .toList();
        if (allEqual(allValues)) {
            return allValues.get(allValues.size() - 1);
        }
        return null;
    }

    private static boolean allEqual(List<CapturedValue> values) {
        if (values.isEmpty()) {
            return false;
        }
        CapturedValue first = values.get(0);
        for (CapturedValue v : values) {
            if (!first.equals(v)) {
                return false;
            }
        }
        return true;
    }

    private static void writeMoveLearningFile() {
        try {
            ensureLabDirs();
            List<MoveObservation> observations;
            int pending;
            synchronized (MOVE_LOCK) {
                observations = new ArrayList<>(moveObservations);
                pending = pendingMoveActions.size();
            }
            StringBuilder out = new StringBuilder();
            out.append("MirageLab v5.2 - REQUEST_PLAYER_MOVE learning").append(System.lineSeparator());
            out.append("observations=").append(observations.size()).append(System.lineSeparator());
            out.append("pending_actions=").append(pending).append(System.lineSeparator());
            for (Direction d : Direction.values()) {
                long count = observations.stream().filter(o -> o.direction == d).count();
                out.append(d).append('=').append(count).append(System.lineSeparator());
            }
            out.append(System.lineSeparator());
            if (!observations.isEmpty()) {
                Map<String, CapturedValue> fields = observations.get(observations.size() - 1).snapshot.values;
                for (String key : fields.keySet()) {
                    CapturedValue.Relation rel = CapturedValue.Relation.detect(key, observations.stream()
                            .filter(o -> o.snapshot.values.containsKey(key)).toList());
                    out.append(key).append(" => ");
                    if (rel != null) {
                        out.append(rel);
                    } else {
                        for (Direction d : Direction.values()) {
                            List<CapturedValue> vals = observations.stream()
                                    .filter(o -> o.direction == d && o.snapshot.values.containsKey(key))
                                    .map(o -> o.snapshot.values.get(key)).toList();
                            if (!vals.isEmpty()) {
                                out.append(d).append(':')
                                        .append(allEqual(vals) ? vals.get(vals.size() - 1) : "<varia>")
                                        .append(' ');
                            }
                        }
                    }
                    out.append(System.lineSeparator());
                }
            }
            Files.writeString(MOVE_LEARNING_FILE, out.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Throwable ignored) {
        }
    }

    private static void playbackStatus(String message) {
        try {
            ensureLabDirs();
            Files.writeString(PLAYBACK_STATUS_FILE,
                    LocalDateTime.now() + "  " + message + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ignored) {
        }
    }

    private static String format(Position p) {
        return p == null ? "<null>" : p.x + "," + p.y;
    }

    private static Position readPosition(Object entity,
                                         Class<?> positionClass,
                                         Field xField,
                                         Field yField) throws Exception {
        if (entity == null) {
            return null;
        }
        Method getComponent = entity.getClass().getMethod("getComponent", Class.class);
        Object position = getComponent.invoke(entity, positionClass);
        if (position == null) {
            return null;
        }
        return new Position(xField.getInt(position), yField.getInt(position));
    }

    private static int readMaxRange() {
        try {
            if (!Files.exists(MAX_RANGE_FILE)) {
                ensureLabDirs();
                Files.writeString(MAX_RANGE_FILE, "4" + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                return 4;
            }
            String raw = Files.readString(MAX_RANGE_FILE, StandardCharsets.UTF_8).trim();
            int value = Integer.parseInt(raw);
            return Math.max(1, Math.min(value, 30));
        } catch (Exception ignored) {
            return 4;
        }
    }


    private static boolean readAutoAttackEnabled() {
        try {
            if (!Files.exists(AUTO_ATTACK_FILE)) {
                ensureLabDirs();
                Files.writeString(AUTO_ATTACK_FILE, "off" + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                return false;
            }
            String raw = Files.readString(AUTO_ATTACK_FILE, StandardCharsets.UTF_8)
                    .replace("\u0000", "").trim().toLowerCase();
            return raw.equals("on") || raw.equals("1") || raw.equals("true")
                    || raw.equals("sim") || raw.equals("yes");
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void processAutoAttack(Object game,
                                          ClassLoader loader,
                                          Map<Integer, String> monsters,
                                          List<MonsterInfo> spatial,
                                          int maxRange) {
        try {
            if (currentAutoTargetId != null) {
                String stillThere = monsters.get(currentAutoTargetId);
                if (stillThere != null) {
                    currentAutoTargetName = stillThere;
                    autoStatus("ATACANDO - id=" + currentAutoTargetId + ", name=" + currentAutoTargetName
                            + " (mantendo o mesmo alvo enquanto existir)");
                    return;
                }

                autoEvent("ALVO FINALIZADO/SUMIU - id=" + currentAutoTargetId
                        + ", name=" + currentAutoTargetName);
                currentAutoTargetId = null;
                currentAutoTargetName = null;
            }

            MonsterInfo nearest = spatial.stream()
                    .filter(m -> m.chebyshev <= maxRange)
                    .findFirst()
                    .orElse(null);

            if (nearest == null) {
                autoStatus("AGUARDANDO - nenhum NPC_MONSTER dentro de " + maxRange + " tiles");
                return;
            }

            sendAggressiveTarget(game, loader, nearest.id);
            currentAutoTargetId = nearest.id;
            currentAutoTargetName = nearest.name;

            String detail = "NOVO ALVO - id=" + nearest.id
                    + ", name=" + nearest.name
                    + ", dx=" + nearest.dx
                    + ", dy=" + nearest.dy
                    + ", manhattan=" + nearest.manhattan
                    + ", range=" + nearest.chebyshev;
            autoStatus(detail);
            autoEvent(detail);
        } catch (Throwable t) {
            autoStatus("ERRO NO AUTOATTACK: " + t);
            autoEvent("ERRO NO AUTOATTACK: " + t);
            currentAutoTargetId = null;
            currentAutoTargetName = null;
        }
    }

    private static void autoStatus(String message) {
        try {
            ensureLabDirs();
            String text = LocalDateTime.now() + "  " + message + System.lineSeparator();
            Files.writeString(
                    AUTO_STATUS_FILE,
                    text,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ignored) {
        }
    }

    private static void autoEvent(String message) {
        if (message.equals(lastAutoEvent)) {
            return;
        }
        lastAutoEvent = message;
        status("autoattack: " + message);
    }

    private static void processAttackCommand(Object game,
                                             ClassLoader loader,
                                             Map<Integer, String> monsters,
                                             List<MonsterInfo> spatial,
                                             int maxRange) throws Exception {
        if (!Files.exists(ATTACK_TARGET_FILE)) {
            return;
        }

        String command = Files.readString(ATTACK_TARGET_FILE, StandardCharsets.UTF_8).trim();
        if (command.isEmpty()) {
            command = "off";
        }

        if (command.equals(lastAttackCommand)) {
            return;
        }
        lastAttackCommand = command;

        if ("off".equalsIgnoreCase(command)) {
            attackStatus("OFF - nenhum comando de ataque pendente");
            return;
        }

        int targetId;
        String monsterName;
        String extra = "";

        if ("nearest".equalsIgnoreCase(command)) {
            MonsterInfo nearest = spatial.stream()
                    .filter(m -> m.chebyshev <= maxRange)
                    .findFirst()
                    .orElse(null);
            if (nearest == null) {
                attackStatus("NAO ENCONTRADO - nenhum NPC_MONSTER dentro de " + maxRange + " tiles");
                return;
            }
            targetId = nearest.id;
            monsterName = nearest.name;
            extra = ", dx=" + nearest.dx + ", dy=" + nearest.dy
                    + ", manhattan=" + nearest.manhattan
                    + ", range=" + nearest.chebyshev;
        } else {
            try {
                targetId = Integer.parseInt(command);
            } catch (NumberFormatException e) {
                attackStatus("IGNORADO - use ID inteiro, 'nearest' ou 'off': " + command);
                return;
            }
            monsterName = monsters.get(targetId);
            if (monsterName == null) {
                attackStatus("NAO ENCONTRADO - id=" + targetId + " nao esta entre os NPC_MONSTER atuais");
                return;
            }
        }

        sendAggressiveTarget(game, loader, targetId);
        attackStatus("ENVIADO - SetTarget(id=" + targetId
                + ", name=" + monsterName + ", mode=AGGRESSIVE" + extra + ")");
        status("attack test sent: id=" + targetId + " name=" + monsterName + extra);
    }

    private static void sendAggressiveTarget(Object game,
                                             ClassLoader loader,
                                             int targetId) throws Exception {
        Class<?> actionBaseClass = Class.forName(ACTION_BASE_CLASS, false, loader);
        Class<?> setTargetClass = Class.forName(SET_TARGET_CLASS, false, loader);
        Class<?> modeClass = Class.forName(SET_TARGET_MODE_CLASS, false, loader);

        Method actionFactory = game.getClass().getMethod(ACTION_FACTORY_METHOD, Class.class);
        Object action = actionFactory.invoke(game, setTargetClass);

        Field aggressiveField = modeClass.getField(AGGRESSIVE_FIELD);
        Object aggressive = aggressiveField.get(null);

        Method configure = setTargetClass.getMethod(
                SET_TARGET_CONFIGURE_METHOD, int.class, modeClass);
        configure.invoke(action, targetId, aggressive);

        Method sendAction = game.getClass().getMethod(SEND_ACTION_METHOD, actionBaseClass);
        sendAction.invoke(game, action);
    }

    private static void ensureLabDirs() throws IOException {
        Files.createDirectories(LAB_DIR);
        Files.createDirectories(STATE_DIR);
        Files.createDirectories(RUNTIME_DIR);
        Files.createDirectories(ROUTES_DIR);
    }

    static void status(String message) {
        try {
            ensureLabDirs();
            String line = LocalDateTime.now() + "  " + message + System.lineSeparator();
            Files.writeString(
                    STATUS_FILE,
                    line,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    private static void attackStatus(String message) {
        try {
            ensureLabDirs();
            String text = LocalDateTime.now() + "  " + message + System.lineSeparator();
            Files.writeString(
                    ATTACK_STATUS_FILE,
                    text,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ignored) {
        }
    }

    private static void writeSnapshot(Map<Integer, String> monsters) throws IOException {
        ensureLabDirs();
        StringBuilder out = new StringBuilder();
        out.append("Atualizado: ").append(LocalDateTime.now()).append(System.lineSeparator());
        out.append("NPC_MONSTER conhecidos: ").append(monsters.size()).append(System.lineSeparator());
        out.append(System.lineSeparator());
        for (Map.Entry<Integer, String> entry : monsters.entrySet()) {
            out.append("MONSTER  id=")
                    .append(entry.getKey())
                    .append("  name=")
                    .append(entry.getValue())
                    .append(System.lineSeparator());
        }
        Files.writeString(
                MONSTERS_FILE,
                out.toString(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void writeSpatial(Position player,
                                     List<MonsterInfo> monsters,
                                     int maxRange,
                                     boolean autoEnabled) throws IOException {
        ensureLabDirs();
        StringBuilder out = new StringBuilder();
        out.append("Atualizado: ").append(LocalDateTime.now()).append(System.lineSeparator());
        if (player == null) {
            out.append("PLAYER position: indisponivel").append(System.lineSeparator());
        } else {
            out.append("PLAYER x=").append(player.x).append(" y=").append(player.y).append(System.lineSeparator());
        }
        out.append("max_range=").append(maxRange).append(" (regra: max(|dx|,|dy|) <= range)")
                .append(System.lineSeparator());
        out.append("auto_attack=").append(autoEnabled ? "ON" : "OFF");
        if (currentAutoTargetId != null) {
            out.append("  current_target=").append(currentAutoTargetId)
                    .append(" ").append(currentAutoTargetName == null ? "" : currentAutoTargetName);
        }
        out.append(System.lineSeparator());
        out.append("Monstros com posicao: ").append(monsters.size()).append(System.lineSeparator());
        out.append(System.lineSeparator());

        int rank = 1;
        for (MonsterInfo m : monsters) {
            out.append(rank++)
                    .append(". id=").append(m.id)
                    .append(" name=").append(m.name)
                    .append(" x=").append(m.x)
                    .append(" y=").append(m.y)
                    .append(" dx=").append(m.dx)
                    .append(" dy=").append(m.dy)
                    .append(" manhattan=").append(m.manhattan)
                    .append(" range=").append(m.chebyshev)
                    .append(m.chebyshev <= maxRange ? "  IN_RANGE" : "")
                    .append(System.lineSeparator());
        }

        Files.writeString(
                SPATIAL_FILE,
                out.toString(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
    }

    private record Position(int x, int y) {}

    private record MonsterInfo(int id,
                               String name,
                               int x,
                               int y,
                               int dx,
                               int dy,
                               int manhattan,
                               int chebyshev) {}

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

    private record MoveSnapshot(long sequence,
                                long capturedAtMs,
                                Map<String, CapturedValue> values) {}

    private record MoveObservation(Direction direction,
                                   Position from,
                                   Position to,
                                   MoveSnapshot snapshot) {}

    private static final class CapturedValue {
        static final Object UNSUPPORTED = new Object();

        enum Kind { NULL, BOOLEAN, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, CHAR, STRING, ENUM }
        enum Relation {
            FROM_X, FROM_Y, TO_X, TO_Y, DX, DY;

            static Relation detect(String key, List<MoveObservation> obs) {
                if (obs.size() < 2) return null;
                Relation[] relations = values();
                for (Relation r : relations) {
                    boolean ok = true;
                    for (MoveObservation o : obs) {
                        CapturedValue cv = o.snapshot.values.get(key);
                        if (cv == null || !cv.isIntegralNumber()) {
                            ok = false;
                            break;
                        }
                        long expected = switch (r) {
                            case FROM_X -> o.from.x;
                            case FROM_Y -> o.from.y;
                            case TO_X -> o.to.x;
                            case TO_Y -> o.to.y;
                            case DX -> o.to.x - o.from.x;
                            case DY -> o.to.y - o.from.y;
                        };
                        if (cv.asLong() != expected) {
                            ok = false;
                            break;
                        }
                    }
                    if (ok) return r;
                }
                return null;
            }
        }

        final Kind kind;
        final Object value;
        final String enumClass;
        final String enumName;

        private CapturedValue(Kind kind, Object value, String enumClass, String enumName) {
            this.kind = kind;
            this.value = value;
            this.enumClass = enumClass;
            this.enumName = enumName;
        }

        static CapturedValue of(Object value) {
            if (value == null) return new CapturedValue(Kind.NULL, null, null, null);
            if (value instanceof Boolean v) return new CapturedValue(Kind.BOOLEAN, v, null, null);
            if (value instanceof Byte v) return new CapturedValue(Kind.BYTE, v, null, null);
            if (value instanceof Short v) return new CapturedValue(Kind.SHORT, v, null, null);
            if (value instanceof Integer v) return new CapturedValue(Kind.INT, v, null, null);
            if (value instanceof Long v) return new CapturedValue(Kind.LONG, v, null, null);
            if (value instanceof Float v) return new CapturedValue(Kind.FLOAT, v, null, null);
            if (value instanceof Double v) return new CapturedValue(Kind.DOUBLE, v, null, null);
            if (value instanceof Character v) return new CapturedValue(Kind.CHAR, v, null, null);
            if (value instanceof String v) return new CapturedValue(Kind.STRING, v, null, null);
            if (value instanceof Enum<?> e) {
                return new CapturedValue(Kind.ENUM, null, e.getDeclaringClass().getName(), e.name());
            }
            return null;
        }

        boolean isIntegralNumber() {
            return kind == Kind.BYTE || kind == Kind.SHORT || kind == Kind.INT || kind == Kind.LONG;
        }

        long asLong() {
            return ((Number) value).longValue();
        }

        static CapturedValue numberLike(CapturedValue base, long value) {
            return switch (base.kind) {
                case BYTE -> new CapturedValue(Kind.BYTE, (byte) value, null, null);
                case SHORT -> new CapturedValue(Kind.SHORT, (short) value, null, null);
                case INT -> new CapturedValue(Kind.INT, (int) value, null, null);
                case LONG -> new CapturedValue(Kind.LONG, value, null, null);
                default -> null;
            };
        }

        Object toRuntimeValue(Class<?> targetType, ClassLoader loader) throws Exception {
            if (kind == Kind.NULL) return null;
            if (kind == Kind.ENUM) {
                Class<?> enumType = Class.forName(enumClass, false, loader);
                @SuppressWarnings({"rawtypes", "unchecked"})
                Object e = Enum.valueOf((Class<? extends Enum>) enumType, enumName);
                return e;
            }
            if (targetType == boolean.class || targetType == Boolean.class) return value;
            if (targetType == byte.class || targetType == Byte.class) return ((Number) value).byteValue();
            if (targetType == short.class || targetType == Short.class) return ((Number) value).shortValue();
            if (targetType == int.class || targetType == Integer.class) return ((Number) value).intValue();
            if (targetType == long.class || targetType == Long.class) return ((Number) value).longValue();
            if (targetType == float.class || targetType == Float.class) return ((Number) value).floatValue();
            if (targetType == double.class || targetType == Double.class) return ((Number) value).doubleValue();
            if (targetType == char.class || targetType == Character.class) return value;
            if (targetType == String.class) return value;
            return UNSUPPORTED;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof CapturedValue other)) return false;
            return kind == other.kind
                    && java.util.Objects.equals(value, other.value)
                    && java.util.Objects.equals(enumClass, other.enumClass)
                    && java.util.Objects.equals(enumName, other.enumName);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(kind, value, enumClass, enumName);
        }

        @Override
        public String toString() {
            if (kind == Kind.ENUM) return enumClass + "." + enumName;
            return String.valueOf(value);
        }
    }
}
