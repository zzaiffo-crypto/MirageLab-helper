import java.io.*;
import java.lang.reflect.*;
/** Optional integration exercise; classpath must include legally obtained Obfuscated.jar. */
public final class RealXpCommandProbe {
 public static void main(String[] args) throws Exception {
  String prefix="uk.co.harveydogs.mirage.shared.ppGE4fatBnKfcPyX4Kmtne6Lgsjv.";
  String xpName=prefix+"yY8x1yOsuoav.fW4YgN0AYKg.nxJX5HQJYGJ";
  Class<?> packet=Class.forName(xpName);
  Object instance=packet.getConstructor().newInstance();
  Class<?> input=Class.forName(prefix+"hpAMVpXfx64VmGp2");
  Constructor<?> c=input.getConstructors()[0];
  Object reader=c.newInstance(new ByteArrayInputStream(new byte[]{0,0,1,10}),null);
  packet.getMethod("eACqBMENic3YHQLgkQTnYXFlcXmJ",input).invoke(instance,reader);
  int delta=(int)packet.getMethod("WVfgbdbcCRRmDq1CfPzsI1qtv").invoke(instance);
  if(delta!=266)throw new AssertionError("XP payload ="+delta);
  Class<?> analyzer=Class.forName("miragelab.AnalyzerEngine");
  Method snap=analyzer.getDeclaredMethod("snapshot",long.class);snap.setAccessible(true);
  Object stats=snap.invoke(null,System.currentTimeMillis());
  Method xp=stats.getClass().getDeclaredMethod("gainedXp");xp.setAccessible(true);
  long got=((Number)xp.invoke(stats)).longValue();
  if(got!=266L)throw new AssertionError("bridge did not record XP; got="+got);
  System.out.println("REAL GAME XP DESERIALIZATION PASS delta="+delta+" analyzer="+got);
  System.exit(0);
 }
}
