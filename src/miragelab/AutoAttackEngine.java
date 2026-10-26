package miragelab;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/** v9.3 Target Intelligence: fast candidate scan, claim hysteresis, HP confidence and deterministic retargeting. */
final class AutoAttackEngine {
    private static final String WORLD_GETTER = "P17VtM6bkJVXJB65ftLh7dMon";
    private static final String PLAYER_ENTITY_GETTER = "eACqBMENic3YHQLgkQTnYXFlcXmJ";
    private static final String IDENTITY_CLASS = "uk.co.harveydogs.mirage.shared.yY8x1yOsuoav.D4yZmsobaWAL";
    private static final String POSITION_CLASS = "uk.co.harveydogs.mirage.shared.yY8x1yOsuoav.CzFlqirRObv";
    private static final String TYPE_FIELD = "eACqBMENic3YHQLgkQTnYXFlcXmJ";
    private static final String ID_FIELD = "FIeR9ZxfSOyX";
    private static final String NAME_FIELD = "WVfgbdbcCRRmDq1CfPzsI1qtv";
    private static final String X_FIELD = "eACqBMENic3YHQLgkQTnYXFlcXmJ";
    private static final String Y_FIELD = "FIeR9ZxfSOyX";
    private static final String TARGET_COMPONENT_CLASS = "uk.co.harveydogs.mirage.client.GEcHnxVxWL24.yY8x1yOsuoav.yY8x1yOsuoav.hvodiLOEjqktduzhy0jtsVYZUcAU";
    private static final String TARGET_COMPONENT_GETTER = "FIeR9ZxfSOyX";
    private static final String ACTION_BASE_CLASS = "uk.co.harveydogs.mirage.shared.ppGE4fatBnKfcPyX4Kmtne6Lgsjv.NT6CLfh1EFZKAgt882tQ6RNRZLmx";
    private static final String SET_TARGET_CLASS = "uk.co.harveydogs.mirage.shared.ppGE4fatBnKfcPyX4Kmtne6Lgsjv.yY8x1yOsuoav.yY8x1yOsuoav.BqDbTihL38.NT6CLfh1EFZKAgt882tQ6RNRZLmx";
    private static final String SET_TARGET_MODE_CLASS = SET_TARGET_CLASS + "$NT6CLfh1EFZKAgt882tQ6RNRZLmx";
    private static final String ACTION_FACTORY_METHOD = "eACqBMENic3YHQLgkQTnYXFlcXmJ";
    private static final String SEND_ACTION_METHOD = "eACqBMENic3YHQLgkQTnYXFlcXmJ";
    private static final String SET_TARGET_CONFIGURE_METHOD = "eACqBMENic3YHQLgkQTnYXFlcXmJ";
    private static final String SET_TARGET_ID_FIELD = "eACqBMENic3YHQLgkQTnYXFlcXmJ";
    private static final String SET_TARGET_MODE_FIELD = "FIeR9ZxfSOyX";

    private static long nextScanAt;
    private static long nextRetargetAt;
    private static long lastTargetAcquiredAt;
    private static long lastTargetSwitchedAt;
    private static long manualLockUntil;
    private static Integer ownTargetId;
    private static long lastCurrentVisibleAt;
    private static Monster lastCurrentObserved;
    private static volatile String summary = "Auto Attack: aguardando";
    private static volatile String rankingSummary = "ranking: aguardando";
    private static int lastEligibleCount;
    private static int lastVisibleMonsterCount;
    private static int lastInRangeCount;
    private static int lastClaimedCount;
    private static int lastPendingClaimCount;
    private static int lastPartyClaimCount;
    private static int lastSelfDefenseOverrides;
    private static int lastUnknownHpCount;
    private static int lastAbsoluteHpCount;
    private static int lastPercentHpCount;
    private static long scanCounter;
    private static int lastNearbyCount, lastWorldEntityCount;
    private static final Deque<String> decisionHistory = new ArrayDeque<>();
    private static synchronized void decision(String entry) {
        SessionTelemetry.accept(entry);
        decisionHistory.addLast(new java.text.SimpleDateFormat("HH:mm:ss.SSS").format(new Date()) + " " + entry);
        while (decisionHistory.size() > 60) decisionHistory.removeFirst();
    }
    static synchronized String decisionHistory(){return String.join("\n", decisionHistory);}

    private static final Map<Integer, Long> contestedUntil = new HashMap<>();
    private static final Map<Integer, ClaimState> remoteClaims = new HashMap<>();

    // Candidate confirmation avoids oscillation while keeping the decision loop very fast.
    private static Integer pendingBestId;
    private static long pendingBestSince;

    // Manual target actions are observed at the Game send boundary. This is more reliable than
    // guessing from delayed target telemetry, especially with 150-250ms network latency.
    private static final Object MANUAL_LOCK = new Object();
    private static long manualSequence;
    private static long processedManualSequence;
    private static ManualCommand pendingManualCommand;
    private static final ThreadLocal<Boolean> INTERNAL_TARGET_SEND = ThreadLocal.withInitial(() -> Boolean.FALSE);

    // Reflection cache.
    private static ClassLoader cachedLoader;
    private static Class<?> identityClass, posClass, targetClass;
    private static Field typeField, idField, nameField, xField, yField;
    private static Method gameWorldGetter, worldPlayerGetter, worldEntitiesGetter, entityGetComponent, targetGetter;

    private AutoAttackEngine() {}

