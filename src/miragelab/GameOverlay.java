package miragelab;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** V9: compact tabbed cockpit, with separate combat/support/insight panes. */
final class GameOverlay {
    private static final Color BG=new Color(11,17,26,245), CARD=new Color(22,32,46), BORDER=new Color(54,73,93);
    private static final Color FG=new Color(236,243,251), SOFT=new Color(151,171,191), BLUE=new Color(76,207,249), GREEN=new Color(110,233,163), AMBER=new Color(252,190,87);
    private static final Path PANEL=Path.of(System.getProperty("user.home"),"Downloads","MirageLab","state","panel.txt");
    private static final Path CAVE=Path.of(System.getProperty("user.home"),"Downloads","MirageLab","state","cavebot.txt");
    private static final Path PLAY=Path.of(System.getProperty("user.home"),"Downloads","MirageLab","state","playback.txt");
    private static JWindow window;
    private static JPanel expandedRoot, collapsedRoot, partyEditor;
    private static JTextField partyNames;
    private static JButton onlyAttackButton;
    private static JTabbedPane tabs;
    private static JLabel live, hpLine, actionsLine, helpLine, sessionLine, partyLine;
    private static JCheckBox attack, retarget, skills, mana, heal, antiks, vision, chase, focus, hpHeal, mpHeal;
    private static JComboBox<HelperConfig.AttackPriority> priority;
    private static JSpinner range, healHpThreshold, healMpThreshold;
    private static JComboBox<String> healHpKey, healMpKey;
    private static JLabel vitalHud, foodHud;
    private static boolean panelOpen, watcherStarted, syncing;
    private static String lastPanelCommand="", lastError="";
    private GameOverlay(){}

    static void launch(){SwingUtilities.invokeLater(()->{
        try {if(window==null)build();panelOpen=false;HelperConfig.Config c=HelperConfig.read();applyCollapsed(c.overlayCollapsed);
             window.setVisible(c.overlayEnabled);if(c.overlayEnabled)window.toFront();startWatcher();
             LabHook.status("MirageLab v10.0 overlay: "+(window.isVisible()?"VISIBLE":"HIDDEN"));}
        catch(Throwable t){LabHook.status("MirageLab overlay ERROR: "+t);}
    });}
    private static void startWatcher(){if(watcherStarted)return;watcherStarted=true;
        new Timer(500,e->{try{checkPanelCommand();if(panelOpen||window==null)return;
            if(HelperConfig.get(System.currentTimeMillis()).overlayEnabled&&!window.isVisible()){
                window.setVisible(true);window.setAlwaysOnTop(true);window.toFront();
            }}catch(Throwable t){LabHook.status("overlay watchdog: "+t);}}).start();
    }
    static void showOverlay(){SwingUtilities.invokeLater(()->{try{if(window==null)build();panelOpen=false;
        applyCollapsed(HelperConfig.read().overlayCollapsed);window.setVisible(true);window.setAlwaysOnTop(true);window.toFront();
        HelperConfig.update(c->c.overlayEnabled=true);startWatcher();}catch(Exception ex){fail(ex);}});}
    static void hideOverlay(){SwingUtilities.invokeLater(()->{try{if(window!=null)window.setVisible(false);HelperConfig.update(c->c.overlayEnabled=false);}catch(Exception ex){fail(ex);}});}
    static void hideForPanel(){SwingUtilities.invokeLater(()->{panelOpen=true;if(window!=null)window.setVisible(false);});}
    static void collapseOverlay(){SwingUtilities.invokeLater(()->{try{if(window==null)build();applyCollapsed(true);window.setVisible(true);
        HelperConfig.update(c->{c.overlayEnabled=true;c.overlayCollapsed=true;});}catch(Exception ex){fail(ex);}});}
    static void expandOverlay(){SwingUtilities.invokeLater(()->{try{if(window==null)build();applyCollapsed(false);window.setVisible(true);
        HelperConfig.update(c->{c.overlayEnabled=true;c.overlayCollapsed=false;});}catch(Exception ex){fail(ex);}});}
    static boolean isCollapsedForTests(){return HelperConfig.read().overlayCollapsed;}

