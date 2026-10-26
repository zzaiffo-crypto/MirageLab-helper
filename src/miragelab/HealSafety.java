package miragelab;

/** Fail-closed potion guard. A hotkey action is not proof that a potion was consumed.
 * Require confirmation of a meaningful HP/Mana increase before permitting another.
 * Once a use fails to produce a confirmed increase, stop sending keys until toggled off/on.
 */
final class HealSafety {
    private double previous=Double.NaN, before=Double.NaN;
    private long priorAt=-1, firstStableAt=-1, usedAt=-1;
    private boolean pending, blocked;
    private int confirmedUses;

    void reset(){previous=Double.NaN;before=Double.NaN;priorAt=-1;firstStableAt=-1;usedAt=-1;pending=false;blocked=false;confirmedUses=0;}
    void observe(Double value,long now){
        if(value==null||!Double.isFinite(value)||value<0||value>100){firstStableAt=-1;return;}
        if(pending && now-usedAt>=700){
            if(value >= before+2.0){pending=false;confirmedUses++;}
            else if(now-usedAt>=2200){pending=false;blocked=true;}
        }
        if(Double.isNaN(previous)||Math.abs(value-previous)>3.0|| priorAt<0 || now-priorAt>1200)firstStableAt=now;
        previous=value;priorAt=now;
    }
    boolean canUse(Double value,int threshold,long now,int cooldownMs){
        if(value==null||!Double.isFinite(value)||value<0||value>100||value>threshold)return false;
        if(blocked||pending||firstStableAt<0||now-firstStableAt<220)return false;
        return usedAt<0 || now-usedAt>=Math.max(3500,cooldownMs);
    }
    void used(Double value,long now){before=value==null?Double.NaN:value;usedAt=now;pending=true;}
    String status(){return blocked?"BLOQUEADO (sem melhora)":pending?"AGUARDANDO CONFIRMACAO":("OK confirmadas="+confirmedUses);}
    boolean blockedForTests(){return blocked;}
}
