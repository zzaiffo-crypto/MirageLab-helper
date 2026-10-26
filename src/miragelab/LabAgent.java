package miragelab;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

public final class LabAgent {
    private static final String GAME_INTERNAL =
            "uk/co/harveydogs/mirage/client/NT6CLfh1EFZKAgt882tQ6RNRZLmx";
    private static final String NETWORK_CHILD_INTERNAL =
            "uk/co/harveydogs/mirage/client/ENG5bVtqWkQx2hDa8v6FdQ/cRp2wNrbVttjbgiAAOCCjsdcdE5d";
    // v0.8.59: server -> client PLAYER_EXPERIENCE_GAINED packet payload.
    // This hook is read-only; it runs only when the incoming integer is deserialized.
    private static final String XP_EVENT_INTERNAL =
            "uk/co/harveydogs/mirage/shared/ppGE4fatBnKfcPyX4Kmtne6Lgsjv/yY8x1yOsuoav/fW4YgN0AYKg/nxJX5HQJYGJ";
    private static final String XP_READ_DESC =
            "(Luk/co/harveydogs/mirage/shared/ppGE4fatBnKfcPyX4Kmtne6Lgsjv/hpAMVpXfx64VmGp2;)V";
    private static final String XP_READ_NAME = "eACqBMENic3YHQLgkQTnYXFlcXmJ";
    private static final String XP_GET_NAME = "WVfgbdbcCRRmDq1CfPzsI1qtv";
    private static final String SEND_ACTION_NAME = "eACqBMENic3YHQLgkQTnYXFlcXmJ";
    private static final String SEND_ACTION_DESC =
            "(Luk/co/harveydogs/mirage/shared/ppGE4fatBnKfcPyX4Kmtne6Lgsjv/NT6CLfh1EFZKAgt882tQ6RNRZLmx;)V";

    private LabAgent() {}

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        LabHook.status("MirageLab Helper v10.0.1 production agent loaded");
        HelperConfig.ensureCurrentFile();
        GameOverlay.launch();
        instrumentation.addTransformer(new Transformer(), false);
    }

    private static final class Transformer implements ClassFileTransformer {
        @Override
        public byte[] transform(Module module,
                                ClassLoader loader,
                                String className,
                                Class<?> classBeingRedefined,
                                ProtectionDomain protectionDomain,
                                byte[] classfileBuffer) {
            if (!GAME_INTERNAL.equals(className) && !NETWORK_CHILD_INTERNAL.equals(className) && !XP_EVENT_INTERNAL.equals(className)) {
                return null;
            }

            try {
                ClassReader reader = new ClassReader(classfileBuffer);
                ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
                final boolean[] renderHook = {false};
                final boolean[] actionHook = {false};
                final boolean[] xpHook = {false};

                ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                    @Override
                    public MethodVisitor visitMethod(int access,
                                                     String name,
                                                     String descriptor,
                                                     String signature,
                                                     String[] exceptions) {
                        MethodVisitor base = super.visitMethod(access, name, descriptor, signature, exceptions);

                        if (GAME_INTERNAL.equals(className) && "render".equals(name) && "()V".equals(descriptor)) {
                            renderHook[0] = true;
                            return new MethodVisitor(Opcodes.ASM9, base) {
                                @Override
                                public void visitCode() {
                                    super.visitCode();
                                    super.visitVarInsn(Opcodes.ALOAD, 0);
                                    super.visitMethodInsn(
                                            Opcodes.INVOKESTATIC,
                                            "miragelab/LabHook",
                                            "safeTick",
                                            "(Ljava/lang/Object;)V",
                                            false);
                                }
                            };
                        }

                        // v5.2: observe Actions at the Game boundary itself. This is earlier and
                        // more reliable than relying only on the socket/network wrapper.
                        if (GAME_INTERNAL.equals(className)
                                && SEND_ACTION_NAME.equals(name)
                                && SEND_ACTION_DESC.equals(descriptor)) {
                            actionHook[0] = true;
                            return new MethodVisitor(Opcodes.ASM9, base) {
                                @Override
                                public void visitCode() {
                                    super.visitCode();
                                    super.visitVarInsn(Opcodes.ALOAD, 1);
                                    super.visitMethodInsn(
                                            Opcodes.INVOKESTATIC,
                                            "miragelab/LabHook",
                                            "safeObserveAction",
                                            "(Ljava/lang/Object;)V",
                                            false);
                                }
                            };
                        }

                        // Keep the old network observation as a fallback. Duplicate observations
                        // are filtered by LabHook.
                        if (NETWORK_CHILD_INTERNAL.equals(className)
                                && SEND_ACTION_NAME.equals(name)
                                && SEND_ACTION_DESC.equals(descriptor)) {
                            actionHook[0] = true;
                            return new MethodVisitor(Opcodes.ASM9, base) {
                                @Override
                                public void visitCode() {
                                    super.visitCode();
                                    super.visitVarInsn(Opcodes.ALOAD, 1);
                                    super.visitMethodInsn(
                                            Opcodes.INVOKESTATIC,
                                            "miragelab/LabHook",
                                            "safeObserveAction",
                                            "(Ljava/lang/Object;)V",
                                            false);
                                }
                            };
                        }

                        // Observe the decoded XP event after readInt() has filled its field.
                        // Do not instrument writeInt(), getters, or reset(): they are not earned XP.
                        if (XP_EVENT_INTERNAL.equals(className) && XP_READ_NAME.equals(name)
                                && XP_READ_DESC.equals(descriptor)) {
                            xpHook[0] = true;
                            return new MethodVisitor(Opcodes.ASM9, base) {
                                @Override public void visitInsn(int opcode) {
                                    if (opcode == Opcodes.RETURN) {
                                        super.visitVarInsn(Opcodes.ALOAD, 0);
                                        super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, XP_EVENT_INTERNAL,
                                                XP_GET_NAME, "()I", false);
                                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "miragelab/LabHook",
                                                "recordExperienceEvent", "(I)V", false);
                                    }
                                    super.visitInsn(opcode);
                                }
                            };
                        }
                        return base;
                    }
                };

                reader.accept(visitor, 0);
                if (GAME_INTERNAL.equals(className)) {
                    LabHook.status("v10.0 helper hooks GAME: render=" + renderHook[0] + ", actionSend=" + actionHook[0]);
                } else if(XP_EVENT_INTERNAL.equals(className)) {
                    LabHook.status("v10 XP event hook incoming-read="+xpHook[0]);
                } else {
                    LabHook.status("v10 network fallback: actionSend="+actionHook[0]);
                }
                return writer.toByteArray();
            } catch (Throwable t) {
                LabHook.status("v10.0 helper transform failed for " + className + ": " + t);
                return null;
            }
        }
    }
}
