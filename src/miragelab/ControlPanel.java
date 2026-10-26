package miragelab;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

/** v10.0 practical UI: overlay-first, instant settings, fast targeting. */
final class ControlPanel {
    private static final Path LAB = Path.of(System.getProperty("user.home"), "Downloads", "MirageLab");
    private static final Path STATE = LAB.resolve("state"), RUNTIME = LAB.resolve("runtime");
    private static final Path RECORD = STATE.resolve("recording.txt"), CAVE = STATE.resolve("cavebot.txt");
    private static final Path CAVE_CFG = STATE.resolve("cavebot_config.txt"), PANEL = STATE.resolve("panel.txt");
    private static final Path CAVE_STATUS = RUNTIME.resolve("cavebot_status.txt"), RECORD_STATUS = RUNTIME.resolve("recording_status.txt");
    private static final Path AGENT_STATUS = RUNTIME.resolve("agent_status.txt"), EVENTS = RUNTIME.resolve("cavebot_events.txt"), HELPER_EVENTS = RUNTIME.resolve("helper_events.txt");
    private static final Path DIAG = LAB.resolve("diagnostics_latest.txt");

    private static final Color BG = new Color(7,10,15), SURFACE=new Color(12,17,24), CARD=new Color(16,23,32), CARD2=new Color(20,29,40);
    private static final Color BORDER=new Color(41,57,76), TEXT=new Color(232,239,245), MUTED=new Color(139,154,168), ACCENT=new Color(52,217,255), GREEN=new Color(82,226,137), ORANGE=new Color(251,191,36);
    private static volatile boolean launched;
    private static JFrame frame;
    private static volatile boolean syncing;

    private ControlPanel() {}

    static void launch(){ GameOverlay.launch(); }
    static void showMainWindow(){GameOverlay.hideForPanel();SwingUtilities.invokeLater(()->{if(frame==null)show();else{frame.setVisible(true);frame.setState(JFrame.NORMAL);frame.toFront();}});}

    private static void show(){
        try{Files.createDirectories(STATE);Files.createDirectories(RUNTIME);}catch(Exception ignored){}
        applyTheme();
        if(frame!=null){frame.setVisible(true);return;}
        frame=new JFrame("MirageLab Helper v10.0");frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);frame.setMinimumSize(new Dimension(980,700));frame.setSize(1080,770);frame.setLocationByPlatform(true);
        JPanel root=new JPanel(new BorderLayout());root.setBackground(BG);
        JPanel header=new JPanel(new BorderLayout());header.setBackground(BG);header.setBorder(new EmptyBorder(14,18,9,18));
        JLabel brand=new JLabel("MIRAGELAB  //  HELPER v10.0");brand.setForeground(ACCENT);brand.setFont(new Font("SansSerif",Font.BOLD,22));
        JLabel sub=new JLabel("live combat helper  •  instant settings  •  game overlay");sub.setForeground(MUTED);sub.setFont(new Font("SansSerif",Font.PLAIN,12));
        JPanel tp=new JPanel(new GridLayout(2,1));tp.setOpaque(false);tp.add(brand);tp.add(sub);header.add(tp,BorderLayout.WEST);
        JButton overlayBtn=accentButton("← VOLTAR AO JOGO");header.add(overlayBtn,BorderLayout.EAST);root.add(header,BorderLayout.NORTH);

        JTabbedPane tabs=new JTabbedPane(JTabbedPane.LEFT);tabs.setBackground(SURFACE);tabs.setForeground(TEXT);tabs.setBorder(new EmptyBorder(0,10,10,10));

        // Helper controls
        JCheckBox aa=check("Auto Attack ativo");JSpinner range=sp(7,1,30),scanMs=sp(16,15,500);JComboBox<HelperConfig.AttackPriority> priority=new JComboBox<>(HelperConfig.AttackPriority.values());stylePriority(priority);
        JCheckBox retarget=check("Trocar alvo automaticamente"),attackAntiKs=check("Anti-KS inteligente"),partyAuto=check("Detectar party automaticamente");JSpinner retargetMs=sp(25,20,2000),distGain=sp(1,0,10),hpGain=sp(1,0,100),manualLock=sp(1200,0,10000);JCheckBox preferAggro=check("Agressor como desempate");JTextField partyNames=new JTextField();
        JTextField ignoredNames=new JTextField(); ignoredNames.setToolTipText("Nomes exatos, separados por vírgula. Ex.: Butterfly. Não ataca esses monstros.");
        JSpinner unknownHold=sp(1800,0,10000), unknownGain=sp(2,1,10), switchCooldown=sp(650,0,5000);partyNames.setToolTipText("Nomes da party separados por vírgula. Ex.: Winny, ElyZto");
        JCheckBox finishWounded=check("Finalizar alvo ferido");JSpinner finishThreshold=sp(35,1,95),hpMemory=sp(7000,500,30000),decisionConfirm=sp(40,0,1000);
        JCheckBox skills=check("Auto Skills ativo"),s1=check("Skill 1"),s2=check("Skill 2"),s3=check("Skill 3");JComboBox<String>s1k=comboKeys(),s2k=comboKeys(),s3k=comboKeys();JSpinner s1c=sp(4000,250,120000),s2c=sp(7000,250,120000),s3c=sp(12000,250,120000),skillGap=sp(220,80,5000);
        JCheckBox manaTrain=check("Mana Training ativo"),manaCombat=check("Também durante combate");JComboBox<String>manaTrainKey=comboKeys();JSpinner manaInt=sp(60,5,3600),inputGap=sp(90,40,1000),pulseMs=sp(55,20,250);
        JCheckBox heal=check("Auto Heal ativo"), hpOn=check("Curar HP"), mpOn=check("Repor mana");JSpinner hpTh=sp(50,1,99),mpTh=sp(35,1,99),healCd=sp(3500,2500,60000);JComboBox<String>hpKey=comboKeys(),mpKey=comboKeys();
        JCheckBox overlayEnabled=check("Overlay sobre o jogo");

