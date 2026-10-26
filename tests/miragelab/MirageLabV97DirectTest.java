package miragelab;

import java.util.*;
public final class MirageLabV97DirectTest {
    static int checks;
    static void ok(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
    static String key(String field){return GameDirectVitals.VITAL_CLASS+"#"+field;}
    static Map<String,Double> metrics(double hp,double maxHp,double mana,double maxMana,double food){
        Map<String,Double> m=new HashMap<>();
        m.put(key(GameDirectVitals.HP_CURRENT),hp);m.put(key(GameDirectVitals.HP_MAX),maxHp);
        m.put(key(GameDirectVitals.MP_CURRENT),mana);m.put(key(GameDirectVitals.MP_MAX),maxMana);
        m.put(key(GameDirectVitals.FOOD_RAW),food);return m;
    }
    public static class FakeEntity {
        uk.co.harveydogs.mirage.shared.yY8x1yOsuoav.Xb046TljtoadQTFItzaxrgXLI vital=new uk.co.harveydogs.mirage.shared.yY8x1yOsuoav.Xb046TljtoadQTFItzaxrgXLI();
        public Object getComponent(Class<?> cls){return cls.isInstance(vital)?vital:null;}
    }
    public static void main(String[] args){
        FakeEntity f=new FakeEntity();f.vital.eACqBMENic3YHQLgkQTnYXFlcXmJ=58;
        f.vital.FIeR9ZxfSOyX=325;f.vital.WVfgbdbcCRRmDq1CfPzsI1qtv=48;
        f.vital.RdO6XVVbolTs44OnCNBZTVZS=235;f.vital.iNCztOvJLBPunDQaaXW1VZF4ggQ=1800f;
        var a=VitalsAdapter0859.read(f);
        ok(a!=null&&a.hpValid()&&a.manaValid(),"cached direct reflection works");
        ok(Math.abs(a.hp()/a.maxHp()*100-17.846)<.02,"typed HP");
        ok(Math.abs(a.mana()/a.maxMana()*100-20.425)<.02,"typed mana");
        ok(Math.abs(a.nourishment()-30)<.01,"typed nourishment");
        long t=System.currentTimeMillis();
        for(int i=1;i<=2500;i++){
            int max=100+i;
            int hp=i%max;
            var m=metrics(hp,max,i%120,120,(i*31)%3636);
            var h=GameDirectVitals.resolve(m,"hp",t);
            var mana=GameDirectVitals.resolve(m,"mana",t);
            ok(h.valid(),"HP valid");ok(mana.valid(),"MP valid");
            ok(Math.abs(h.percent()-100d*hp/max)<1e-7,"exact percentage");
            ok(Math.abs(mana.percent()-100d*(i%120)/120)<1e-7,"exact mana");
            ok(GameDirectVitals.nourishmentFrom(m)!=null,"food known");
            ok(GameDirectVitals.nourishmentFrom(m)>=0,"food positive");
            var corrupt=new HashMap<>(m);corrupt.put(key(GameDirectVitals.HP_MAX),-100d);
            ok(!GameDirectVitals.resolve(corrupt,"hp",t).valid(),"reject invalid max");
            corrupt=new HashMap<>(m);corrupt.put(key(GameDirectVitals.HP_CURRENT),100000d);
            ok(!GameDirectVitals.resolve(corrupt,"hp",t).valid(),"reject excess current");
            var unknown=new HashMap<>(m);unknown.remove(key(GameDirectVitals.HP_MAX));
            ok(!GameDirectVitals.resolve(unknown,"hp",t).valid(),"reject missing max");
        }
        var full=metrics(50,100,30,100,1800);
        ok(Math.abs(GameDirectVitals.nourishmentFrom(full)-30d)<.001,"food raw conversion");
        full.put(key(GameDirectVitals.FOOD_RAW),Double.NaN);
        ok(GameDirectVitals.nourishmentFrom(full)==null,"food NaN reject");
        AnalyzerEngine.reset();AnalyzerEngine.corpseObserved(41,t);AnalyzerEngine.corpseObserved(41,t+100);
        ok(AnalyzerEngine.snapshot(System.currentTimeMillis()).observedCorpses()==1,"deduplicate corpses");
        AnalyzerEngine.reset();ok(AnalyzerEngine.snapshot(System.currentTimeMillis()).observedCorpses()==0,"reset analyzer");
        ok(AnalyzerEngine.snapshot(System.currentTimeMillis()).xpPerHour()==null,"do not invent XP");
        System.out.println("V9.7 direct / analyzer PASS "+checks+" checks");
    }
}