    static void tick(Object game, long now) {
        try {
            HelperConfig.Config c = HelperConfig.get(now);
            if (now < nextScanAt) return;
            nextScanAt = now + c.attackScanIntervalMs;

            Scan s = scan(game, now, c);
            scanCounter++;
            lastNearbyCount = s == null ? 0 : s.monsters.size();
            if (s == null || s.player == null) { summary = "Auto Attack: aguardando player"; return; }
            lastVisibleMonsterCount = s.monsters.size();
            updateRemoteClaims(s, c, now);
            updateScanStats(s, c, now);
            applyObservedManualCommand(s, c, now);

            if (CaveBotEngine.isActive()) {
                ownTargetId = null;
                pendingBestId = null;
                summary = statusPrefix(c) + "integrado ao CaveBot";
                return;
            }
            if (!c.autoAttackEnabled) {
                if (ownTargetId != null) { try { sendNone(game, ownTargetId); } catch (Throwable ignored) {} ownTargetId = null; }
                pendingBestId = null;
                summary = statusPrefix(c) + "OFF";
                return;
            }

            Monster current = ownTargetId == null ? null : s.monsters.get(ownTargetId);
            if (current != null) {
                lastCurrentVisibleAt = now;
                lastCurrentObserved = current;
            } else if (ownTargetId != null && lastCurrentObserved != null
                    && lastCurrentObserved.id == ownTargetId
                    && CombatFocusPolicy.keepMissing(now, lastCurrentVisibleAt,
                           c.attackTargetMissingGraceMs, s.localTargetId == null || Objects.equals(s.localTargetId, ownTargetId))) {
                // Preserve the selected entity across brief world snapshot gaps; do not chase a ghost.
                summary = statusPrefix(c) + "alvo temporariamente ausente | FOCO";
                return;
            }
            if (current != null && now >= manualLockUntil && isClaimBlocked(current, c, now)) {
                contestedUntil.put(current.id, now + Math.max(300L, c.attackClaimReleaseMs));
                try { sendNone(game, current.id); } catch (Throwable ignored) { }
                decision("ANTI-KS release id="+current.id+" name="+current.name);
                ownTargetId = null; current = null;
            }
            if (current != null && ignoredByName(current.name, c.attackIgnoreNames)) {
                try { sendNone(game, current.id); } catch (Throwable ignored) { }
                decision("FILTER release id=" + current.id + " name=" + current.name);
                ownTargetId = null; current = null; pendingBestId = null;
            }
            if (current != null && (current.distance > c.attackRange || current.deadLike())) {
                ownTargetId = null; current = null; pendingBestId = null;
            }

            List<Monster> ranked = rankedEligible(s, c, now);
            rankingSummary = buildRankingSummary(ranked, c);

            if (current != null) {
                if (now < manualLockUntil) {
                    summary = statusPrefix(c) + describe(current) + " | manual lock";
                    return;
                }
                if (c.attackRetargetEnabled && now >= nextRetargetAt) {
                    nextRetargetAt = now + c.attackRetargetIntervalMs;
                    Monster best = ranked.isEmpty() ? null : ranked.get(0);
                    if (best != null && best.id != current.id && shouldSwitchAt(current, best, c, now, lastTargetAcquiredAt, lastTargetSwitchedAt)) {
                        if (switchConfirmed(current, best, c, now)) {
                            sendAggressive(game, best.id);
                            ownTargetId = best.id;
                            lastTargetAcquiredAt = now; lastTargetSwitchedAt = now;
                            decision("RETARGET "+current.id+" "+hpText(current)+" -> "+best.id+" "+hpText(best)+" mode="+c.attackPriority+" scanned="+s.monsters.size());
                            pendingBestId = null;
                            summary = statusPrefix(c) + "SWITCH " + current.name + " -> " + describe(best);
                            return;
                        }
                    } else {
                        pendingBestId = null;
                    }
                }
                if (c.attackChaseEnabled) chaseCurrent(game, s, current, c, now);
                summary = statusPrefix(c) + describe(current) + " | " + c.attackPriority.label()
                        + (finishProtected(current, c) ? " | FINISH" : "");
                return;
            }

            if (now < manualLockUntil) { summary = statusPrefix(c) + "pausa curta após ação manual"; return; }
            Monster chosen = ranked.isEmpty() ? null : ranked.get(0);
            if (chosen == null) {
                summary = statusPrefix(c) + "aguardando alvo | " + VitalSensorSupport.monsterHpStatus();
                return;
            }
            sendAggressive(game, chosen.id);
            ownTargetId = chosen.id;
            lastTargetAcquiredAt = now;
            decision("TARGET "+chosen.id+" "+chosen.name+" "+hpText(chosen)+" d="+chosen.distance+" mode="+c.attackPriority+(chosen.percentKnown||chosen.absoluteKnown?" MEASURED":" HP_UNKNOWN_FALLBACK"));
            nextRetargetAt = now + c.attackRetargetIntervalMs;
            pendingBestId = null;
            if (c.attackChaseEnabled) chaseCurrent(game, s, chosen, c, now);
            summary = statusPrefix(c) + "alvo=" + describe(chosen) + " | " + c.attackPriority.label();
        } catch (Throwable t) {
            summary = "Auto Attack: ERRO " + t.getClass().getSimpleName();
        }
    }

    static String summary() { return summary; }
    static String rankingSummary() { return rankingSummary; }
    /** Explicitly distinguish measured HP ranking from its distance fallback. */
    static String hpGuidance(HelperConfig.Config c){
        if(c==null)return "HP: aguardando";
        if(c.attackPriority!=HelperConfig.AttackPriority.LOWEST_HP_ABSOLUTE
                &&c.attackPriority!=HelperConfig.AttackPriority.LOWEST_HP_PERCENT)
            return "Prioridade por distância (HP não é usado)";
        if(lastInRangeCount==0)return "HP: sem monstros ao alcance; aguardando dados internos";
        int known=c.attackPriority==HelperConfig.AttackPriority.LOWEST_HP_ABSOLUTE?lastAbsoluteHpCount:lastPercentHpCount;
        if(known==0)return "HP direto indisponivel: FOCO no alvo atual; distancia so para 1a escolha | " + GameDirectVitals.monsterStatus();
        if(known<lastInRangeCount)return "HP PARCIAL: "+known+"/"+lastInRangeCount+" no alcance com leitura";
        return "HP OK: "+known+"/"+lastInRangeCount+" candidatos com leitura";
    }

    static boolean hasActiveTarget() { return ownTargetId != null; }
    static Integer activeTargetId() { return ownTargetId; }
    static int lastEligibleCount() { return lastEligibleCount; }
    static long scanCounter() { return scanCounter; }
    static int lastNearbyCount() { return lastNearbyCount; }