        // Cave controls
        JCheckBox combat=check("Combate"),loop=check("Loop da rota"),loot=check("AutoLoot"),food=check("AutoFood"),antiKs=check("Anti-KS");
        JSpinner det=sp(4,1,20),ranged=sp(7,1,20),aggro=sp(12,1,30),combatDist=sp(1,1,4),leash=sp(6,2,20),look=sp(8,1,20),tol=sp(1,0,3),detour=sp(7,2,20),foodTh=sp(30,1,99);
        JSpinner burst=sp(2,1,3),burstWait=sp(170,80,1000),burstTimeout=sp(480,150,3000),fullPoll=sp(60,20,500),fastPoll=sp(12,8,100);

        // DASHBOARD
        JPanel dash=page();JPanel quick=card("Quick Control",new FlowLayout(FlowLayout.LEFT,8,4));
        JButton caveStart=accentButton("▶ CAVEBOT"),caveStop=button("■ STOP"),recStart=button("● REC ROTA"),recStop=button("■ STOP REC"),diagQuick=button("DIAGNÓSTICO"),hide=button("OCULTAR PAINEL");
        quick.add(overlayEnabled);quick.add(caveStart);quick.add(caveStop);quick.add(recStart);quick.add(recStop);quick.add(diagQuick);quick.add(hide);dash.add(quick,BorderLayout.NORTH);
        JTextArea overview=console();dash.add(new JScrollPane(overview),BorderLayout.CENTER);tabs.addTab("  Dashboard  ",dash);

        // AUTO ATTACK
        JPanel attack=page();JPanel attackCfg=card("Auto Attack — alterações salvam na hora",new GridLayout(0,4,8,8));
        JCheckBox screenHp=check("Reconhecer HP pela barra (beta)");
        screenHp.setToolTipText("Confirma a barra de vida em capturas independentes. Janela maximizada e escala padrao recomendadas.");
        screenHp.setSelected(HelperConfig.read().attackVisualHpEnabled);
        screenHp.addActionListener(e->{try{HelperConfig.update(c->c.attackVisualHpEnabled=screenHp.isSelected());}catch(Exception ex){err(frame,ex);}});
        JCheckBox focusLock=check("FOCO: nao trocar alvo com HP desconhecido");
        focusLock.setSelected(HelperConfig.read().attackFocusProtection);
        focusLock.setToolTipText("Nao troca o alvo atual apenas por distancia quando o modo escolhido e Menor HP.");
        focusLock.addActionListener(e->{try{HelperConfig.update(c->c.attackFocusProtection=focusLock.isSelected());}catch(Exception ex){err(frame,ex);}});
        JButton recalMobHp=button("REAPRENDER HP MOBS");attackCfg.add(aa);attackCfg.add(retarget);attackCfg.add(attackAntiKs);attackCfg.add(finishWounded);
        add(attackCfg,"Alcance",range,"Prioridade",priority);add(attackCfg,"Refresh inimigos (ms)",scanMs,"Reavaliar alvo (ms)",retargetMs);add(attackCfg,"Ganho distância",distGain,"Margem troca HP",hpGain);add(attackCfg,"Memória HP (ms)",hpMemory,"Confirmar decisão (ms)",decisionConfirm);add(attackCfg,"Finalizar abaixo de (%)",finishThreshold,"Lock clique manual (ms)",manualLock);attackCfg.add(partyAuto);attackCfg.add(label("Party (nomes, separados por vírgula)"));attackCfg.add(partyNames);attackCfg.add(new JLabel(""));attackCfg.add(recalMobHp);attackCfg.add(screenHp);attackCfg.add(focusLock);add(attackCfg,"Segurar HP desconhecido (ms)",unknownHold,"Vantagem distância sem HP",unknownGain);add(attackCfg,"Cooldown troca sem HP (ms)",switchCooldown,"Ignorar nome exato",ignoredNames);attackCfg.add(new JLabel("HP conhecido sempre vence HP desconhecido"));attackCfg.add(new JLabel("Agressor só desempata"));attackCfg.add(new JLabel(""));
        JTextArea attackStatus=console();attack.add(attackCfg,BorderLayout.NORTH);attack.add(new JScrollPane(attackStatus),BorderLayout.CENTER);tabs.addTab("  Auto Attack  ",attack);

