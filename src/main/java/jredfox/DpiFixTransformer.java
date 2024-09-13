package jredfox;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import net.minecraft.client.Minecraft;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraftforge.common.ForgeVersion;

public class DpiFixTransformer implements IClassTransformer {
	
	/**
	 * check if notch names should be used without loading any minecraft classes
	 */
	public static boolean onesixnotch = ForgeVersion.getMajorVersion() < 9 || ForgeVersion.getMajorVersion() == 9 && ForgeVersion.getMinorVersion() <= 11 && ForgeVersion.getBuildVersion() < 937;
	
	@Override
	public byte[] transform(String name, String transformedName, byte[] basicClass) 
	{
		if(transformedName.equals("net.minecraft.client.Minecraft"))
		{
			try
			{
				System.out.println("Transforming: Patching Minecraft Fullscreen to fix MC-68754, MC-111419, MC-160054");
				ClassNode classNode = new ClassNode();
	            ClassReader classReader = new ClassReader(basicClass);
	            classReader.accept(classNode, 0);
	            
	            patchFullScreen(name, classNode);
	            
	            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
	            classNode.accept(classWriter);
	            
	            byte[] bytes = classWriter.toByteArray();
	            if(Boolean.parseBoolean(System.getProperty("asm.dump", "false")))
	            	dumpFile(transformedName, bytes);
	            
	            return bytes;
			}
			catch(Throwable t)
			{
				t.printStackTrace();
			}
		}
		return basicClass;
	}
	
