package miragelab;

/** Pure, idempotent user modes; does not alter cave routes, movement, inventories or hotkeys. */
final class AutomationPresets {
    enum Mode { ATTACK_ONLY, ASSISTED, HUNT_READY, PAUSED }
    private AutomationPresets() {}
    static void apply(HelperConfig.Config c, Mode mode) {
        if (c==null || mode==null) throw new IllegalArgumentException("mode/config required");
        switch(mode){
            case ATTACK_ONLY -> {
                c.autoAttackEnabled=true; c.attackRetargetEnabled=true;
                c.autoSkillsEnabled=false; c.manaTrainingEnabled=false; c.autoHealEnabled=false;
            }
            case ASSISTED -> {
                c.autoAttackEnabled=true; c.attackRetargetEnabled=true;
                c.autoSkillsEnabled=true; c.manaTrainingEnabled=false; c.autoHealEnabled=false;
            }
            case HUNT_READY -> {
                c.autoAttackEnabled=true; c.attackRetargetEnabled=true;
                c.autoSkillsEnabled=true; c.manaTrainingEnabled=false;
                // Do NOT enable healing until the real sensor and hotkeys have been validated by the user.
                c.autoHealEnabled=false;
            }
            case PAUSED -> {
                c.autoAttackEnabled=false; c.autoSkillsEnabled=false;
                c.manaTrainingEnabled=false; c.autoHealEnabled=false;
            }
        }
    }
    static String describe(Mode mode){
        return switch(mode){
            case ATTACK_ONLY -> "Alvos automaticos; nenhuma skill, treino ou cura";
            case ASSISTED -> "Alvos + skills habilitadas; cura desligada";
            case HUNT_READY -> "Preset de combate; CaveBot e cura exigem ativacao separada";
            case PAUSED -> "Modulos auxiliares parados. CaveBot, se ativo, nao e afetado";
        };
    }
}