        // SKILLS
        JPanel sk=page();JPanel skillCfg=card("Skills & Mana — pulso real de tecla",new GridLayout(0,4,8,8));skillCfg.add(skills);skillCfg.add(new JLabel(""));skillCfg.add(label("Gap skills (ms)"));skillCfg.add(skillGap);
        addSkillRow(skillCfg,s1,s1k,s1c);addSkillRow(skillCfg,s2,s2k,s2c);addSkillRow(skillCfg,s3,s3k,s3c);
        JPanel train=card("Mana Training",new GridLayout(0,4,8,8));train.add(manaTrain);train.add(manaCombat);train.add(new JLabel(""));train.add(new JLabel(""));add(train,"Hotkey",manaTrainKey,"Intervalo (s)",manaInt);add(train,"Gap inputs (ms)",inputGap,"Duração do key pulse (ms)",pulseMs);
        JTextArea skillStatus=console();JPanel skTop=new JPanel(new GridLayout(2,1,8,8));skTop.setOpaque(false);skTop.add(skillCfg);skTop.add(train);sk.add(skTop,BorderLayout.NORTH);sk.add(new JScrollPane(skillStatus),BorderLayout.CENTER);tabs.addTab("  Skills & Mana  ",sk);

        // HEAL
        JPanel hp=page();JPanel healCfg=card("Auto Heal",new GridLayout(0,4,8,8));healCfg.add(heal);healCfg.add(hpOn);healCfg.add(mpOn);healCfg.add(new JLabel(""));add(healCfg,"HP abaixo de (%)",hpTh,"Hotkey HP",hpKey);add(healCfg,"Mana abaixo de (%)",mpTh,"Hotkey Mana",mpKey);add(healCfg,"Cooldown (ms)",healCd,"",new JLabel(""));
        healCfg.add(label("HP/Mana: leitura direta do jogo. Sem max confiavel, nao utiliza pocao."));healCfg.add(new JLabel(""));healCfg.add(new JLabel(""));healCfg.add(new JLabel(""));JTextArea healStatus=console();hp.add(healCfg,BorderLayout.NORTH);hp.add(new JScrollPane(healStatus),BorderLayout.CENTER);tabs.addTab("  Auto Heal  ",hp);

        // CAVE
        JPanel cave=page();JPanel cb=card("Route",new FlowLayout(FlowLayout.LEFT,8,4));JButton clearRoute=button("LIMPAR ROTA");cb.add(recStart);cb.add(recStop);cb.add(clearRoute);cb.add(caveStart);cb.add(caveStop);
        JPanel nav=card("Navigation",new GridLayout(0,4,8,8));add(nav,"Lookahead",look,"Tolerância",tol);add(nav,"Raio A*",detour,"Burst",burst);add(nav,"Burst wait",burstWait,"Burst timeout",burstTimeout);add(nav,"World scan",fullPoll,"Fast player poll",fastPoll);nav.add(loop);nav.add(new JLabel(""));nav.add(new JLabel(""));nav.add(new JLabel(""));
        JPanel fight=card("Cave Combat",new GridLayout(0,4,8,8));add(fight,"Detecção",det,"Ranged",ranged);add(fight,"Agressor",aggro,"Distância",combatDist);add(fight,"Leash",leash,"",new JLabel(""));fight.add(combat);fight.add(antiKs);fight.add(new JLabel("Prioridade vem do Auto Attack"));fight.add(new JLabel(""));
        JTextArea caveStatus=console();JPanel ctop=new JPanel(new GridLayout(3,1,8,8));ctop.setOpaque(false);ctop.add(cb);ctop.add(nav);ctop.add(fight);cave.add(ctop,BorderLayout.NORTH);cave.add(new JScrollPane(caveStatus),BorderLayout.CENTER);tabs.addTab("  CaveBot  ",cave);

        // AUTOMATION
        JPanel autom=page();JPanel ac=card("Loot / Food / Anti-KS",new GridLayout(0,4,8,8));ac.add(loot);ac.add(food);ac.add(antiKs);ac.add(new JLabel(""));add(ac,"Nourishment abaixo de",foodTh,"",new JLabel(""));
        JComboBox<String> foodKey = new JComboBox<>(new String[]{"1","2","3","4","5","6","7","8","9","F1","F2","F3","F4"});
        add(ac,"Tecla da comida",foodKey,"",new JLabel(""));JTextArea autoStatus=console();autom.add(ac,BorderLayout.NORTH);autom.add(new JScrollPane(autoStatus),BorderLayout.CENTER);tabs.addTab("  Loot & Food  ",autom);

        // STATUS
        JPanel st=page();JPanel tools=card("Tools",new FlowLayout(FlowLayout.LEFT,8,4));JButton diag=button("GERAR DIAGNÓSTICO"),folder=button("ABRIR PASTA"),showOverlay=button("OVERLAY"),close=button("FECHAR PAINEL"),exit=button("ENCERRAR MIRAGE");tools.add(diag);tools.add(folder);tools.add(showOverlay);tools.add(close);tools.add(exit);JTextArea all=console();st.add(tools,BorderLayout.NORTH);st.add(new JScrollPane(all),BorderLayout.CENTER);tabs.addTab("  Status  ",st);