    /** Called from LabHook for every SetTarget sent by the game. */
    static void observeSetTargetAction(Object action, long now) {
        if (action == null || !SET_TARGET_CLASS.equals(action.getClass().getName())) return;
        if (Boolean.TRUE.equals(INTERNAL_TARGET_SEND.get())) return;
        try {
            Field id = action.getClass().getDeclaredField(SET_TARGET_ID_FIELD); id.setAccessible(true);
            Field mode = action.getClass().getDeclaredField(SET_TARGET_MODE_FIELD); mode.setAccessible(true);
            int targetId = id.getInt(action);
            Object m = mode.get(action);
            int ordinal = m instanceof Enum<?> e ? e.ordinal() : -1;
            synchronized (MANUAL_LOCK) {
                pendingManualCommand = new ManualCommand(++manualSequence, targetId, ordinal, now);
            }
        } catch (Throwable ignored) { }
    }

    private static void applyObservedManualCommand(Scan s, HelperConfig.Config c, long now) {
        ManualCommand cmd;
        synchronized (MANUAL_LOCK) {
            cmd = pendingManualCommand;
            if (cmd == null || cmd.sequence <= processedManualSequence) return;
            processedManualSequence = cmd.sequence;
        }
        // ordinal 1 = AGGRESSIVE, ordinal 2 = NONE (confirmed from the client enum).
        if (cmd.modeOrdinal == 1) {
            Monster m = s.monsters.get(cmd.targetId);
            // Explicit user choice temporarily overrides automatic Anti-KS/ranking.
            if (m != null && m.distance <= c.attackRange && !m.deadLike()) {
                ownTargetId = m.id;
                lastTargetAcquiredAt = now; lastTargetSwitchedAt = now;
                decision("MANUAL target id="+m.id+" "+m.name);
                manualLockUntil = now + c.manualTargetLockMs;
                pendingBestId = null;
            }
        } else if (cmd.modeOrdinal == 2) {
            ownTargetId = null;
            pendingBestId = null;
            manualLockUntil = now + Math.min(300L, c.manualTargetLockMs);
        }
    }

    private static String statusPrefix(HelperConfig.Config c) {
        return "Auto Attack [scan=" + c.attackScanIntervalMs + "ms raio="+c.attackScanRadius+" in=" + lastInRangeCount
                + " elig=" + lastEligibleCount + " claim=" + lastClaimedCount + "+" + lastPendingClaimCount + " party=" + lastPartyClaimCount
                + " hp=" + lastAbsoluteHpCount + "abs/" + lastPercentHpCount + "% ?=" + lastUnknownHpCount
                + " world=" + lastVisibleMonsterCount + "] " + (c.attackFocusProtection ? "FOCO ": "");
    }

    private static void updateScanStats(Scan s, HelperConfig.Config c, long now) {
        lastEligibleCount=lastInRangeCount=lastClaimedCount=lastPendingClaimCount=lastPartyClaimCount=lastSelfDefenseOverrides=lastUnknownHpCount=lastAbsoluteHpCount=lastPercentHpCount=0;
        lastPartyClaimCount = s.partyClaimed.size();
        for(Monster m:s.monsters.values()){
            if(m.distance > c.attackRange || m.deadLike() || ignoredByName(m.name, c.attackIgnoreNames)) continue;
            lastInRangeCount++;
            ClaimState cs = remoteClaims.get(m.id);
            if (cs != null && cs.rawSeenThisScan && !cs.confirmed(now)) lastPendingClaimCount++;
            if (isClaimBlocked(m,c,now)) { lastClaimedCount++; continue; }
            if (cs != null && cs.confirmed(now) && m.hostile && c.attackSelfDefenseOverride) lastSelfDefenseOverrides++;
            if (isContested(m.id, now)) continue;
            lastEligibleCount++;
            if(m.absoluteKnown)lastAbsoluteHpCount++;
            if(m.percentKnown)lastPercentHpCount++;
            if(!m.absoluteKnown && !m.percentKnown) lastUnknownHpCount++;
        }
    }

    private static boolean isEligible(Monster m, Scan s, HelperConfig.Config c, long now) {
        return m != null && m.distance <= c.attackRange && !m.deadLike()
                && !isContested(m.id, now) && !isClaimBlocked(m, c, now);
    }

    private static List<Monster> rankedEligible(Scan s, HelperConfig.Config c, long now) {
        List<Monster> eligible = new ArrayList<>();
        for (Monster m : s.monsters.values()) {
            if (m.distance > c.attackRange || m.deadLike() || isContested(m.id, now) || ignoredByName(m.name, c.attackIgnoreNames)) continue;
            if (isClaimBlocked(m, c, now)) continue;
            eligible.add(m);
        }
        eligible.sort(monsterComparator(c));
        return eligible;
    }

    private static void updateRemoteClaims(Scan s, HelperConfig.Config c, long now) {
        for (ClaimState cs : remoteClaims.values()) cs.rawSeenThisScan = false;
        for (Integer id : s.claimed) {
            ClaimState cs = remoteClaims.computeIfAbsent(id, z -> new ClaimState());
            if (cs.firstSeenAt == 0L || now - cs.lastSeenAt > c.attackClaimReleaseMs) cs.firstSeenAt = now;
            cs.lastSeenAt = now;
            cs.rawSeenThisScan = true;
            if (now - cs.firstSeenAt >= Math.max(0, c.attackClaimConfirmMs)) {
                cs.confirmedUntil = now + Math.max(100, c.attackClaimReleaseMs);
            }
        }
        remoteClaims.entrySet().removeIf(e -> now - e.getValue().lastSeenAt > Math.max(500L, c.attackClaimReleaseMs * 2L)
                && e.getValue().confirmedUntil <= now);
    }

    private static boolean isClaimBlocked(Monster m, HelperConfig.Config c, long now) {
        if (m == null || !c.attackAntiKsEnabled) return false;
        ClaimState cs = remoteClaims.get(m.id);
        if (cs == null || !cs.confirmed(now)) return false;
        // A monster actively targeting us is self-defense, not a KS opportunity.
        return !(c.attackSelfDefenseOverride && m.hostile);
    }

