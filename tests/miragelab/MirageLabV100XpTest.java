package miragelab;
import java.util.Random;
import java.lang.reflect.*;
import jdk.internal.org.objectweb.asm.*;
import java.security.ProtectionDomain;

/** Client event XP regression and bytecode instrumentation smoke. No proprietary game JAR required. */
public final class MirageLabV100XpTest {
    static int checks;
    static void ok(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
    static final String NAME="uk/co/harveydogs/mirage/shared/ppGE4fatBnKfcPyX4Kmtne6Lgsjv/yY8x1yOsuoav/fW4YgN0AYKg/nxJX5HQJYGJ";
    static final String READ_DESC="(Luk/co/harveydogs/mirage/shared/ppGE4fatBnKfcPyX4Kmtne6Lgsjv/hpAMVpXfx64VmGp2;)V";
    static byte[] fakeCommand(){
        ClassWriter w=new ClassWriter(0);w.visit(Opcodes.V17,Opcodes.ACC_PUBLIC,NAME,null,"java/lang/Object",null);
        w.visitField(Opcodes.ACC_PRIVATE,"value","I",null,null).visitEnd();
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
        c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);c.visitInsn(Opcodes.RETURN);c.visitMaxs(1,1);c.visitEnd();
        c=w.visitMethod(Opcodes.ACC_PUBLIC,"WVfgbdbcCRRmDq1CfPzsI1qtv","()I",null,null);
        c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitFieldInsn(Opcodes.GETFIELD,NAME,"value","I");c.visitInsn(Opcodes.IRETURN);c.visitMaxs(1,1);c.visitEnd();
        c=w.visitMethod(Opcodes.ACC_PUBLIC,"eACqBMENic3YHQLgkQTnYXFlcXmJ",READ_DESC,null,null);
        c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitIntInsn(Opcodes.BIPUSH,17);c.visitFieldInsn(Opcodes.PUTFIELD,NAME,"value","I");c.visitInsn(Opcodes.RETURN);c.visitMaxs(2,2);c.visitEnd();
        w.visitEnd();return w.toByteArray();
    }
    static void checkTransform()throws Exception{
        Class<?> t=Class.forName("miragelab.LabAgent$Transformer");Constructor<?> ctor=t.getDeclaredConstructor();ctor.setAccessible(true);
        ClassFileTransformerAdapter trans=new ClassFileTransformerAdapter(ctor.newInstance(),t);
        byte[] raw=fakeCommand(), patched=trans.patch(NAME,raw);
        ok(patched!=null && patched.length>raw.length,"XP read command was transformed");
        int[] hooked={0};new ClassReader(patched).accept(new ClassVisitor(Opcodes.ASM9){
            @Override public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] ex){
                if(!"eACqBMENic3YHQLgkQTnYXFlcXmJ".equals(name)||!READ_DESC.equals(desc))return null;
                return new MethodVisitor(Opcodes.ASM9){
                    @Override public void visitMethodInsn(int op,String owner,String nm,String descriptor,boolean itf){
                        if(owner.equals("miragelab/LabHook") && nm.equals("recordExperienceEvent"))hooked[0]++;
                    }
                };
            }
        },0);
        ok(hooked[0]==1,"exactly one XP event interception");
        ok(trans.patch("unrelated/Game",raw)==null,"unrelated classes untouched");
    }
    static final class ClassFileTransformerAdapter {
        final Object obj;final Method transform;
        ClassFileTransformerAdapter(Object o,Class<?> cls)throws Exception{
            obj=o;transform=cls.getDeclaredMethod("transform",Module.class,ClassLoader.class,String.class,Class.class,ProtectionDomain.class,byte[].class);
            transform.setAccessible(true);
        }
        byte[] patch(String name,byte[] bytes)throws Exception{
            return (byte[])transform.invoke(obj,null,MirageLabV100XpTest.class.getClassLoader(),name,null,null,bytes);
        }
    }
    public static void main(String[] args)throws Exception{
        AnalyzerEngine.reset();long expected=0;Random r=new Random(100);
        for(int i=0;i<10000;i++){
            int n=r.nextInt(600)+1;AnalyzerEngine.gainedXpEvent(n);expected+=n;
            if(i%19==0){var s=AnalyzerEngine.snapshot(System.currentTimeMillis());
                ok(s.gainedXp()!=null && s.gainedXp()==expected,"XP delta totals");
                ok(s.xpPerHour()!=null && s.xpPerHour()>0,"XP rate populated");
                ok(s.xpEvents()==i+1,"XP event count");}
        }
        AnalyzerEngine.gainedXpEvent(-7);AnalyzerEngine.gainedXpEvent(0);AnalyzerEngine.gainedXpEvent(100_000_001);
        ok(AnalyzerEngine.snapshot(System.currentTimeMillis()).gainedXp()==expected,"invalid deltas discarded");
        AnalyzerEngine.reset();var s=AnalyzerEngine.snapshot(System.currentTimeMillis());
        ok(s.xpPerHour()==null && s.gainedXp()==null && s.xpEvents()==0,"reset isolates session");
        checkTransform();System.out.println("V10 XP event/hook PASS "+checks+" checks");
    }
}
