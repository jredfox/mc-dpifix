package jredfox;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.lwjgl.opengl.Display;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
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
		if(DpiFix.fsSaveFix)
		{
			InsnList l = new InsnList();
			l.add(new LabelNode());
			l.add(newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixTransformer", "syncFullScreen", "()V", false));
			m.instructions.insert(l);
		}
		
		//fix MC-160054 tabbing out or showing desktop results in minimized MC < 1.8
		if(DpiFix.fsTabFix)
		{
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
		}
		
		/*
		 * Fixes MC-68754
		 * if(!this.fullscreen)
		 * {
		 * 		if(DpiFix.isWindows)
		 * 			Display.setResizable(false);
		 * 		Display.setResizable(true);
		 * }
		 */
		if(DpiFix.fsResizeableFix)
		{
			//Disable all instances of Forge's / Optifine's Fullscreen "Fix"
			MethodInsnNode startResize = getMethodInsnNode(m, Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "setResizable", "(Z)V", false);
			if(startResize != null)
			{
				System.err.println("Disabling Forge's \"FIX\" for Fullscreen!");
				MethodInsnNode resizeInsn = newMethodInsnNode(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "setResizable", "(Z)V", false);
				AbstractInsnNode ab = startResize;
				while(ab != null)
				{
					if(ab instanceof MethodInsnNode && equals(resizeInsn, (MethodInsnNode) ab))
					{
						MethodInsnNode minsn = (MethodInsnNode)ab;
						minsn.owner = "jredfox/DpiFixTransformer";
					}
					ab = ab.getNext();
				}
			}
			
			InsnList li = new InsnList();
			li.add(new VarInsnNode(Opcodes.ALOAD, 0));
			li.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/client/Minecraft", getObfString("fullscreen", "field_71431_Q").toString(), "Z"));
			LabelNode l26 = new LabelNode();
			li.add(new JumpInsnNode(Opcodes.IFNE, l26));
			if(DpiFix.isWindows)
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
		
		//DpiFixTransformer.patchSplash(this.mcDataDir);
		if(DpiFix.fsSplashFix && ForgeVersion.getMajorVersion() >= 10 && ForgeVersion.getBuildVersion() >= 1389)
		{
			MethodNode in = getMethodNode(classNode, getObfString("init", "func_71384_a"), "()V");
			if(in == null)
				in = getMethodNode(classNode, "startGame", "()V");
			
			InsnList ilist = new InsnList();
			ilist.add(new VarInsnNode(Opcodes.ALOAD, 0));
			ilist.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/client/Minecraft", getObfString("mcDataDir", "field_71412_D"), "Ljava/io/File;"));
			ilist.add(newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixTransformer", "patchSplash", "(Ljava/io/File;)V", false));
			in.instructions.insert(nextLabelNode(in.instructions.getFirst()), ilist);
		}
		
		if(DpiFix.isLinux ? DpiFix.fsMouseFixLinux : DpiFix.fsMouseFixOther)
		{
			//make leftClickCounter public without Universal At from 1.6 - 1.12.2
			for(FieldNode f : classNode.fields)
			{
				if(f.name.equals(getObfString("leftClickCounter", "field_71429_W")))
				{
					f.access = Opcodes.ACC_PUBLIC;
				}
			}
			
			/**
			 * DpiFixTransformer.fsMousePre(this);
			 */
			MethodInsnNode vsync = getMethodInsnNode(m, Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "setVSyncEnabled", "(Z)V", false);
			InsnList fspre = new InsnList();
			fspre.add(new VarInsnNode(Opcodes.ALOAD, 0));
			fspre.add(newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixTransformer", "fsMousePre", "(Lnet/minecraft/client/Minecraft;)V", false));
			m.instructions.insert(vsync, fspre);
			
			/**
			 * DpiFixTransformer.fsMousePost(this);
			 */
			MethodNode runGame = getMethodNode(classNode, getObfString("runGameLoop", onesixnotch ? "S" : "func_71411_J"), "()V");
			InsnList fspost = new InsnList();
			fspost.add(new VarInsnNode(Opcodes.ALOAD, 0));
			fspost.add(newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixTransformer", "fsMousePost", "(Lnet/minecraft/client/Minecraft;)V", false));
			AbstractInsnNode spot = getLastMethodInsn(runGame, Opcodes.INVOKEVIRTUAL, onesixnotch ? (ForgeVersion.getMinorVersion() == 11 ? "lv" : ForgeVersion.getMinorVersion() == 10 ? "lu" : "ls") : "net/minecraft/profiler/Profiler", getObfString("endSection", onesixnotch ? "b" : "func_76319_b"), "()V", false);
			runGame.instructions.insert(spot, fspost);
		}
	}
	
	/**
	 * getting the first instanceof of this will usually tell you where the initial injection point should be after
	 */
	public static LineNumberNode getFirstInstruction(MethodNode method) 
	{
		for(AbstractInsnNode obj : method.instructions.toArray())
			if(obj instanceof LineNumberNode)
				return (LineNumberNode) obj;
		return null;
	}
	
	public static MethodInsnNode getLastMethodInsn(MethodNode node, int opcode, String owner, String name, String desc, boolean isInterface) 
	{
		MethodInsnNode compare = newMethodInsnNode(opcode,owner,name,desc,isInterface);
		AbstractInsnNode[] list = node.instructions.toArray();
		for(int i=list.length-1;i>=0;i--)
		{
			AbstractInsnNode ab = list[i];
			if(ab.getOpcode() == opcode && ab instanceof MethodInsnNode && equals(compare, (MethodInsnNode)ab) )
			{
				return (MethodInsnNode)ab;
			}
		}
		return null;
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
	
	public static LineNumberNode nextLabelNode(AbstractInsnNode spot) 
	{
		AbstractInsnNode n = spot;
		while(n != null)
		{
			n = n.getNext();
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
	
	//##############################  START Functions  ##############################\\
	//Minecraft
	
	public static boolean rfalse() { return false; }
	
	/**
	 * Dummy Method to stop forge from breaking on macOS
	 */
	public static void setResizable(boolean resizeable) {}
	
	/**
	 * called right before the fullscreen boolean gets toggled
	 */
	public static void syncFullScreen()
	{
		Minecraft mc = Minecraft.getMinecraft();
		mc.gameSettings.fullScreen = !mc.isFullScreen();//1.6.4 doesn't have AT's so use the getter method and hope it's not overriden
		mc.gameSettings.saveOptions();
	}

	public static void patchSplash(File mcDataDir)
	{
		if(DpiFix.isMacOs && !(new File(mcDataDir, "config/splash.properties.patched").exists()))
		{
			//create configuration dir
			File cfgdir = new File(mcDataDir, "config");
			if(!cfgdir.exists())
				cfgdir.mkdirs();
			
			List<String> li = new ArrayList();
			String nl = System.lineSeparator();
			li.add("#Splash screen properties" + nl +
					"background=0xFFFFFF" + nl +
					"memoryGood=0x78CB34\r\n" + nl +
					"font=0x0\r\n" + nl +
					"barBackground=0xFFFFFF" + nl +
					"barBorder=0xC0C0C0" + nl +
					"memoryLow=0xE42F2F" + nl +
					"rotate=false" + nl +
					"memoryWarn=0xE6E84A" + nl +
					"showMemory=true" + nl +
					"bar=0xCB3D35" + nl +
					"enabled=false" + nl +
					"resourcePackPath=resources" + nl +
					"logoOffset=0" + nl +
					"forgeTexture=fml\\:textures/gui/forge.png" + nl +
					"fontTexture=textures/font/ascii.png"
					);
			
			DpiFix.saveFileLines(li, new File(cfgdir, "splash.properties"));
			
			try
			{
				new File(cfgdir, "splash.properties.patched").createNewFile();
			}
			catch (Exception e) 
			{
				e.printStackTrace();
			}
		}
	}

	public static volatile Object lock = new Object();
	public static volatile boolean mouseFlag;
	public static void fsMousePre(Minecraft mc) 
	{
		synchronized (lock) 
		{
	        if(mc.inGameHasFocus)
	        {
	        	DpiFixProxy.setIngameNotInFocus(mc);
	        	mouseFlag = true;
	        }
		}
	}

	public static void fsMousePost(Minecraft mc) 
	{
		if(mouseFlag)
		{
			synchronized (lock)
			{
		        if(mouseFlag && Display.isActive())
		        {
		        	DpiFixProxy.setIngameFocus(mc);
		        	mouseFlag = false;
		        }
			}
		}
	}
	
	//##############################  End Functions  ##############################\\
	

}