        root.add(tabs,BorderLayout.CENTER);frame.setContentPane(root);
        foodKey.setSelectedItem(readMap(CAVE_CFG).getOrDefault("auto_food_hotkey","1"));
        loadUi(aa,range,scanMs,priority,retarget,retargetMs,distGain,hpGain,manualLock,preferAggro,finishWounded,finishThreshold,hpMemory,decisionConfirm,heal,hpTh,hpKey,mpTh,mpKey,healCd,skills,s1,s1k,s1c,s2,s2k,s2c,s3,s3k,s3c,skillGap,manaTrain,manaTrainKey,manaInt,manaCombat,inputGap,pulseMs,overlayEnabled,combat,loop,loot,food,antiKs,det,ranged,aggro,combatDist,leash,look,tol,detour,foodTh,burst,burstWait,burstTimeout,fullPoll,fastPoll);
        hpOn.setSelected(HelperConfig.read().hpHealEnabled);mpOn.setSelected(HelperConfig.read().manaHealEnabled);
        HelperConfig.Config initialHelper=HelperConfig.read();attackAntiKs.setSelected(initialHelper.attackAntiKsEnabled);partyAuto.setSelected(initialHelper.partyAutoDetect);partyNames.setText(initialHelper.partyMembers);
        ignoredNames.setText(initialHelper.attackIgnoreNames); unknownHold.setValue(initialHelper.attackUnknownHpHoldMs);
        unknownGain.setValue(initialHelper.attackUnknownHpDistanceGain); switchCooldown.setValue(initialHelper.attackSwitchCooldownMs);

        // Instant-save helper settings: each listener mutates only its own field to avoid stale-form overwrites.
        on(aa,()->h(c->c.autoAttackEnabled=aa.isSelected()));on(retarget,()->h(c->c.attackRetargetEnabled=retarget.isSelected()));on(attackAntiKs,()->h(c->c.attackAntiKsEnabled=attackAntiKs.isSelected()));on(partyAuto,()->h(c->c.partyAutoDetect=partyAuto.isSelected()));bindText(partyNames,v->h(c->c.partyMembers=v));
        bindText(ignoredNames,v->h(c->c.attackIgnoreNames=v));
        on(unknownHold,()->h(c->c.attackUnknownHpHoldMs=(Integer)unknownHold.getValue()));
        on(unknownGain,()->h(c->c.attackUnknownHpDistanceGain=(Integer)unknownGain.getValue()));
        on(switchCooldown,()->h(c->c.attackSwitchCooldownMs=(Integer)switchCooldown.getValue()));on(preferAggro,()->h(c->c.preferAggressor=preferAggro.isSelected()));on(finishWounded,()->h(c->c.attackFinishWounded=finishWounded.isSelected()));
        on(range,()->h(c->c.attackRange=(Integer)range.getValue()));on(scanMs,()->h(c->c.attackScanIntervalMs=(Integer)scanMs.getValue()));on(priority,()->{h(c->c.attackPriority=(HelperConfig.AttackPriority)priority.getSelectedItem());caveSet("attack_priority",priority.getSelectedItem());});on(retargetMs,()->h(c->c.attackRetargetIntervalMs=(Integer)retargetMs.getValue()));on(distGain,()->h(c->c.attackSwitchDistanceGain=(Integer)distGain.getValue()));on(hpGain,()->h(c->c.attackSwitchHpGain=(Integer)hpGain.getValue()));on(manualLock,()->h(c->c.manualTargetLockMs=(Integer)manualLock.getValue()));on(finishThreshold,()->h(c->c.attackFinishThreshold=(Integer)finishThreshold.getValue()));on(hpMemory,()->h(c->c.attackHpMemoryMs=(Integer)hpMemory.getValue()));on(decisionConfirm,()->h(c->c.attackDecisionConfirmMs=(Integer)decisionConfirm.getValue()));
        on(hpOn,()->h(c->c.hpHealEnabled=hpOn.isSelected()));on(mpOn,()->h(c->c.manaHealEnabled=mpOn.isSelected()));
        on(heal,()->h(c->c.autoHealEnabled=heal.isSelected()));on(hpTh,()->h(c->c.hpThreshold=(Integer)hpTh.getValue()));on(hpKey,()->h(c->c.hpHotkey=(String)hpKey.getSelectedItem()));on(mpTh,()->h(c->c.manaThreshold=(Integer)mpTh.getValue()));on(mpKey,()->h(c->c.manaHotkey=(String)mpKey.getSelectedItem()));on(healCd,()->h(c->c.healCooldownMs=(Integer)healCd.getValue()));
        on(skills,()->h(c->c.autoSkillsEnabled=skills.isSelected()));on(s1,()->h(c->c.skill1Enabled=s1.isSelected()));on(s1k,()->h(c->c.skill1Hotkey=(String)s1k.getSelectedItem()));on(s1c,()->h(c->c.skill1CooldownMs=(Integer)s1c.getValue()));on(s2,()->h(c->c.skill2Enabled=s2.isSelected()));on(s2k,()->h(c->c.skill2Hotkey=(String)s2k.getSelectedItem()));on(s2c,()->h(c->c.skill2CooldownMs=(Integer)s2c.getValue()));on(s3,()->h(c->c.skill3Enabled=s3.isSelected()));on(s3k,()->h(c->c.skill3Hotkey=(String)s3k.getSelectedItem()));on(s3c,()->h(c->c.skill3CooldownMs=(Integer)s3c.getValue()));on(skillGap,()->h(c->c.skillGlobalGapMs=(Integer)skillGap.getValue()));
        on(manaTrain,()->h(c->c.manaTrainingEnabled=manaTrain.isSelected()));on(manaTrainKey,()->h(c->c.manaTrainingHotkey=(String)manaTrainKey.getSelectedItem()));on(manaInt,()->h(c->c.manaTrainingIntervalSec=(Integer)manaInt.getValue()));on(manaCombat,()->h(c->c.manaTrainingInCombat=manaCombat.isSelected()));on(inputGap,()->h(c->c.inputGlobalGapMs=(Integer)inputGap.getValue()));on(pulseMs,()->h(c->c.inputPulseMs=(Integer)pulseMs.getValue()));on(overlayEnabled,()->{h(c->c.overlayEnabled=overlayEnabled.isSelected());if(overlayEnabled.isSelected())GameOverlay.showOverlay();else GameOverlay.hideOverlay();});

