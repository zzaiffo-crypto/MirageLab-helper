package miragelab;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Locale;
/** V10: compact, translucent session analyzer. No opaque fullscreen overlays. */
final class AnalyzerWindow {
    private static final Color BACK=new Color(12,19,29,222),INK=new Color(229,239,247),
            SUB=new Color(154,181,199),BLUE=new Color(86,206,243),GOOD=new Color(127,225,164),WARN=new Color(255,194,113);
    private static JWindow window;
    private static JLabel timer,xp,events,corpses,skill,security,resources;
    private AnalyzerWindow(){}
    static void toggle(){SwingUtilities.invokeLater(()->{if(window==null)build();window.setVisible(!window.isVisible());});}
    static void show(){SwingUtilities.invokeLater(()->{if(window==null)build();window.setVisible(true);});}
    static void hide(){SwingUtilities.invokeLater(()->{if(window!=null)window.setVisible(false);});}
    private static JLabel text(String value,int size,Color color,boolean bold){
        JLabel l=new JLabel(value);l.setFont(new Font(Font.SANS_SERIF,bold?Font.BOLD:Font.PLAIN,size));l.setForeground(color);return l;
    }
    private static JButton button(String value){
        JButton b=new JButton(value);b.setForeground(INK);b.setBackground(new Color(34,50,68));
        b.setFocusPainted(false);b.setFont(new Font(Font.SANS_SERIF,Font.BOLD,10));
        b.setBorder(new CompoundBorder(new LineBorder(new Color(60,85,110)),new EmptyBorder(5,9,5,9)));return b;
    }
    private static void build(){
        window=new JWindow();window.setAlwaysOnTop(true);window.setAutoRequestFocus(false);
        window.setBackground(new Color(0,0,0,0));
        JPanel root=new JPanel(new BorderLayout(2,11)){
            @Override protected void paintComponent(Graphics g){
                Graphics2D q=(Graphics2D)g.create();q.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                q.setColor(BACK);q.fillRoundRect(0,0,getWidth()-1,getHeight()-1,17,17);
                q.setColor(new Color(62,94,114,170));q.drawRoundRect(0,0,getWidth()-1,getHeight()-1,17,17);q.dispose();
            }
        };
        root.setOpaque(false);root.setBorder(new EmptyBorder(12,14,12,14));
        JPanel header=new JPanel(new BorderLayout(8,0));header.setOpaque(false);
        JLabel brand=text("ML   ANALYSER   /   V10",13,BLUE,true);header.add(brand,BorderLayout.WEST);
        JButton reset=button("RESET"),close=button("×");
        reset.setToolTipText("Zerar XP e corpos desta sessao, sem alterar suas configuracoes");
        reset.addActionListener(e->{AnalyzerEngine.reset();refresh();});close.addActionListener(e->hide());
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT,4,0));actions.setOpaque(false);
        actions.add(reset);actions.add(close);header.add(actions,BorderLayout.EAST);
        root.add(header,BorderLayout.NORTH);
        JPanel body=new JPanel(new GridLayout(7,1,0,5));body.setOpaque(false);
        timer=text("Sessao 00:00",11,SUB,false);
        xp=text("XP/h: aguardando evento",17,GOOD,true);
        events=text("XP recebida: —",11,INK,false);
        corpses=text("Corpos observados: —",11,INK,false);
        skill=text("Skill seguinte: nao mapeada",11,SUB,false);
        resources=text("Alimentacao: —",11,SUB,false);
        security=text("Seguranca: —",11,SUB,false);
        for(JLabel l:new JLabel[]{timer,xp,events,corpses,skill,resources,security})body.add(l);
        root.add(body,BorderLayout.CENTER);
        JLabel foot=text("Corpos nao equivalem a mortes confirmadas  •  arraste para mover",10,SUB,false);
        root.add(foot,BorderLayout.SOUTH);
        window.setContentPane(root);window.setSize(365,243);window.setLocation(75,100);
        final Point[] grab={null};
        MouseAdapter drag=new MouseAdapter(){
            public void mousePressed(MouseEvent e){grab[0]=e.getPoint();}
            public void mouseDragged(MouseEvent e){if(grab[0]==null)return;
                Point p=window.getLocation();window.setLocation(p.x+e.getX()-grab[0].x,p.y+e.getY()-grab[0].y);}
        };
        brand.addMouseListener(drag);brand.addMouseMotionListener(drag);
        foot.addMouseListener(drag);foot.addMouseMotionListener(drag);
        new Timer(500,e->{if(window.isVisible())refresh();}).start();refresh();
    }
    private static void refresh(){
        if(window==null)return;var s=AnalyzerEngine.snapshot(System.currentTimeMillis());
        long secs=s.elapsedMs()/1000;timer.setText(String.format(Locale.ROOT,"Sessao %02d:%02d  •  dados do cliente",secs/60,secs%60));
        xp.setText(s.xpPerHour()==null?"XP/h: aguardando evento":String.format(Locale.ROOT,"XP/h: %,.0f",s.xpPerHour()));
        xp.setToolTipText(s.source());
        events.setText("XP recebida: "+(s.gainedXp()==null?"—":String.format(Locale.ROOT,"%,d",s.gainedXp()))+"  | eventos: "+s.xpEvents());
        corpses.setText(String.format(Locale.ROOT,"Corpos observados: %d (%.1f/h)",s.observedCorpses(),s.bodiesPerHour()));
        skill.setText(s.skillRemaining()==null?"Skill seguinte: ainda nao mapeada":s.skillName()+" restante "+s.skillRemaining());
        resources.setText(ResourceMonitor.summary());security.setText(SecurityMonitor.summary());
        security.setForeground(SecurityMonitor.summary().startsWith("AVISO")?WARN:SUB);
    }
}
