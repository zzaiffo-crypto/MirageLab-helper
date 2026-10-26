package miragelab;

import java.lang.reflect.*;
/** Direct component-mapper independent read-only v0.8.59 adapter; caches class and fields. */
final class VitalsAdapter0859 {
    private static ClassLoader lastLoader;
    private static Class<?> vitalsType;
    private static Method entityGetComponent;
    private static Field hp, maxHp, mp, maxMp, food;
    record Snapshot(Double hp,Double maxHp,Double mana,Double maxMana,Double nourishment,String source){
        boolean hpValid(){return valid(hp,maxHp);}
        boolean manaValid(){return valid(mana,maxMana);}
        private static boolean valid(Double a,Double b){return a!=null&&b!=null&&Double.isFinite(a)&&Double.isFinite(b)&&b>0&&a>=0&&a<=b;}
    }
    private VitalsAdapter0859(){}
    static synchronized Snapshot read(Object entity){
        if(entity==null)return null;
        try{
            ClassLoader loader=entity.getClass().getClassLoader();
            if(loader!=lastLoader){
                lastLoader=loader;vitalsType=Class.forName(GameDirectVitals.VITAL_CLASS,false,loader);
                hp=vitalsType.getField(GameDirectVitals.HP_CURRENT);
                maxHp=vitalsType.getField(GameDirectVitals.HP_MAX);
                mp=vitalsType.getField(GameDirectVitals.MP_CURRENT);
                maxMp=vitalsType.getField(GameDirectVitals.MP_MAX);
                food=vitalsType.getField(GameDirectVitals.FOOD_RAW);
                entityGetComponent=null;
            }
            if(entityGetComponent==null || entityGetComponent.getDeclaringClass()!=entity.getClass())
                entityGetComponent=entity.getClass().getMethod("getComponent",Class.class);
            Object component=entityGetComponent.invoke(entity,vitalsType);
            if(component==null)return null;
            double h=hp.getInt(component),hmax=maxHp.getInt(component),m=mp.getInt(component),mmax=maxMp.getInt(component),raw=food.getFloat(component);
            if(!Double.isFinite(raw)||raw<0||raw>5000)return null;
            return new Snapshot(h,hmax,m,mmax,Math.min(60,raw/60),"ECS v0.8.59");
        }catch(Throwable e){return null;}
    }
}