        on(combat,()->caveSet("combat_enabled",combat.isSelected()));on(loop,()->caveSet("loop_route",loop.isSelected()));on(loot,()->caveSet("auto_loot",loot.isSelected()));on(food,()->caveSet("auto_food",food.isSelected()));on(antiKs,()->caveSet("anti_ks",antiKs.isSelected()));on(det,()->caveSet("detection_range",det.getValue()));on(ranged,()->caveSet("ranged_detection_range",ranged.getValue()));on(aggro,()->caveSet("aggro_detection_range",aggro.getValue()));on(combatDist,()->caveSet("combat_distance",combatDist.getValue()));on(leash,()->caveSet("combat_leash",leash.getValue()));on(look,()->caveSet("route_lookahead",look.getValue()));on(tol,()->caveSet("route_waypoint_tolerance",tol.getValue()));on(detour,()->caveSet("path_detour_radius",detour.getValue()));on(foodTh,()->caveSet("auto_food_threshold",foodTh.getValue()));on(foodKey,()->caveSet("auto_food_hotkey",foodKey.getSelectedItem()));on(burst,()->caveSet("route_burst_size",burst.getValue()));on(burstWait,()->caveSet("route_burst_wait_ms",burstWait.getValue()));on(burstTimeout,()->caveSet("route_burst_timeout_ms",burstTimeout.getValue()));on(fullPoll,()->caveSet("world_poll_ms",fullPoll.getValue()));on(fastPoll,()->caveSet("fast_player_poll_ms",fastPoll.getValue()));

        recalMobHp.addActionListener(e->{VitalSensorSupport.clearMonsterSensor();msg(frame,"Sensor de HP dos mobs limpo. Ataque um mob e o Helper vai reaprender HP atual/máximo.");});
        recStart.addActionListener(e->write(RECORD,"start "+System.nanoTime()));recStop.addActionListener(e->write(RECORD,"stop "+System.nanoTime()));clearRoute.addActionListener(e->write(RECORD,"clear "+System.nanoTime()));caveStart.addActionListener(e->write(CAVE,"start "+System.nanoTime()));caveStop.addActionListener(e->write(CAVE,"stop "+System.nanoTime()));
        ActionListener dl=e->{try{buildDiagnostics();msg(frame,"Diagnóstico criado em:\n"+DIAG);}catch(Exception x){err(frame,x);}};diag.addActionListener(dl);diagQuick.addActionListener(dl);
        folder.addActionListener(e->{try{if(Desktop.isDesktopSupported())Desktop.getDesktop().open(LAB.toFile());}catch(Exception x){err(frame,x);}});hide.addActionListener(e->returnToGame());close.addActionListener(e->returnToGame());exit.addActionListener(e->{if(JOptionPane.showConfirmDialog(frame,"Encerrar Mirage e Helper?","MirageLab",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION)System.exit(0);});overlayBtn.addActionListener(e->returnToGame());showOverlay.addActionListener(e->returnToGame());

        final String[] lastPanel={""};
        new javax.swing.Timer(300,e->{
            String aas=AutoAttackEngine.summary(),sks=SkillEngine.summary(),mts=ManaTrainingEngine.summary(),ahs=AutoHealEngine.summary(),cs=read(CAVE_STATUS),rs=read(RECORD_STATUS);
            attackStatus.setText(aas+"\n"+AutoAttackEngine.rankingSummary()+"\n"+VitalSensorSupport.monsterHpStatus()+"\n\n"+CombatContext.summary());skillStatus.setText(sks+"\n"+mts+"\n\n"+InputArbiter.summary());healStatus.setText(ahs+"\n\nLeitura: dados internos ECS do personagem.\nSem par atual/maximo confiavel, a cura e suspensa.\n\n"+InputArbiter.summary());caveStatus.setText(cs+"\n\n"+rs);autoStatus.setText(cs+"\n\n"+AutoFoodEngine.summary()+"\nAutoFood funciona mesmo sem CaveBot. Sem leitura confiavel de nourishment, nao consome.");overview.setText("AUTO ATTACK\n"+aas+"\n\nSKILLS\n"+sks+"\n"+mts+"\n\nHEAL\n"+ahs+"\n\nINPUT\n"+InputArbiter.summary()+"\n\nCAVEBOT\n"+cs);all.setText("=== AUTO ATTACK ===\n"+aas+"\n\n=== SKILLS ===\n"+sks+"\n"+mts+"\n\n=== HEAL ===\n"+ahs+"\n\n=== INPUT ===\n"+InputArbiter.summary()+"\n\n=== CAVEBOT ===\n"+cs+"\n\n=== RECORDER ===\n"+rs);
            syncFastToggles(aa,retarget,skills,manaTrain,heal,overlayEnabled);syncing=true;attackAntiKs.setSelected(HelperConfig.get(System.currentTimeMillis()).attackAntiKsEnabled);syncing=false;syncAttackFields(range,priority);
            try{String cmd=read(PANEL).trim();if(!cmd.isEmpty()&&!cmd.equals(lastPanel[0])){lastPanel[0]=cmd;if(cmd.toLowerCase().startsWith("show"))showMainWindow();else if(cmd.toLowerCase().startsWith("hide"))returnToGame();}}catch(Exception ignored){}
        }).start();
        frame.addWindowListener(new WindowAdapter(){@Override public void windowClosing(WindowEvent e){returnToGame();}});frame.setVisible(true);
    }

