package miragelab;

import java.util.concurrent.atomic.LongAdder;

/** Passive, thread-safe event counters. No in-game actions and no reliance on unsupported HP estimates. */
final class SessionTelemetry {
    private static final long started = System.nanoTime();
    private static final LongAdder selected=new LongAdder(), changed=new LongAdder(), manual=new LongAdder(), exclusions=new LongAdder();
    private SessionTelemetry(){}
    static void accept(String decision) {
        if(decision == null)return;
        if(decision.startsWith("TARGET "))selected.increment();
        else if(decision.startsWith("RETARGET "))changed.increment();
        else if(decision.startsWith("MANUAL "))manual.increment();
        else if(decision.startsWith("FILTER ")||decision.startsWith("ANTI-KS "))exclusions.increment();
    }
    static long totalSelections(){return selected.sum();}
    static long totalChanges(){return changed.sum();}
    static long totalManual(){return manual.sum();}
    static long totalReleases(){return exclusions.sum();}
    static String summary(){
        long min=Math.max(0,(System.nanoTime()-started)/60_000_000_000L);
        return "Sessao "+min+"min  | alvos "+selected.sum()+"  | trocas "+changed.sum()
                +"  | manuais "+manual.sum()+"  | liberacoes "+exclusions.sum();
    }
}
