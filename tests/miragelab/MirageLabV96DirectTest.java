package miragelab;
import java.util.*;
public final class MirageLabV96DirectTest {
    private static int assertions;
    private static void check(boolean x,String m){assertions++;if(!x)throw new AssertionError(m);}
    private static Map<String,Double> m(Object...args){Map<String,Double> x=new LinkedHashMap<>();
        for(int i=0;i<args.length;i+=2)x.put((String)args[i],((Number)args[i+1]).doubleValue());return x;}
    public static void main(String[] args){
        GameDirectVitals.resetForTests();
        var healthy=m("g.monster#hp",50,"g.monster#maxHp",200);
        var d=GameDirectVitals.resolve(healthy,"hp",100);
        check(d.valid(),"named current/max read");check(d.percent()==25,"correct percentage");
        check(d.current()==50 && d.max()==200,"correct absolute");
        check(!GameDirectVitals.resolve(m("g.monster#hp",199,"g.monster#maxHp",100),"hp",100).valid(),"reject cur>max");
        check(!GameDirectVitals.resolve(m("g.monster#hp",50,"g.monster#maxHp",0),"hp",100).valid(),"reject max zero");
        check(!GameDirectVitals.resolve(m("g.monster#hp",50),"hp",100).valid(),"reject no max");
        check(!GameDirectVitals.resolve(m("g.monster#hp",50,"other#maxHp",200),"hp",100).valid(),"reject unpaired component");
        check(!GameDirectVitals.resolve(m("g.monster#hp",50,"g.monster#maxHp",100,"g.monster#health",50),"hp",100).valid(),"reject ambiguous");
        check(!GameDirectVitals.resolve(m("com.badlogic.gdx.scenes.scene2d.Actor#hp",50,
                    "com.badlogic.gdx.scenes.scene2d.Actor#maxHp",200),"hp",100).valid(),"reject interface");
        check(!GameDirectVitals.resolve(m(GameDirectVitals.CANDIDATE_CURRENT,49),"hp",100).valid(),"raw obfuscated drop not a percentage");
        check(!GameDirectVitals.monster(m(GameDirectVitals.CANDIDATE_CURRENT,49),100).valid(),"raw obfuscated mob is not measured");
        check(GameDirectVitals.monsterStatus().contains("candidato"),"raw candidate exposed");
        check(GameDirectVitals.resolve(m("g.player#currentMana",10,"g.player#maxMana",200),"mana",100).percent()==5,"direct mana");
        check(!GameDirectVitals.resolve(m("g.player#currentMana",10,"g.player#maxMana",200),"hp",100).valid(),"mana != hp");
        Random rng=new Random(9306);
        for(int i=0;i<3600;i++){
            int max=1+rng.nextInt(5000),cur=rng.nextInt(max+1);
            var hp=GameDirectVitals.resolve(m("vital.Stats#currentHp",cur,"vital.Stats#maxHp",max),"hp",200+i);
            check(hp.valid(),"fuzz paired valid");
            check(Math.abs(hp.percent()-100.0*cur/max)<1e-8,"fuzz precision");
            check(!GameDirectVitals.resolve(m("vital.Stats#currentHp",cur),"hp",200+i).valid(),"fuzz never guess max");
        }
        System.out.println("V9.6 DIRECT TEST PASS "+assertions+" assertions");
    }
}