    private static void returnToGame(){if(frame!=null)frame.setVisible(false);GameOverlay.showOverlay();}

    private static void syncFastToggles(JCheckBox aa,JCheckBox rt,JCheckBox sk,JCheckBox mt,JCheckBox heal,JCheckBox ov){HelperConfig.Config c=HelperConfig.get(System.currentTimeMillis());syncing=true;aa.setSelected(c.autoAttackEnabled);rt.setSelected(c.attackRetargetEnabled);sk.setSelected(c.autoSkillsEnabled);mt.setSelected(c.manaTrainingEnabled);heal.setSelected(c.autoHealEnabled);ov.setSelected(c.overlayEnabled);syncing=false;}
    private static void syncAttackFields(JSpinner range,JComboBox<HelperConfig.AttackPriority> priority){HelperConfig.Config c=HelperConfig.get(System.currentTimeMillis());syncing=true;if(!range.getValue().equals(c.attackRange))range.setValue(c.attackRange);if(priority.getSelectedItem()!=c.attackPriority)priority.setSelectedItem(c.attackPriority);syncing=false;}
    private static void h(java.util.function.Consumer<HelperConfig.Config>x){if(syncing)return;try{HelperConfig.update(x);}catch(Exception e){err(frame,e);}}
    private static void caveSet(String k,Object v){if(syncing)return;try{String raw=Files.exists(CAVE_CFG)?Files.readString(CAVE_CFG,StandardCharsets.UTF_8):"# MirageLab CaveBot v10.0\n";raw=set(raw,k,v);Files.createDirectories(CAVE_CFG.getParent());SafeStateStore.write(CAVE_CFG,raw);}catch(Exception e){err(frame,e);}}

    private static void loadUi(JCheckBox aa,JSpinner ar,JSpinner scan,JComboBox<HelperConfig.AttackPriority> ap,JCheckBox rt,JSpinner rtm,JSpinner dg,JSpinner hg,JSpinner ml,JCheckBox pa,JCheckBox fw,JSpinner fwt,JSpinner hpm,JSpinner dc,JCheckBox ah,JSpinner hp,JComboBox<String>hk,JSpinner mp,JComboBox<String>mk,JSpinner cd,JCheckBox skills,JCheckBox s1,JComboBox<String>s1k,JSpinner s1c,JCheckBox s2,JComboBox<String>s2k,JSpinner s2c,JCheckBox s3,JComboBox<String>s3k,JSpinner s3c,JSpinner sg,JCheckBox mt,JComboBox<String>mtk,JSpinner mti,JCheckBox mtc,JSpinner ig,JSpinner pulse,JCheckBox ov,JCheckBox combat,JCheckBox loop,JCheckBox loot,JCheckBox food,JCheckBox aks,JSpinner det,JSpinner ranged,JSpinner aggro,JSpinner dist,JSpinner leash,JSpinner look,JSpinner tol,JSpinner dr,JSpinner ft,JSpinner burst,JSpinner bw,JSpinner bt,JSpinner wp,JSpinner fp){
        syncing=true;HelperConfig.Config h=HelperConfig.read();aa.setSelected(h.autoAttackEnabled);ar.setValue(h.attackRange);scan.setValue(h.attackScanIntervalMs);ap.setSelectedItem(h.attackPriority);rt.setSelected(h.attackRetargetEnabled);rtm.setValue(h.attackRetargetIntervalMs);dg.setValue(h.attackSwitchDistanceGain);hg.setValue(h.attackSwitchHpGain);ml.setValue(h.manualTargetLockMs);pa.setSelected(h.preferAggressor);fw.setSelected(h.attackFinishWounded);fwt.setValue(h.attackFinishThreshold);hpm.setValue(h.attackHpMemoryMs);dc.setValue(h.attackDecisionConfirmMs);ah.setSelected(h.autoHealEnabled);hp.setValue(h.hpThreshold);hk.setSelectedItem(h.hpHotkey);mp.setValue(h.manaThreshold);mk.setSelectedItem(h.manaHotkey);cd.setValue(h.healCooldownMs);skills.setSelected(h.autoSkillsEnabled);s1.setSelected(h.skill1Enabled);s1k.setSelectedItem(h.skill1Hotkey);s1c.setValue(h.skill1CooldownMs);s2.setSelected(h.skill2Enabled);s2k.setSelectedItem(h.skill2Hotkey);s2c.setValue(h.skill2CooldownMs);s3.setSelected(h.skill3Enabled);s3k.setSelectedItem(h.skill3Hotkey);s3c.setValue(h.skill3CooldownMs);sg.setValue(h.skillGlobalGapMs);mt.setSelected(h.manaTrainingEnabled);mtk.setSelectedItem(h.manaTrainingHotkey);mti.setValue(h.manaTrainingIntervalSec);mtc.setSelected(h.manaTrainingInCombat);ig.setValue(h.inputGlobalGapMs);pulse.setValue(h.inputPulseMs);ov.setSelected(h.overlayEnabled);
        Map<String,String>c=readMap(CAVE_CFG);combat.setSelected(bool(c,"combat_enabled",true));loop.setSelected(bool(c,"loop_route",true));loot.setSelected(bool(c,"auto_loot",true));food.setSelected(bool(c,"auto_food",true));aks.setSelected(bool(c,"anti_ks",true));det.setValue(num(c,"detection_range",4));ranged.setValue(num(c,"ranged_detection_range",7));aggro.setValue(num(c,"aggro_detection_range",12));dist.setValue(num(c,"combat_distance",1));leash.setValue(num(c,"combat_leash",6));look.setValue(num(c,"route_lookahead",8));tol.setValue(num(c,"route_waypoint_tolerance",1));dr.setValue(num(c,"path_detour_radius",7));ft.setValue(num(c,"auto_food_threshold",30));burst.setValue(num(c,"route_burst_size",2));bw.setValue(num(c,"route_burst_wait_ms",170));bt.setValue(num(c,"route_burst_timeout_ms",480));wp.setValue(num(c,"world_poll_ms",60));fp.setValue(num(c,"fast_player_poll_ms",12));syncing=false;
    }