    private static Comparator<Monster> monsterComparator(HelperConfig.Config c) {
        Comparator<Monster> hostileTie = Comparator.comparingInt((Monster m) -> c.preferAggressor && m.hostile ? 0 : 1);
        Comparator<Monster> hpPct = Comparator
                .comparingInt((Monster m) -> m.percentKnown ? 0 : 1)
                .thenComparingDouble(m -> m.percentKnown ? hpRankPercent(m) : 101d)
                .thenComparingDouble(m -> m.percentKnown ? m.hpPercent : 101d)
                .thenComparingLong(m -> m.hpAgeMs);
        Comparator<Monster> hpAbs = Comparator
                .comparingInt((Monster m) -> m.absoluteKnown ? 0 : 1)
                .thenComparingDouble(m -> m.absoluteKnown ? m.hpCurrent : Double.POSITIVE_INFINITY)
                .thenComparingLong(m -> m.hpAgeMs);
        return switch (c.attackPriority) {
            case LOWEST_HP_ABSOLUTE -> hpAbs.thenComparing(hostileTie).thenComparing(hpPct).thenComparingInt(m -> m.distance).thenComparingInt(m -> m.id);
            case LOWEST_HP_PERCENT -> hpPct.thenComparing(hostileTie).thenComparing(hpAbs).thenComparingInt(m -> m.distance).thenComparingInt(m -> m.id);
            case FARTHEST -> Comparator.comparingInt((Monster m) -> m.distance).reversed()
                    .thenComparing(hostileTie).thenComparing(hpPct).thenComparingInt(m -> m.id);
            case NEAREST -> Comparator.comparingInt((Monster m) -> m.distance)
                    .thenComparing(hostileTie).thenComparing(hpPct).thenComparingInt(m -> m.id);
        };
    }

    /** Small confidence penalty: stale HP remains useful, but a 1-2% stale edge does not thrash a fresh target. */
    private static double hpRankPercent(Monster m) {
        if (m == null || !m.percentKnown || m.hpPercent == null) return 101d;
        double penalty = m.hpAgeMs <= 350L ? 0d : (m.hpAgeMs <= 1500L ? 0.75d : Math.min(4d, 0.75d + (m.hpAgeMs - 1500L) / 1500d));
        return Math.min(100d, m.hpPercent + penalty);
    }

    private static boolean shouldSwitch(Monster current, Monster candidate, HelperConfig.Config c) {
        if (candidate == null || current == null || candidate.id == current.id) return false;
        boolean pctMode = c.attackPriority == HelperConfig.AttackPriority.LOWEST_HP_PERCENT;
        boolean absMode = c.attackPriority == HelperConfig.AttackPriority.LOWEST_HP_ABSOLUTE;
        if (!CombatFocusPolicy.permitSwitch(c.attackFocusProtection,pctMode||absMode,
                pctMode ? current.percentKnown : current.absoluteKnown,
                pctMode ? candidate.percentKnown : candidate.absoluteKnown,
                pctMode ? current.hpPercent : current.hpCurrent,
                pctMode ? candidate.hpPercent : candidate.hpCurrent,
                current.hpPercent,current.hpAgeMs,candidate.hpAgeMs,c.attackFocusMinGain,
                c.attackFinishThreshold)) return false;
        if (isHpPriority(c.attackPriority) && finishProtectedAgainst(current, candidate, c)) return false;
        return switch (c.attackPriority) {
            case NEAREST -> {
                int gain = Math.max(1, c.attackSwitchDistanceGain);
                if (candidate.distance + gain <= current.distance) yield true;
                yield candidate.distance == current.distance && c.preferAggressor && candidate.hostile && !current.hostile;
            }
            case FARTHEST -> {
                int gain = Math.max(1, c.attackSwitchDistanceGain);
                if (candidate.distance >= current.distance + gain) yield true;
                yield candidate.distance == current.distance && c.preferAggressor && candidate.hostile && !current.hostile;
            }
            case LOWEST_HP_ABSOLUTE -> hpSwitch(current.hpCurrent,current.absoluteKnown,candidate.hpCurrent,candidate.absoluteKnown,current,candidate,c);
            case LOWEST_HP_PERCENT -> hpSwitch(current.hpPercent,current.percentKnown,candidate.hpPercent,candidate.percentKnown,current,candidate,c);
        };
    }

    private static boolean hpSwitch(Double currentHp,boolean currentKnown,Double candidateHp,boolean candidateKnown,Monster current,Monster candidate,HelperConfig.Config c){
        if(candidateKnown&&!currentKnown)return true;
        if(!candidateKnown&&currentKnown)return false;
        if(candidateKnown){
            double gain=c.attackSwitchHpGain<=0?0.01:c.attackSwitchHpGain;
            boolean percentMode = c.attackPriority == HelperConfig.AttackPriority.LOWEST_HP_PERCENT;
            double cur = percentMode ? hpRankPercent(current) : currentHp;
            double cand = percentMode ? hpRankPercent(candidate) : candidateHp;
            if(cand+gain<=cur)return true;
            if(Math.abs(cand-cur)<0.5&&c.preferAggressor&&candidate.hostile&&!current.hostile)return true;
            return false;
        }
        int gain=Math.max(1,c.attackSwitchDistanceGain);
        if(candidate.distance+gain<=current.distance)return true;
        return candidate.distance==current.distance&&c.preferAggressor&&candidate.hostile&&!current.hostile;
    }

    /** Stabilize fallback without delaying real, better HP evidence or emergency target release. */
    private static boolean shouldSwitchAt(Monster current, Monster candidate, HelperConfig.Config c,
                                          long now, long acquiredAt, long lastSwitchAt) {
        if (!shouldSwitch(current, candidate, c)) return false;
        boolean unknownFallback = isHpPriority(c.attackPriority)
                && !(c.attackPriority == HelperConfig.AttackPriority.LOWEST_HP_PERCENT
                        ? candidate.percentKnown : candidate.absoluteKnown);
        if (unknownFallback) {
            // Unknown HP cannot justify rapid ping-pong. Require a meaningful distance advantage.
            if (candidate.distance + Math.max(2, c.attackUnknownHpDistanceGain) > current.distance) return false;
            if (now - acquiredAt < c.attackUnknownHpHoldMs) return false;
        }
        // A verified HP advantage is meaningful enough to bypass this cooldown.
        boolean realHpAdvantage = isHpPriority(c.attackPriority)
                && (c.attackPriority == HelperConfig.AttackPriority.LOWEST_HP_PERCENT
                     ? candidate.percentKnown : candidate.absoluteKnown);
        if (!realHpAdvantage && now - lastSwitchAt < c.attackSwitchCooldownMs) return false;
        return true;
    }

