package miragelab;

import java.lang.reflect.*;
import java.util.*;
/** Alert-only proximity monitoring. Does not infer PK from mere player presence. */
final class SecurityMonitor {
    private static final String WORLD="P17VtM6bkJVXJB65ftLh7dMon", GET_PLAYER="eACqBMENic3YHQLgkQTnYXFlcXmJ";
    private static final String IDENTITY="uk.co.harveydogs.mirage.shared.yY8x1yOsuoav.D4yZmsobaWAL";
    private static long nextAt;
    private static volatile String warning="Seguranca: aguardando jogo";
    static String summary(){return warning;}
    static void tick(Object game,long now){
        if(now<nextAt||game==null)return;nextAt=now+1000L;
        try {
            Object world=game.getClass().getMethod(WORLD).invoke(game);
            if(world==null){warning="Seguranca: mundo ausente";return;}
            Object player=world.getClass().getMethod(GET_PLAYER).invoke(world);
            if(player==null){warning="Seguranca: jogador ausente";return;}
            // Identity and position are read using the same component classes as the existing CaveBot.
            ClassLoader loader=game.getClass().getClassLoader();
            Class<?> idClazz=Class.forName(IDENTITY,false,loader);
            Class<?> posClazz=Class.forName("uk.co.harveydogs.mirage.shared.yY8x1yOsuoav.CzFlqirRObv",false,loader);
            // If a client build uses a different component class, simply report unsupported.
            Object items=world.getClass().getMethod("getEntities").invoke(world);
            if(!(items instanceof Iterable<?> entities)){warning="Seguranca: entidades indisponiveis";return;}
            int players=0;
            Method read=player.getClass().getMethod("getComponent",Class.class);
            Object playerPos=read.invoke(player,posClazz);
            if(playerPos==null){warning="Seguranca: posicao desconhecida";return;}
            int px=posClazz.getField("eACqBMENic3YHQLgkQTnYXFlcXmJ").getInt(playerPos);
            int py=posClazz.getField("FIeR9ZxfSOyX").getInt(playerPos);
            for(Object entity:entities){
                if(entity==player)continue;
                Object v=read.invoke(entity,idClazz);
                if(v==null)continue;
                Object type=idClazz.getField("eACqBMENic3YHQLgkQTnYXFlcXmJ").get(v);
                if(type==null || !"PLAYER".equals(type instanceof Enum<?> en?en.name():type.toString()))continue;
                Object ep=read.invoke(entity,posClazz);if(ep==null)continue;
                int ex=posClazz.getField("eACqBMENic3YHQLgkQTnYXFlcXmJ").getInt(ep);
                int ey=posClazz.getField("FIeR9ZxfSOyX").getInt(ep);
                if(Math.max(Math.abs(ex-px),Math.abs(ey-py))<=8)players++;
            }
            warning=players>0?"AVISO: "+players+" jogadores a 8 tiles (PK nao confirmado)":"Seguranca: sem jogadores identificados";
        }catch(Throwable e){warning="Seguranca: leitura indisponivel";}
    }
}