    private static void bindText(JTextField f, java.util.function.Consumer<String> save){
        javax.swing.Timer t=new javax.swing.Timer(250,e->{if(!syncing)save.accept(f.getText().trim());});t.setRepeats(false);
        f.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){private void q(){if(!syncing)t.restart();}public void insertUpdate(javax.swing.event.DocumentEvent e){q();}public void removeUpdate(javax.swing.event.DocumentEvent e){q();}public void changedUpdate(javax.swing.event.DocumentEvent e){q();}});
    }

    private static void on(AbstractButton b,Runnable r){b.addActionListener(e->{if(!syncing)r.run();});}private static void on(JComboBox<?>b,Runnable r){b.addActionListener(e->{if(!syncing)r.run();});}private static void on(JSpinner s,Runnable r){s.addChangeListener(e->{if(!syncing)r.run();});}
    private static String set(String raw,String key,Object value){String[]ls=raw.split("\\R",-1);StringBuilder b=new StringBuilder();boolean found=false;for(String l:ls){if(l.trim().toLowerCase().startsWith(key.toLowerCase()+"=")){b.append(key).append('=').append(value).append('\n');found=true;}else if(!l.isEmpty())b.append(l).append('\n');}if(!found)b.append(key).append('=').append(value).append('\n');return b.toString();}
    private static void buildDiagnostics()throws IOException{StringBuilder b=new StringBuilder("MirageLab Helper v10.0 diagnostics - "+LocalDateTime.now()+"\n\n");section(b,"AUTO ATTACK",AutoAttackEngine.summary()+"\n"+AutoAttackEngine.rankingSummary()+"\n"+VitalSensorSupport.monsterHpStatus()+"\n"+ScreenHpSupport.status()+"\n"+PartySupport.summary());section(b,"DECISOES RECENTES DO AUTO TARGET",AutoAttackEngine.decisionHistory());section(b,"HP PROBE (CAMPOS EM OBSERVACAO)",read(LAB.resolve("state/monster_hp_probe_v91.txt")));section(b,"DADOS DIRETOS DO JOGO (DIAGNOSTICO)",
    "MONSTROS\n"+read(LAB.resolve("state/direct_monster_fields_v96.txt"))+
    "\nJOGADOR\n"+read(LAB.resolve("state/direct_player_fields_v96.txt")));section(b,"TELEMETRIA DA SESSAO",SessionTelemetry.summary());section(b,"SEGURANCA E ANALYSER",SecurityMonitor.summary()+"\n"+AnalyzerEngine.snapshot(System.currentTimeMillis()));section(b,"AUTO SKILLS",SkillEngine.summary()+"\n"+ManaTrainingEngine.summary());section(b,"AUTO HEAL",AutoHealEngine.summary());section(b,"AUTO FOOD",AutoFoodEngine.summary());section(b,"INPUT BUS",InputArbiter.summary());section(b,"HELPER CONFIG",read(HelperConfig.file()));section(b,"CAVEBOT STATUS",read(CAVE_STATUS));section(b,"RECORDING STATUS",read(RECORD_STATUS));section(b,"CAVEBOT EVENTS",read(EVENTS));section(b,"HELPER EVENTS",read(HELPER_EVENTS));section(b,"AGENT STATUS",read(AGENT_STATUS));Files.writeString(DIAG,b.toString(),StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);}private static void section(StringBuilder b,String n,String s){b.append("=== ").append(n).append(" ===\n").append(s).append("\n\n");}

    private static void stylePriority(JComboBox<HelperConfig.AttackPriority> c){c.setRenderer(new DefaultListCellRenderer(){@Override public Component getListCellRendererComponent(JList<?> l,Object v,int i,boolean s,boolean f){JLabel x=(JLabel)super.getListCellRendererComponent(l,v,i,s,f);if(v instanceof HelperConfig.AttackPriority p)x.setText(p.label());return x;}});}
    private static void applyTheme(){UIManager.put("Panel.background",BG);UIManager.put("TabbedPane.background",SURFACE);UIManager.put("TabbedPane.foreground",TEXT);UIManager.put("TabbedPane.selected",CARD2);UIManager.put("TabbedPane.contentAreaColor",BG);UIManager.put("Label.foreground",TEXT);UIManager.put("CheckBox.background",CARD);UIManager.put("CheckBox.foreground",TEXT);UIManager.put("ComboBox.background",CARD2);UIManager.put("ComboBox.foreground",TEXT);UIManager.put("TextArea.background",new Color(5,8,12));UIManager.put("TextArea.foreground",new Color(199,213,225));UIManager.put("TextArea.caretForeground",ACCENT);UIManager.put("Button.background",CARD2);UIManager.put("Button.foreground",TEXT);UIManager.put("Spinner.background",CARD2);UIManager.put("TextField.background",CARD2);UIManager.put("TextField.foreground",TEXT);}
    private static JPanel page(){JPanel p=new JPanel(new BorderLayout(9,9));p.setBackground(BG);p.setBorder(new EmptyBorder(10,10,10,10));return p;}private static JPanel card(String t,LayoutManager lm){JPanel p=new JPanel(lm);p.setBackground(CARD);TitledBorder tb=BorderFactory.createTitledBorder(BorderFactory.createLineBorder(BORDER),t);tb.setTitleColor(ACCENT);p.setBorder(BorderFactory.createCompoundBorder(tb,new EmptyBorder(7,7,7,7)));return p;}
    private static JButton button(String s){JButton b=new JButton(s);b.setFocusPainted(false);b.setBackground(CARD2);b.setForeground(TEXT);b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER),new EmptyBorder(6,10,6,10)));return b;}private static JButton accentButton(String s){JButton b=button(s);b.setForeground(ACCENT);b.setFont(new Font("SansSerif",Font.BOLD,12));return b;}
    private static JCheckBox check(String s){JCheckBox c=new JCheckBox(s);c.setOpaque(false);c.setForeground(TEXT);c.setFocusPainted(false);return c;}private static JLabel label(String s){JLabel l=new JLabel(s);l.setForeground(TEXT);return l;}private static JSpinner sp(int v,int min,int max){return new JSpinner(new SpinnerNumberModel(v,min,max,1));}private static JComboBox<String> comboKeys(){return new JComboBox<>(hotkeys());}
    private static void add(JPanel p,String a,JComponent av,String b,JComponent bv){p.add(label(a));p.add(av);p.add(label(b));p.add(bv);}private static void addSkillRow(JPanel p,JCheckBox on,JComboBox<String> key,JSpinner cd){p.add(on);p.add(key);p.add(label("Cooldown (ms)"));p.add(cd);}private static String[]hotkeys(){java.util.List<String>x=new ArrayList<>();for(int i=1;i<=9;i++)x.add(Integer.toString(i));for(int i=1;i<=12;i++)x.add("F"+i);x.add("SPACE");return x.toArray(String[]::new);}private static JTextArea console(){JTextArea a=new JTextArea();a.setEditable(false);a.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12));a.setBackground(new Color(5,8,12));a.setForeground(new Color(190,207,220));a.setBorder(new EmptyBorder(9,9,9,9));return a;}
    private static void write(Path p,String s){try{Files.createDirectories(p.getParent());Files.writeString(p,s+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);}catch(Exception ignored){}}private static String read(Path p){try{return Files.exists(p)?Files.readString(p,StandardCharsets.UTF_8):"<indisponível>";}catch(Exception e){return"<erro>";}}private static Map<String,String>readMap(Path p){Map<String,String>m=new HashMap<>();try{for(String l:Files.readAllLines(p,StandardCharsets.UTF_8)){String t=l.trim();int e=t.indexOf('=');if(e>0)m.put(t.substring(0,e).trim().toLowerCase(),t.substring(e+1).trim());}}catch(Exception ignored){}return m;}private static int num(Map<String,String>m,String k,int d){try{return Integer.parseInt(m.getOrDefault(k,""+d));}catch(Exception e){return d;}}private static boolean bool(Map<String,String>m,String k,boolean d){String v=m.get(k);return v==null?d:Boolean.parseBoolean(v);}private static void msg(Component c,String s){JOptionPane.showMessageDialog(c,s);}private static void err(Component c,Exception e){JOptionPane.showMessageDialog(c,"Erro: "+e.getMessage(),"MirageLab",JOptionPane.ERROR_MESSAGE);}
}