    static boolean focusedSwitchForTests(HelperConfig.AttackPriority priority,
                                         int curDist, Double curAbs, Double curPct, long curAge,
                                         int candDist, Double candAbs, Double candPct, long candAge,
                                         boolean enabled) {
        HelperConfig.Config c = new HelperConfig.Config();
        c.attackPriority = priority;
        c.attackFocusProtection = enabled;
        c.attackFinishWounded = false;
        c.preferAggressor = false;
        Monster a = new Monster(1,"current",curDist,curAbs,null,normalizedHp(curPct),false,
                curAge,normalizedHp(curPct)!=null,curAbs!=null,false);
        Monster b = new Monster(2,"candidate",candDist,candAbs,null,normalizedHp(candPct),false,
                candAge,normalizedHp(candPct)!=null,candAbs!=null,false);
        return shouldSwitch(a,b,c);
    }

    static boolean shouldSwitchAtForTests(HelperConfig.AttackPriority priority, int curDist, Double curHp,
                                          int candDist, Double candHp, long now, long acquiredAt, long switchedAt,
                                          int holdMs, int distGain, int cooldownMs) {
        HelperConfig.Config cfg = new HelperConfig.Config();
        cfg.attackPriority = priority; cfg.attackFinishWounded = false; cfg.attackFocusProtection = false;
        cfg.preferAggressor = false; cfg.attackSwitchHpGain = 1;
        cfg.attackUnknownHpHoldMs = holdMs; cfg.attackUnknownHpDistanceGain = distGain;
        cfg.attackSwitchCooldownMs = cooldownMs;
        return shouldSwitchAt(testMonsterPercent(1,curDist,curHp,false),
                testMonsterPercent(2,candDist,candHp,false),cfg,now,acquiredAt,switchedAt);
    }

    static boolean shouldSwitchAtDetailedForTests(HelperConfig.AttackPriority priority,
                                                   int curDist, Double curAbs, Double curPct,
                                                   int candDist, Double candAbs, Double candPct,
                                                   long now, long acquired, long switched) {
        HelperConfig.Config cfg = new HelperConfig.Config();
        cfg.attackPriority=priority; cfg.attackFinishWounded=false; cfg.preferAggressor=false; cfg.attackFocusProtection=false;
        return shouldSwitchAt(testMonster(1,curDist,curAbs,curPct,false),
                testMonster(2,candDist,candAbs,candPct,false),cfg,now,acquired,switched);
    }

