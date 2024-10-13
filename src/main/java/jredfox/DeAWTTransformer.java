package jredfox;

import java.awt.Component;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import jml.gamemodelib.GameModeLibAgent;

/**
 * Temporarily Disables all java.awt.* To Prevent Flashy frames Until Minecraft#startGame gets called
 * @author jredfox
 * @credit MoreStack for suggesting editing {@link java.awt.Component#setVisible(boolean)} directly instead of whitelisting 50 classes
 */
public class DeAWTTransformer implements ClassFileTransformer {

	public byte[] component;
	public static final String DEAWT_VERSION = "0.1";//TODO: make part of the filename
	
	@Override
	public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classBytes) 
	{
		if(className == null)
			return classBytes;
		
		if(className.equals("java/awt/Component"))
		{
			if(component != null)
				return component;
			try
			{
				System.out.println("Transforming java.awt.Component to prevent flashes");
				ClassNode classNode = CoreUtils.getClassNode(classBytes);
				
				//add the field canSetVisible
				classNode.fields.add(new FieldNode(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "canSetVisible", "Z", null, null));
				
				MethodNode m = CoreUtils.getMethodNode(classNode, "setVisible", "(Z)V");
				InsnList l = new InsnList();
				//Print debug
				if(GameModeLibAgent.debug)
				{
					l.add(new FieldInsnNode(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;"));
					l.add(new VarInsnNode(Opcodes.ALOAD, 0));
					l.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;"));
					l.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;", false));
					l.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false));
				}
				
				LabelNode l1 = new LabelNode();
				l.add(new VarInsnNode(Opcodes.ILOAD, 1));
				l.add(new JumpInsnNode(Opcodes.IFEQ, l1));
				
				l.add(new FieldInsnNode(Opcodes.GETSTATIC, "java/awt/Component", "canSetVisible", "Z"));
				l.add(new JumpInsnNode(Opcodes.IFNE, l1));
				
				l.add(new VarInsnNode(Opcodes.ALOAD, 0));
				l.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;"));
				l.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;"));
				l.add(new LdcInsnNode("cpw.mods.fml"));
				l.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "startsWith", "(Ljava/lang/String;)Z"));
				l.add(new JumpInsnNode(Opcodes.IFNE, l1));

				l.add(new VarInsnNode(Opcodes.ALOAD, 0));
				l.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;"));
				l.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;", false));
				l.add(new LdcInsnNode("javax.swing.JDialog"));
				l.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "startsWith", "(Ljava/lang/String;)Z", false));
				l.add(new JumpInsnNode(Opcodes.IFNE, l1));
				
				LabelNode l2 = new LabelNode();
				l.add(l2);
				l.add(new InsnNode(Opcodes.RETURN));
				l.add(l1);
				l.add(new LabelNode());
				l.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
				
				m.instructions.insert(l);
				this.component = CoreUtils.toByteArray(CoreUtils.getClassWriter(classNode, ClassWriter.COMPUTE_MAXS), className);
				return this.component;
			}
			catch(Throwable t)
			{
				t.printStackTrace();
			}
		}
		
		return classBytes;
	}

}