    private static void build(){window=new JWindow();window.setAlwaysOnTop(true);window.setAutoRequestFocus(false);window.setBackground(new Color(0,0,0,0));
        expandedRoot=buildExpanded();collapsedRoot=buildCollapsed();HelperConfig.Config c=HelperConfig.read();applyCollapsed(c.overlayCollapsed);
        Rectangle b=GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        if(c.overlayX>=0&&c.overlayY>=0)window.setLocation(onScreen(c.overlayX,c.overlayY));
        else window.setLocation(onScreen(b.x+b.width-window.getWidth()-22,b.y+48));
    }
    private static JPanel buildExpanded(){
        JPanel root=new JPanel(new BorderLayout(0,8));root.setBackground(BG);
        root.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER),new EmptyBorder(9,11,10,11)));
        JPanel head=new JPanel(new BorderLayout());head.setOpaque(false);
        JLabel brand=label("MIRAGELAB",BLUE,Font.BOLD,13);JLabel ver=label("V10   •   DIRETO DO JOGO",SOFT,Font.PLAIN,10);
        JPanel logos=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));logos.setOpaque(false);logos.add(brand);logos.add(ver);
        head.add(logos,BorderLayout.WEST);
        JButton collapse=button("—");collapse.setToolTipText("Recolher para icone pequeno");collapse.addActionListener(e->collapseOverlay());
        JButton pause=button("PARAR TUDO");pause.setForeground(AMBER);pause.setToolTipText("Desativa modulos e envia STOP para CaveBot/replay. Nao desliga o jogo.");
        pause.addActionListener(e->emergencyPause());
        JPanel topButtons=new JPanel(new FlowLayout(FlowLayout.RIGHT,5,0));topButtons.setOpaque(false);topButtons.add(pause);topButtons.add(collapse);
        head.add(topButtons,BorderLayout.EAST);installDrag(head,logos,brand);root.add(head,BorderLayout.NORTH);

        tabs=new JTabbedPane();tabs.setBackground(CARD);tabs.setForeground(FG);tabs.setFont(new Font("SansSerif",Font.BOLD,11));
        tabs.addTab("COMBATE",combatPane());tabs.addTab("SUPORTE",supportPane());tabs.addTab("STATUS",statusPane());
        root.add(tabs,BorderLayout.CENTER);
        JPanel foot=new JPanel(new BorderLayout(4,4));foot.setOpaque(false);
        live=label("Auto Attack: aguardando...",GREEN,Font.PLAIN,10);live.setFont(new Font(Font.MONOSPACED,Font.PLAIN,10));
        hpLine=label("HP: aguardando...",AMBER,Font.PLAIN,10);hpLine.setFont(new Font(Font.MONOSPACED,Font.PLAIN,10));
        JPanel messages=new JPanel(new GridLayout(2,1,0,3));messages.setOpaque(false);messages.add(live);messages.add(hpLine);
        JButton party=button("GRUPO");party.setToolTipText("Permitir membros de grupo no Anti-KS (nao cria party no jogo)");party.addActionListener(e->togglePartyEditor());
        JButton analyser=button("XP/H");analyser.setToolTipText("Abrir painel leve de estatisticas • RESET dedicado");
        analyser.addActionListener(e->AnalyzerWindow.toggle());
        JButton hp=button("STATUS");hp.setToolTipText("Mostra telemetria e estado dos sensores sem alterar os dados");hp.addActionListener(e->tabs.setSelectedIndex(2));
        JButton settings=button("AJUSTES");settings.addActionListener(e->{hideForPanel();ControlPanel.showMainWindow();});
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT,4,0));actions.setOpaque(false);actions.add(analyser);actions.add(hp);actions.add(party);actions.add(settings);
        foot.add(messages,BorderLayout.CENTER);foot.add(actions,BorderLayout.EAST);
        partyEditor=buildPartyEditor();JPanel south=new JPanel(new BorderLayout(0,5));south.setOpaque(false);south.add(foot,BorderLayout.NORTH);south.add(partyEditor,BorderLayout.SOUTH);root.add(south,BorderLayout.SOUTH);
        syncControls(HelperConfig.read());
        new Timer(250,e->refresh()).start();
        return root;
    }
    private static JPanel combatPane(){
        JPanel p=panel();p.setLayout(new BorderLayout(5,7));
        JPanel switches=new JPanel(new GridLayout(2,3,8,5));switches.setOpaque(false);
        attack=check("Auto Attack");retarget=check("Retarget");antiks=check("Anti-KS");
        switches.add(attack);switches.add(retarget);switches.add(antiks);
        switches.add(label("Distancia",SOFT,Font.PLAIN,11));
        range=new JSpinner(new SpinnerNumberModel(7,1,30,1));styleSpinner(range);switches.add(range);
        priority=new JComboBox<>(HelperConfig.AttackPriority.values());styleCombo(priority);priority.setRenderer(new DefaultListCellRenderer(){
            @Override public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus){
                JLabel l=(JLabel)super.getListCellRendererComponent(list,value,index,selected,focus);
                if(value instanceof HelperConfig.AttackPriority v)l.setText(v.label());return l;}});switches.add(priority);
        attack.addActionListener(e->set(c->c.autoAttackEnabled=attack.isSelected()));
        retarget.addActionListener(e->set(c->c.attackRetargetEnabled=retarget.isSelected()));
        antiks.addActionListener(e->set(c->c.attackAntiKsEnabled=antiks.isSelected()));
        priority.addActionListener(e->{if(!syncing)set(c->c.attackPriority=(HelperConfig.AttackPriority)priority.getSelectedItem());});
        range.addChangeListener(e->{if(!syncing)set(c->c.attackRange=(Integer)range.getValue());});
        p.add(switches,BorderLayout.NORTH);
        JPanel profiles=new JPanel(new GridLayout(1,2,7,0));profiles.setOpaque(false);
        onlyAttackButton=button("SÓ ATAQUE");JButton assist=button("ASSISTIDO");
        onlyAttackButton.addActionListener(e->applyPreset(AutomationPresets.Mode.ATTACK_ONLY));
        assist.addActionListener(e->applyPreset(AutomationPresets.Mode.ASSISTED));
        profiles.add(onlyAttackButton);profiles.add(assist);
        p.add(profiles,BorderLayout.CENTER);
        focus=check("FOCO: nao abandonar alvo sem HP");
        focus.setToolTipText("Em modos de HP, fica no alvo atual ate morte, saida do alcance ou leitura confiavel que justifique troca.");
        focus.addActionListener(e->set(c->c.attackFocusProtection=focus.isSelected()));
        vision=check("HP interno (sem captura de tela)");
        vision.setSelected(true);
        vision.setEnabled(false);
        chase=check("CHASE: aproximar 1 tile");
        chase.setToolTipText("Perseguicao opcional: movimentos cardinais supervisionados, distancia limitada, desliga ao parar Auto Attack");
        chase.addActionListener(e->set(c->c.attackChaseEnabled=chase.isSelected()));
        vision.setToolTipText("Fonte de HP baseada em componentes internos. Leitura de maximo nao confirmada = HP desconhecido.");
        JPanel opts=new JPanel(new GridLayout(3,1,2,2));opts.setOpaque(false);opts.add(focus);opts.add(chase);opts.add(vision);
        p.add(opts,BorderLayout.SOUTH);
        return p;
    }
    private static JPanel supportPane(){
        JPanel p=panel();p.setLayout(new BorderLayout(5,8));
        JPanel top=new JPanel(new GridLayout(1,3,8,4));top.setOpaque(false);
        skills=check("Skills");mana=check("Treino mana");heal=check("AUTO HEAL");
        top.add(skills);top.add(mana);top.add(heal);
        skills.addActionListener(e->set(c->c.autoSkillsEnabled=skills.isSelected()));
        mana.addActionListener(e->set(c->c.manaTrainingEnabled=mana.isSelected()));
        heal.addActionListener(e->set(c->c.autoHealEnabled=heal.isSelected()));
        p.add(top,BorderLayout.NORTH);
        JPanel settings=new JPanel(new GridLayout(3,4,7,5));settings.setOpaque(false);
        hpHeal=check("HP");hpHeal.setToolTipText("Permite poção HP quando ficar abaixo do limite");
        mpHeal=check("Mana");mpHeal.setToolTipText("Ative somente se configurou uma tecla para a poção de mana");
        healHpThreshold=new JSpinner(new SpinnerNumberModel(50,1,99,1));styleSpinner(healHpThreshold);
        healMpThreshold=new JSpinner(new SpinnerNumberModel(35,1,99,1));styleSpinner(healMpThreshold);
        String[] hotkeys={"1","2","3","4","5","6","7","8","9","F1","F2","F3","F4","F5","F6","F7","F8"};
        healHpKey=new JComboBox<>(hotkeys);healMpKey=new JComboBox<>(hotkeys);styleCombo(healHpKey);styleCombo(healMpKey);
        settings.add(hpHeal);settings.add(label("Curar abaixo de (%)",SOFT,Font.PLAIN,10));settings.add(healHpThreshold);settings.add(healHpKey);
        settings.add(mpHeal);settings.add(label("Mana abaixo de (%)",SOFT,Font.PLAIN,10));settings.add(healMpThreshold);settings.add(healMpKey);
        settings.add(label("Direto do jogo",GREEN,Font.BOLD,11));settings.add(label("Sem imagens",SOFT,Font.PLAIN,11));
        settings.add(label("Teclas ao lado",SOFT,Font.PLAIN,10));settings.add(label("poção no slot",SOFT,Font.PLAIN,10));
        hpHeal.addActionListener(e->set(c->c.hpHealEnabled=hpHeal.isSelected()));
        mpHeal.addActionListener(e->set(c->c.manaHealEnabled=mpHeal.isSelected()));
        healHpThreshold.addChangeListener(e->{if(!syncing)set(c->c.hpThreshold=(Integer)healHpThreshold.getValue());});
        healMpThreshold.addChangeListener(e->{if(!syncing)set(c->c.manaThreshold=(Integer)healMpThreshold.getValue());});
        healHpKey.addActionListener(e->{if(!syncing)set(c->c.hpHotkey=(String)healHpKey.getSelectedItem());});
        healMpKey.addActionListener(e->{if(!syncing)set(c->c.manaHotkey=(String)healMpKey.getSelectedItem());});
        p.add(settings,BorderLayout.CENTER);
        vitalHud=label("HP e Mana: aguardando ECS confirmado",AMBER,Font.PLAIN,10);
        foodHud=label("Food: CONFIG > Loot & Food (independente do CaveBot)",SOFT,Font.PLAIN,10);
        JPanel footer=new JPanel(new GridLayout(2,1,0,4));footer.setOpaque(false);footer.add(vitalHud);footer.add(foodHud);
        p.add(footer,BorderLayout.SOUTH);
        return p;
    }
    private static JPanel statusPane(){
        JPanel p=panel();p.setLayout(new GridLayout(5,1,0,4));
        sessionLine=label("Sessao iniciando",GREEN,Font.BOLD,11);
        actionsLine=label("Alvos: aguardando",FG,Font.PLAIN,11);
        helpLine=label("HP: nao confirmado",AMBER,Font.PLAIN,11);
        partyLine=label("Party: sem cadastro manual",SOFT,Font.PLAIN,11);
        JLabel hint=label("Para detalhes e diagnostico: CONFIG > Status",SOFT,Font.PLAIN,10);
        p.add(sessionLine);p.add(actionsLine);p.add(helpLine);p.add(partyLine);p.add(hint);
        return p;
    }
    private static JPanel buildPartyEditor(){
        JPanel p=new JPanel(new BorderLayout(6,5));p.setBackground(CARD);p.setBorder(new EmptyBorder(7,7,7,7));
        p.add(label("ANTI-KS • membros liberados (nomes separados por virgula)",FG,Font.PLAIN,11),BorderLayout.NORTH);
        partyNames=new JTextField(30);partyNames.setBackground(BG);partyNames.setForeground(FG);partyNames.setCaretColor(BLUE);p.add(partyNames,BorderLayout.CENTER);
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT,3,0));buttons.setOpaque(false);
        JButton save=button("SALVAR"),close=button("FECHAR");buttons.add(save);buttons.add(close);p.add(buttons,BorderLayout.EAST);
        save.addActionListener(e->saveParty());partyNames.addActionListener(e->saveParty());close.addActionListener(e->setPartyEditorVisible(false));
        p.setVisible(false);return p;
    }
    private static void refresh(){
        try{HelperConfig.Config c=HelperConfig.get(System.currentTimeMillis());syncControls(c);
            String s=AutoAttackEngine.summary(),rank=AutoAttackEngine.rankingSummary(),hp=AutoAttackEngine.hpGuidance(c);
            live.setText(shorten(s,80));live.setToolTipText(s);live.setForeground(c.autoAttackEnabled?GREEN:SOFT);
            hpLine.setText(shorten(hp,80));hpLine.setToolTipText(hp+" | "+VitalSensorSupport.monsterHpStatus());
            hpLine.setForeground(hp.startsWith("HP OK")?GREEN:AMBER);
            actionsLine.setText(shorten(rank,75));actionsLine.setToolTipText(rank);
            sessionLine.setText(SessionTelemetry.summary());
            helpLine.setText(shorten(VitalSensorSupport.monsterHpStatus(),80));
            helpLine.setToolTipText(VitalSensorSupport.monsterHpStatus());
            GameDirectVitals.Player vr=GameDirectVitals.playerStatus();
            if(vitalHud!=null) {
                vitalHud.setText(vr.hp().valid() || vr.mana().valid()
                    ? "HP "+GameDirectVitals.show(vr.hp())+" | Mana "+GameDirectVitals.show(vr.mana())
                    : "HP/Mana: aguardando dados internos confirmados");
                vitalHud.setForeground(vr.hp().valid()?GREEN:AMBER);
                vitalHud.setToolTipText(vr.status());
            }
            if(foodHud!=null){foodHud.setText(shorten(AutoFoodEngine.summary(),100));foodHud.setToolTipText(AutoFoodEngine.summary());}
            partyLine.setText("Party cadastrada: "+HelperConfig.normalizedPartyMembers(c).size()+"  | CaveBot: "+(CaveBotEngine.isActive()?"ATIVO":"OFF"));
            checkPanelCommand();
        }catch(Throwable ex){fail(ex);}
    }
    private static void syncControls(HelperConfig.Config c){if(attack==null)return;syncing=true;
        try{attack.setSelected(c.autoAttackEnabled);retarget.setSelected(c.attackRetargetEnabled);antiks.setSelected(c.attackAntiKsEnabled);
            skills.setSelected(c.autoSkillsEnabled);mana.setSelected(c.manaTrainingEnabled);heal.setSelected(c.autoHealEnabled);
            if(hpHeal!=null){hpHeal.setSelected(c.hpHealEnabled);mpHeal.setSelected(c.manaHealEnabled);
                healHpThreshold.setValue(c.hpThreshold);healMpThreshold.setValue(c.manaThreshold);
                healHpKey.setSelectedItem(c.hpHotkey);healMpKey.setSelectedItem(c.manaHotkey);}
            if(vision!=null)vision.setSelected(true);chase.setSelected(c.attackChaseEnabled);focus.setSelected(c.attackFocusProtection);
            if(priority.getSelectedItem()!=c.attackPriority)priority.setSelectedItem(c.attackPriority);
            if(!range.getValue().equals(c.attackRange))range.setValue(c.attackRange);
        }finally{syncing=false;}
    }
    private static void applyPreset(AutomationPresets.Mode mode){set(c->AutomationPresets.apply(c,mode));LabHook.status("MirageLab preset: "+mode);}
    private static void emergencyPause(){
        applyPreset(AutomationPresets.Mode.PAUSED);
        try{SafeStateStore.write(CAVE,"stop\n");SafeStateStore.write(PLAY,"stop\n");}
        catch(Exception e){fail(e);}
    }
    private static void togglePartyEditor(){if(partyEditor==null)return;boolean show=!partyEditor.isVisible();
        if(show)partyNames.setText(HelperConfig.read().partyMembers);setPartyEditorVisible(show);
        if(show)SwingUtilities.invokeLater(()->partyNames.requestFocusInWindow());
    }
    private static void saveParty(){set(c->{c.partyMembers=partyNames.getText().trim();c.partyAutoDetect=true;});setPartyEditorVisible(false);}
    private static void setPartyEditorVisible(boolean show){if(partyEditor==null||window==null)return;partyEditor.setVisible(show);
        if(!HelperConfig.read().overlayCollapsed){Point before=window.getLocation();window.pack();
            window.setSize(Math.max(570,window.getWidth()),window.getHeight());window.setLocation(onScreen(before.x,before.y));window.revalidate();}
    }
    static boolean partyEditorVisibleForTests(){return partyEditor!=null&&partyEditor.isVisible();}
    static void showPartyEditorForTests(){togglePartyEditor();}
    static void savePartyForTests(String names){partyNames.setText(names);saveParty();}
    static void onlyAttackForTests(){if(onlyAttackButton!=null)onlyAttackButton.doClick();}
    static void pauseForTests(){emergencyPause();}
    private static JPanel buildCollapsed(){JPanel p=panel();p.setLayout(new BorderLayout());p.setBorder(BorderFactory.createLineBorder(BLUE));
        JButton icon=button("ML");icon.setFont(new Font("SansSerif",Font.BOLD,15));icon.setToolTipText("Abrir MirageLab V9");
        icon.addActionListener(e->expandOverlay());p.add(icon,BorderLayout.CENTER);installDrag(p,icon);return p;}
    private static void applyCollapsed(boolean collapsed){if(window==null)return;Point old=window.getLocation();
        if(collapsed&&partyEditor!=null)partyEditor.setVisible(false);
        window.setContentPane(collapsed?collapsedRoot:expandedRoot);window.pack();
        if(collapsed)window.setSize(50,42);else window.setSize(Math.max(570,window.getWidth()),Math.max(255,window.getHeight()));
        window.setLocation(onScreen(old.x,old.y));window.revalidate();window.repaint();}
    private static void installDrag(Component... parts){final Point[]ds={null},ws={null};MouseAdapter d=new MouseAdapter(){
        public void mousePressed(MouseEvent e){ds[0]=e.getLocationOnScreen();ws[0]=window.getLocation();}
        public void mouseDragged(MouseEvent e){if(ds[0]!=null){Point q=e.getLocationOnScreen();window.setLocation(onScreen(ws[0].x+q.x-ds[0].x,ws[0].y+q.y-ds[0].y));}}
        public void mouseReleased(MouseEvent e){Point q=window.getLocation();set(c->{c.overlayX=q.x;c.overlayY=q.y;});ds[0]=null;}
    };for(Component c:parts){c.addMouseListener(d);c.addMouseMotionListener(d);}}
    private static void checkPanelCommand(){try{if(!Files.exists(PANEL))return;String command=Files.readString(PANEL,StandardCharsets.UTF_8).replace("\0","").trim();
        if(command.isEmpty()||command.equals(lastPanelCommand))return;lastPanelCommand=command;
        if(command.toLowerCase().startsWith("show")){hideForPanel();ControlPanel.showMainWindow();}
        else if(command.toLowerCase().startsWith("overlay"))showOverlay();
    }catch(Exception ex){fail(ex);}}
    private static void set(java.util.function.Consumer<HelperConfig.Config> mutation){if(syncing)return;
        try{HelperConfig.update(mutation);}catch(Exception ex){fail(ex);}}
    private static void fail(Throwable e){String m=e.getClass().getSimpleName()+": "+e.getMessage();if(!m.equals(lastError)){
        lastError=m;LabHook.status("MirageLab UI error: "+m);if(hpLine!=null)hpLine.setText("Erro ao salvar: "+shorten(m,65));}}
    private static JPanel panel(){JPanel p=new JPanel();p.setBackground(BG);p.setBorder(new EmptyBorder(8,5,8,5));return p;}
    private static JLabel label(String s,Color col,int weight,int size){JLabel x=new JLabel(s);x.setForeground(col);x.setFont(new Font("SansSerif",weight,size));return x;}
    private static JCheckBox check(String s){JCheckBox b=new JCheckBox(s);b.setOpaque(false);b.setForeground(FG);b.setFocusPainted(false);b.setFont(new Font("SansSerif",Font.PLAIN,11));return b;}
    private static JButton button(String s){JButton b=new JButton(s);b.setBackground(CARD);b.setForeground(BLUE);b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER),new EmptyBorder(5,9,5,9)));
        b.setFont(new Font("SansSerif",Font.BOLD,10));return b;}
    private static String shorten(String s,int limit){return s==null?"":(s.length()>limit?s.substring(0,limit-1)+"…":s);}
    private static Point onScreen(int x,int y){Rectangle b=GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int maxX=Math.max(b.x,b.x+b.width-window.getWidth()),maxY=Math.max(b.y,b.y+b.height-window.getHeight());
        return new Point(Math.max(b.x,Math.min(maxX,x)),Math.max(b.y,Math.min(maxY,y)));}
    private static void styleCombo(JComboBox<?> b){b.setBackground(CARD);b.setForeground(FG);b.setFont(new Font("SansSerif",Font.PLAIN,11));}
    private static void styleSpinner(JSpinner s){s.setBackground(CARD);s.setPreferredSize(new Dimension(60,24));
        if(s.getEditor() instanceof JSpinner.DefaultEditor e){e.getTextField().setBackground(CARD);e.getTextField().setForeground(FG);}}
}