    /** Comma/semicolon list; full normalized monster name matches only (no accidental substring bans). */
    private static boolean ignoredByName(String name, String list) {
        if (name == null || list == null || list.isBlank()) return false;
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        for (String item : list.split("[,;]")) {
            if (normalized.equals(item.trim().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }
    static boolean ignoredByNameForTests(String name, String list) { return ignoredByName(name, list); }

    private static boolean isHpPriority(HelperConfig.AttackPriority p){return p==HelperConfig.AttackPriority.LOWEST_HP_ABSOLUTE||p==HelperConfig.AttackPriority.LOWEST_HP_PERCENT;}

    private static boolean finishProtected(Monster current, HelperConfig.Config c) {
        return c.attackFinishWounded && current != null && current.percentKnown && current.hpPercent <= c.attackFinishThreshold;
    }

    private static boolean finishProtectedAgainst(Monster current, Monster candidate, HelperConfig.Config c) {
        if (!finishProtected(current, c) || candidate == null) return false;
        // Finish-lock is only a stabilizer. It must never contradict the selected LOWEST_HP criterion.
        if (c.attackPriority == HelperConfig.AttackPriority.LOWEST_HP_PERCENT
                && current.percentKnown && candidate.percentKnown) {
            double gain = c.attackSwitchHpGain <= 0 ? 0.01 : c.attackSwitchHpGain;
            return !(candidate.hpPercent + gain <= current.hpPercent);
        }
        if (c.attackPriority == HelperConfig.AttackPriority.LOWEST_HP_ABSOLUTE
                && current.absoluteKnown && candidate.absoluteKnown) {
            double gain = c.attackSwitchHpGain <= 0 ? 0.01 : c.attackSwitchHpGain;
            return !(candidate.hpCurrent + gain <= current.hpCurrent);
        }
        // The finish lock must not block a candidate with a measured value
        // when the current target has no measurement in the selected mode.
        // Otherwise a percent-only current target could trap absolute-HP mode.
        if (c.attackPriority == HelperConfig.AttackPriority.LOWEST_HP_ABSOLUTE)
            return current.absoluteKnown || !candidate.absoluteKnown;
        if (c.attackPriority == HelperConfig.AttackPriority.LOWEST_HP_PERCENT)
            return current.percentKnown || !candidate.percentKnown;
        return true;
    }

    private static boolean switchConfirmed(Monster current, Monster best, HelperConfig.Config c, long now) {
        if (best == null) return false;
        // Strong HP evidence should not wait behind an arbitrary debounce.
        if (c.attackPriority == HelperConfig.AttackPriority.LOWEST_HP_PERCENT) {
            if (best.percentKnown && !current.percentKnown && !c.attackFocusProtection) return true;
            if (best.percentKnown && current.percentKnown) {
                double improvement = current.hpPercent - best.hpPercent;
                if (improvement >= Math.max(3.0, Math.max(1, c.attackSwitchHpGain) * 2.0)) return true;
            }
        } else if (c.attackPriority == HelperConfig.AttackPriority.LOWEST_HP_ABSOLUTE) {
            if (best.absoluteKnown && !current.absoluteKnown && !c.attackFocusProtection) return true;
            if (best.absoluteKnown && current.absoluteKnown) {
                double improvement = current.hpCurrent - best.hpCurrent;
                if (improvement >= Math.max(3.0, Math.max(1, c.attackSwitchHpGain) * 2.0)) return true;
            }
        }
        if (c.attackFinishWounded && !finishProtectedAgainst(current, best, c)
                && best.percentKnown && best.hpPercent <= c.attackFinishThreshold) return true;
        if (c.attackDecisionConfirmMs <= 0) return true;
        if (!Objects.equals(pendingBestId, best.id)) {
            pendingBestId = best.id;
            pendingBestSince = now;
            return false;
        }
        return now - pendingBestSince >= c.attackDecisionConfirmMs;
    }

    static boolean shouldSwitchForTests(HelperConfig.AttackPriority p, int currentDist, Double currentHp, boolean currentHostile,
                                        int candidateDist, Double candidateHp, boolean candidateHostile,
                                        int distGain, int hpGain, boolean preferAggressor) {
        HelperConfig.Config c = new HelperConfig.Config();
        c.attackPriority = p; c.attackSwitchDistanceGain = distGain; c.attackSwitchHpGain = hpGain;
        c.preferAggressor = preferAggressor; c.attackFinishWounded = false; c.attackFocusProtection = false;
        Monster a = testMonsterPercent(1, currentDist, currentHp, currentHostile);
        Monster b = testMonsterPercent(2, candidateDist, candidateHp, candidateHostile);
        return shouldSwitch(a, b, c);
    }

    static boolean shouldSwitchDetailedForTests(HelperConfig.AttackPriority p,int currentDist,Double currentAbs,Double currentPct,
                                                int candidateDist,Double candidateAbs,Double candidatePct,int hpGain){
        HelperConfig.Config c=new HelperConfig.Config();c.attackPriority=p;c.attackSwitchDistanceGain=1;c.attackSwitchHpGain=hpGain;c.preferAggressor=false;c.attackFinishWounded=false;c.attackFocusProtection=false;
        Monster a=testMonster(1,currentDist,currentAbs,currentPct,false);Monster b=testMonster(2,candidateDist,candidateAbs,candidatePct,false);
        return shouldSwitch(a,b,c);
    }

    static boolean finishProtectedForTests(Double hp, boolean enabled, int threshold) {
        HelperConfig.Config c = new HelperConfig.Config(); c.attackFinishWounded = enabled; c.attackFinishThreshold = threshold;
        return finishProtected(testMonsterPercent(1, 2, hp, false), c);
    }

    static Integer chooseForTests(HelperConfig.AttackPriority p, boolean preferAggressor, TargetPriority.Candidate... candidates) {
        HelperConfig.Config c = new HelperConfig.Config(); c.attackPriority = p; c.preferAggressor = preferAggressor;
        List<Monster> ms = new ArrayList<>();
        for (TargetPriority.Candidate x : candidates) ms.add(testMonster(x.id(),x.distance(),x.hpCurrent(),x.hpPercent(),x.hostile()));
        ms.sort(monsterComparator(c));
        return ms.isEmpty() ? null : ms.get(0).id;
    }

    private static Monster testMonsterPercent(int id,int distance,Double pct,boolean hostile){return testMonster(id,distance,null,pct,hostile);}
    private static Monster testMonster(int id,int distance,Double abs,Double pct,boolean hostile){
        Double p=normalizedHp(pct);Double a=abs!=null&&Double.isFinite(abs)&&abs>0?abs:null;
        return new Monster(id,"M"+id,distance,a,null,p,hostile,(a==null&&p==null)?Long.MAX_VALUE:0L,p!=null,a!=null,false);
    }

    static boolean finishBlocksForTests(HelperConfig.AttackPriority p, Double currentAbs, Double currentPct,
                                        Double candidateAbs, Double candidatePct, int hpGain, int threshold) {
        HelperConfig.Config c = new HelperConfig.Config();
        c.attackPriority = p; c.attackFinishWounded = true; c.attackFinishThreshold = threshold; c.attackSwitchHpGain = hpGain;
        Monster cur = testMonster(1, 2, currentAbs, currentPct, false);
        Monster cand = testMonster(2, 3, candidateAbs, candidatePct, false);
        return finishProtectedAgainst(cur, cand, c);
    }

    static boolean claimBlocksForTests(long firstSeenAt, long lastSeenAt, long confirmedUntil, long now,
                                       boolean hostile, boolean selfDefenseOverride) {
        ClaimState cs = new ClaimState();
        cs.firstSeenAt = firstSeenAt; cs.lastSeenAt = lastSeenAt; cs.confirmedUntil = confirmedUntil;
        Monster m = testMonster(1, 2, null, null, hostile);
        remoteClaims.put(1, cs);
        HelperConfig.Config c = new HelperConfig.Config(); c.attackSelfDefenseOverride = selfDefenseOverride;
        boolean out = isClaimBlocked(m, c, now);
        remoteClaims.remove(1);
        return out;
    }

    static boolean claimWouldConfirmForTests(long firstSeenAt, long now, int confirmMs) {
        return now - firstSeenAt >= Math.max(0, confirmMs);
    }

    static int compareForTests(HelperConfig.AttackPriority p, TargetPriority.Candidate a, TargetPriority.Candidate b, boolean preferAggressor) {
        HelperConfig.Config c = new HelperConfig.Config(); c.attackPriority = p; c.preferAggressor = preferAggressor;
        Monster ma = testMonster(a.id(), a.distance(), a.hpCurrent(), a.hpPercent(), a.hostile());
        Monster mb = testMonster(b.id(), b.distance(), b.hpCurrent(), b.hpPercent(), b.hostile());
        return monsterComparator(c).compare(ma, mb);
    }

    private static String buildRankingSummary(List<Monster> ranked, HelperConfig.Config c) {
        if (ranked == null || ranked.isEmpty()) return "ranking: nenhum candidato";
        String mode = switch (c.attackPriority) {
            case LOWEST_HP_PERCENT -> "HP%";
            case LOWEST_HP_ABSOLUTE -> "HP atual";
            case NEAREST -> "Distância perto";
            case FARTHEST -> "Distância longe";
        };
        StringBuilder b = new StringBuilder("ranking [" + mode + "]: ");
        int n = Math.min(3, ranked.size());
        for (int i = 0; i < n; i++) {
            if (i > 0) b.append("  ·  ");
            Monster m = ranked.get(i);
            b.append('#').append(i + 1).append(' ').append(shortName(m.name)).append(' ')
                    .append(hpCompact(m,c)).append(" d").append(m.distance);
            if (m.hostile) b.append('!');
        }
        if (isHpPriority(c.attackPriority) && c.attackFinishWounded) {
            b.append("  | finish≤").append(c.attackFinishThreshold).append('%');
        }
        if (lastClaimedCount > 0 || lastPendingClaimCount > 0 || lastPartyClaimCount > 0 || lastSelfDefenseOverrides > 0) {
            b.append("  | KS=").append(lastClaimedCount);
            if (lastPendingClaimCount > 0) b.append("+").append(lastPendingClaimCount).append("?");
            if (lastPartyClaimCount > 0) b.append(" PARTY=").append(lastPartyClaimCount);
            if (lastSelfDefenseOverrides > 0) b.append(" DEF=").append(lastSelfDefenseOverrides);
        }
        return b.toString();
    }

    private static String describe(Monster m) {
        return m.name + " d=" + m.distance + hpText(m);
    }

    private static String hpText(Monster m) {
        if(m==null)return " hp=?";
        String stale=m.hpAgeMs>250L?"~":"";
        if(m.absoluteKnown&&m.percentKnown)return " hp"+stale+"="+fmtHp(m.hpCurrent)+"/"+fmtHp(m.hpMax)+" ("+Math.round(m.hpPercent)+"%)";
        if(m.percentKnown)return " hp"+stale+"="+Math.round(m.hpPercent)+"%";
        if(m.absoluteKnown)return " hp"+stale+"="+fmtHp(m.hpCurrent)+"/"+fmtHp(m.hpMax);
        return " hp=?";
    }

    private static String hpCompact(Monster m,HelperConfig.Config c) {
        if(m==null)return "?";
        String stale=m.hpAgeMs>250L?"~":"";
        if(c.attackPriority==HelperConfig.AttackPriority.LOWEST_HP_ABSOLUTE&&m.absoluteKnown)return stale+fmtHp(m.hpCurrent)+"hp"+(m.percentKnown?"/"+Math.round(m.hpPercent)+"%":"");
        if(m.percentKnown)return stale+Math.round(m.hpPercent)+"%"+(m.absoluteKnown?"/"+fmtHp(m.hpCurrent)+"hp":"");
        if(m.absoluteKnown)return stale+fmtHp(m.hpCurrent)+"hp";
        return "?";
    }
    private static String fmtHp(Double d){if(d==null)return"?";long r=Math.round(d);return Math.abs(d-r)<0.01?Long.toString(r):String.format(Locale.ROOT,"%.1f",d);}

    private static String shortName(String s) {
        if (s == null) return "?";
        String t = s.replace(' ', '_');
        return t.length() > 12 ? t.substring(0, 12) : t;
    }

    private static Double normalizedHp(Double hp) { return hp != null && hp > 0.1 && hp <= 100.0 ? hp : null; }

    private static boolean isContested(int id, long now) {
        Long u = contestedUntil.get(id); if (u == null) return false; if (u <= now) { contestedUntil.remove(id); return false; } return true;
    }

    private static void prepareReflection(Object game) throws Exception {
        ClassLoader loader = game.getClass().getClassLoader(); if (loader == cachedLoader && identityClass != null) return;
        cachedLoader = loader;
        identityClass = Class.forName(IDENTITY_CLASS, false, loader); posClass = Class.forName(POSITION_CLASS, false, loader);
        try { targetClass = Class.forName(TARGET_COMPONENT_CLASS, false, loader); targetGetter = targetClass.getMethod(TARGET_COMPONENT_GETTER); }
        catch (Throwable t) { targetClass = null; targetGetter = null; }
        typeField = identityClass.getField(TYPE_FIELD); idField = identityClass.getField(ID_FIELD); nameField = identityClass.getField(NAME_FIELD);
        xField = posClass.getField(X_FIELD); yField = posClass.getField(Y_FIELD);
        gameWorldGetter = game.getClass().getMethod(WORLD_GETTER); worldPlayerGetter = null; worldEntitiesGetter = null; entityGetComponent = null;
    }

    private static Scan scan(Object game, long now, HelperConfig.Config c) throws Exception {
        prepareReflection(game);
        Object world = gameWorldGetter.invoke(game); if (world == null) return null;
        if (worldPlayerGetter == null || worldPlayerGetter.getDeclaringClass() != world.getClass()) {
            worldPlayerGetter = world.getClass().getMethod(PLAYER_ENTITY_GETTER); worldEntitiesGetter = world.getClass().getMethod("getEntities");
        }
        Object playerEntity = worldPlayerGetter.invoke(world); if (playerEntity == null) return null;
        if (entityGetComponent == null || entityGetComponent.getDeclaringClass() != playerEntity.getClass()) {
            entityGetComponent = playerEntity.getClass().getMethod("getComponent", Class.class);
        }
        Position player = pos(playerEntity); if (player == null) return null;
        Integer localTargetId = entityId(targetClass == null ? null : targetEntity(playerEntity));
        Map<Integer, Monster> monsters = new HashMap<>(); Map<Integer,Position> monsterPositions = new HashMap<>(); Set<Integer> claimed = new HashSet<>(); Set<Integer> partyClaimed = new HashSet<>();
        PartySupport.beginScan();
        Object eo = worldEntitiesGetter.invoke(world); if (!(eo instanceof Iterable<?> entities)) return new Scan(player, monsters, claimed, partyClaimed, localTargetId, monsterPositions);
        for (Object e : entities) {
            if (e == playerEntity) continue;
            Object ident = entityGetComponent.invoke(e, identityClass); if (ident == null) continue;
            Object type = typeField.get(ident); String tn = type instanceof Enum<?> en ? en.name() : String.valueOf(type);
            int id = idField.getInt(ident); String name = String.valueOf(nameField.get(ident)); Position pp = pos(e); if (pp == null) continue;
            if ("NPC_MONSTER".equals(tn)) {
                int distance = cheb(player, pp);
                if (!withinScanRadius(player.x,player.y,pp.x,pp.y,Math.max(c.attackRange,c.attackScanRadius),
                        Objects.equals(localTargetId,id) || Objects.equals(ownTargetId,id))) continue;
                boolean h = false; if (targetClass != null) { try { h = targetEntity(e) == playerEntity; } catch (Throwable ignored) { } }
                // HP reflection is by far the expensive part. Only inspect the near field plus the
                // active/local target; distant world entities still count as visible but are not deep-scanned.
                boolean near = distance <= c.attackRange + 2 || Objects.equals(localTargetId, id) || Objects.equals(ownTargetId, id);
                if (near) VitalSensorSupport.observeMonsterVitals(e, id, Objects.equals(localTargetId, id) || Objects.equals(ownTargetId, id), now);
                VitalSensorSupport.MonsterHpObservation hp = VitalSensorSupport.monsterHpObservation(id, now, c.attackHpMemoryMs);
                Double pct=normalizedHp(hp.percent());Double abs=hp.current()!=null&&hp.current()>0?hp.current():null;Double max=hp.max()!=null&&hp.max()>0?hp.max():null;
                if(pct==null && c.attackPriority==HelperConfig.AttackPriority.LOWEST_HP_PERCENT && c.attackVisualHpEnabled && near) {
                    pct=normalizedHp(ScreenHpSupport.observe(id,pp.x-player.x,pp.y-player.y,c.attackRange,now));
                }
                boolean dead=(hp.current()!=null&&hp.current()<=0)||(hp.percent()!=null&&hp.percent()<=0.1);
                monsterPositions.put(id,pp);
                monsters.put(id, new Monster(id,name,distance,abs,max,pct,h,hp.ageMs(),pct!=null,abs!=null&&max!=null,dead));
            } else if ("PLAYER".equals(tn) && targetClass != null) {
                try {
                    Integer tid = entityId(targetEntity(e));
                    if (tid != null) {
                        boolean party = PartySupport.isPartyMember(e, id, name, c, now);
                        if (party) partyClaimed.add(tid); else claimed.add(tid);
                    }
                } catch (Throwable ignored) { }
            }
        }
        return new Scan(player, monsters, claimed, partyClaimed, localTargetId, monsterPositions);
    }

    private static Object targetEntity(Object entity) throws Exception {
        if (entity == null || targetClass == null || targetGetter == null) return null;
        Object c = entityGetComponent.invoke(entity, targetClass); return c == null ? null : targetGetter.invoke(c);
    }
    private static Integer entityId(Object e) { try { if (e == null) return null; Object i = entityGetComponent.invoke(e, identityClass); return i == null ? null : idField.getInt(i); } catch (Throwable t) { return null; } }
    private static Position pos(Object e) { try { Object p = entityGetComponent.invoke(e, posClass); if (p == null) return null; return new Position(xField.getInt(p), yField.getInt(p)); } catch (Throwable t) { return null; } }
    static boolean withinScanRadius(int px,int py,int mx,int my,int radius,boolean selected){
        return selected || (Math.abs(px-mx)<=Math.max(0,radius) && Math.abs(py-my)<=Math.max(0,radius));
    }
    private static int cheb(Position a, Position b) { return Math.max(Math.abs(a.x - b.x), Math.abs(a.y - b.y)); }

    private static void chaseCurrent(Object game, Scan scan, Monster target, HelperConfig.Config c, long now) {
        if (target==null || scan==null || !c.autoAttackEnabled || CaveBotEngine.isActive()) return;
        if (target.distance<=1 || target.distance>Math.min(c.attackRange,c.attackChaseLeash)) return;
        Position pp=scan.monsterPositions.get(target.id);
        if(pp==null || scan.player==null) return;
        try { ChaseSupport.tick(game,target.id,scan.player.x,scan.player.y,pp.x,pp.y,c.attackChaseLeash,now); }
        catch(Throwable ex){decision("CHASE erro="+ex.getClass().getSimpleName());}
    }

    private static void sendAggressive(Object game, int id) throws Exception { sendTarget(game, id, 1); }
    private static void sendNone(Object game, int id) throws Exception { sendTarget(game, id, 2); }
    private static void sendTarget(Object game, int id, int modeOrdinal) throws Exception {
        ClassLoader l = game.getClass().getClassLoader();
        Class<?> ab = Class.forName(ACTION_BASE_CLASS, false, l), st = Class.forName(SET_TARGET_CLASS, false, l), mc = Class.forName(SET_TARGET_MODE_CLASS, false, l);
        Object a = game.getClass().getMethod(ACTION_FACTORY_METHOD, Class.class).invoke(game, st);
        Object[] modes = mc.getEnumConstants(); st.getMethod(SET_TARGET_CONFIGURE_METHOD, int.class, mc).invoke(a, id, modes[modeOrdinal]);
        boolean old = INTERNAL_TARGET_SEND.get(); INTERNAL_TARGET_SEND.set(Boolean.TRUE);
        try { game.getClass().getMethod(SEND_ACTION_METHOD, ab).invoke(game, a); }
        finally { INTERNAL_TARGET_SEND.set(old); }
    }


    static boolean partyClaimBlocksForTests(boolean antiKs, boolean claimantParty, boolean hostile, boolean selfDefenseOverride) {
        if (!antiKs) return false;
        if (claimantParty) return false;
        return !(selfDefenseOverride && hostile);
    }

    static double hpRankPercentForTests(double hp, long ageMs) {
        return hpRankPercent(new Monster(1,"T",2,null,null,hp,false,ageMs,true,false,false));
    }

    private static final class ClaimState {
        long firstSeenAt;
        long lastSeenAt;
        long confirmedUntil;
        boolean rawSeenThisScan;
        boolean confirmed(long now) { return confirmedUntil > now; }
    }
    private record Position(int x, int y) { }
    private record Monster(int id,String name,int distance,Double hpCurrent,Double hpMax,Double hpPercent,boolean hostile,long hpAgeMs,boolean percentKnown,boolean absoluteKnown,boolean deadLike) { }
    private record Scan(Position player, Map<Integer, Monster> monsters, Set<Integer> claimed, Set<Integer> partyClaimed, Integer localTargetId, Map<Integer, Position> monsterPositions) { }
    private record ManualCommand(long sequence, int targetId, int modeOrdinal, long observedAt) { }
}