	/**
	 * patches fullscreen for versions of forge that hasn't patched in in 1.12.2
	 */
	public static void patchFullScreen(String notch_mc, ClassNode classNode) 
	{
		//fix MC-111419 by injecting DpiFixTransformer#syncFullScreen
		String toggleFullscreen = getObfString("toggleFullscreen", onesixnotch ? "j" : "func_71352_k");
		MethodNode m = getMethodNode(classNode, toggleFullscreen, "()V");
		InsnList l = new InsnList();
		l.add(new LabelNode());
		l.add(newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixTransformer", "syncFullScreen", "()V", false));
		m.instructions.insert(l);
		
		//fix MC-160054 tabbing out or showing desktop results in minimized MC < 1.8
		MethodNode m2 = getMethodNode(classNode, getObfString("runGameLoop", onesixnotch ? "S" : "func_71411_J"), "()V");
		if(getMethodInsnNode(m2, Opcodes.INVOKEVIRTUAL, onesixnotch ? notch_mc : "net/minecraft/client/Minecraft", toggleFullscreen, "()V", false) != null)
		{
			MethodInsnNode spot = getMethodInsnNode(m2, Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "isActive", "()Z", false);
			if(spot != null)
			{
				JumpInsnNode jump = nextJumpInsnNode(spot);
				if(jump != null)
				{
					InsnList list = new InsnList();
					list.add(newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixTransformer", "rfalse", "()Z", false));
					list.add(new JumpInsnNode(Opcodes.IFEQ, jump.label));
					m2.instructions.insert(prevLabelNode(spot), list);
				}
			}
		}
		
		/*
		 * Fixes MC-68754
		 * if(!this.fullscreen)
		 * {
		 * 		if(!isMac)
		 * 			Display.setResizable(false);
		 * 		Display.setResizable(true);
		 * }
		 */
		//detect if it's already been patched by forge and patch their patch if on macOS
		MethodInsnNode startResize = getMethodInsnNode(m, Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "setResizable", "(Z)V", false);
		if(startResize != null)
		{
			System.err.println("FullScreen Already Patched!");
			if(DpiFix.isMacOs) 
			{
				System.err.println("Patching Forge's \"FIX\" for macOS");
				startResize.owner = "jredfox/DpiFixTransformer";
			}
			return;
		}
		
		InsnList li = new InsnList();
		li.add(new VarInsnNode(Opcodes.ALOAD, 0));
		li.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/client/Minecraft", getObfString("fullscreen", "field_71431_Q").toString(), "Z"));
		LabelNode l26 = new LabelNode();
		li.add(new JumpInsnNode(Opcodes.IFNE, l26));
		if(!DpiFix.isMacOs)
		{
			LabelNode l27 = new LabelNode();
			li.add(l27);
			li.add(new InsnNode(Opcodes.ICONST_0));
			li.add(newMethodInsnNode(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "setResizable", "(Z)V", false));
		}
		LabelNode l28 = new LabelNode();
		li.add(l28);
		li.add(new InsnNode(Opcodes.ICONST_1));
		li.add(newMethodInsnNode(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "setResizable", "(Z)V", false));
		li.add(l26);
		//since we can't compute frames for mc 1.6.4 manually add the frame
		li.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
		
		m.instructions.insert(getMethodInsnNode(m, Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "setFullscreen", "(Z)V", false), li);
		
	}

	public static LineNumberNode prevLabelNode(AbstractInsnNode spot) 
	{
		AbstractInsnNode n = spot;
		while(n != null)
		{
			n = n.getPrevious();
			if(n instanceof LineNumberNode)
				return (LineNumberNode) n;
		}
		return null;
	}

	public static JumpInsnNode nextJumpInsnNode(AbstractInsnNode spot)
	{
		AbstractInsnNode n = spot;
		while(n != null)
		{
			n = n.getNext();
			if(n instanceof JumpInsnNode)
				return (JumpInsnNode) n;
		}
		return null;
	}

	public static MethodInsnNode newMethodInsnNode(int opcode, String owner, String name, String desc, boolean itf) 
	{
		MethodInsnNode insn = new MethodInsnNode(opcode, owner, name, desc);
		return insn;
	}

	public static String getObfString(String deob, String ob)
	{
		return DpiFix.isObf ? ob : deob;
	}
	
	public static MethodInsnNode getMethodInsnNode(MethodNode node, int opcode, String owner, String name, String desc, boolean itf)
	{
		AbstractInsnNode[] arr = node.instructions.toArray();
		MethodInsnNode compare = newMethodInsnNode(opcode, owner, name, desc, itf);
		for(AbstractInsnNode ab : arr)
		{
			if(ab instanceof MethodInsnNode)
			{
				if(equals(compare, (MethodInsnNode)ab))
				{
					return (MethodInsnNode)ab;
				}
			}
		}
		return null;
	}
	
	public static boolean equals(MethodInsnNode obj1, MethodInsnNode obj2)
	{
		return obj1.getOpcode() == obj2.getOpcode() && obj1.name.equals(obj2.name) && obj1.desc.equals(obj2.desc) && obj1.owner.equals(obj2.owner);
	}
	
	public static MethodNode getMethodNode(ClassNode classNode, String method_name, String method_desc) 
	{
		for (Object method_ : classNode.methods)
		{
			MethodNode method = (MethodNode) method_;
			if (method.name.equals(method_name) && method.desc.equals(method_desc))
			{
				return method;
			}
		}
		return null;
	}
	
	/**
	 * dumps a file from memory
	 * @throws IOException 
	 */
	public static void dumpFile(String name, byte[] bytes) throws IOException  
	{
    	name = name.replace('.', '/');
    	File f = new File(System.getProperty("user.dir") + "/asm/dumps/dpi-fix/" + name + ".class");
    	f.getParentFile().mkdirs();
    	FileUtils.writeByteArrayToFile(f, bytes);
	}

	/**
	 * called right before the fullscreen boolean gets toggled
	 */
	public static void syncFullScreen()
	{
		Minecraft mc = Minecraft.getMinecraft();
		mc.gameSettings.fullScreen = !mc.isFullScreen();//1.6.4 doesn't have AT's so use the getter method and hope it's not overriden
		mc.gameSettings.saveOptions();
	}

	public static boolean rfalse() 
	{
		return false;
	}

	/**
	 * Dummy Method to stop forge from breaking on macOS
	 */
	public static void setResizable(boolean resizeable) {}
	

}
