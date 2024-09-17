package jredfox;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
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
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraftforge.common.ForgeVersion;

public class DpiFixCoreMod implements IClassTransformer {
	
	/**
	 * check if notch names should be used without loading any minecraft classes
	 */
	public static boolean onesixnotch = ForgeVersion.getMajorVersion() < 9 || ForgeVersion.getMajorVersion() == 9 && ForgeVersion.getMinorVersion() <= 11 && ForgeVersion.getBuildVersion() < 937;
	
	@Override
	public byte[] transform(String name, String transformedName, byte[] basicClass) 
	{
		boolean mc = transformedName.equals("net.minecraft.client.Minecraft");
		if(mc || transformedName.equals("net.minecraft.client.gui.LoadingScreenRenderer"))
		{
			try
			{
				ClassNode classNode = new ClassNode();
	            ClassReader classReader = new ClassReader(basicClass);
	            classReader.accept(classNode, 0);
	            
	            if(mc)
	            {
	            	System.out.println("Patching: Minecraft Fullscreen to fix MC-68754, MC-111419, MC-160054");
	            	patchFullScreen(name.replace(".", "/"), classNode);
	            	patchMaxResFix( name.replace(".", "/"), classNode);
	            }
	            else
	            {
	            	patchLoadingScreenRenderer(name, classNode);
	            }
	            
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
		//Universal AT 1.6 - 1.12.2
		String notch_leftClickCounter = (ForgeVersion.getMinorVersion() == 11 ? "W" : ForgeVersion.getMinorVersion() == 10 ? "W" : "V");
		String notch_fullscreen = (ForgeVersion.getMinorVersion() == 11 ? "P" : ForgeVersion.getMinorVersion() == 10 ? "P" : "O");
		for(FieldNode f : classNode.fields)
		{
			if(f.name.equals(getObfString("leftClickCounter", onesixnotch ? notch_leftClickCounter : "field_71429_W")) || f.name.equals(getObfString("fullscreen", onesixnotch ? notch_fullscreen : "field_71431_Q")) )
			{
				f.access = Opcodes.ACC_PUBLIC;
			}
		}
		
		//fix MC-111419 by injecting DpiFixCoreMod#syncFullScreen
		String toggleFullscreen = getObfString("toggleFullscreen", onesixnotch ? "j" : "func_71352_k");
		MethodNode m = getMethodNode(classNode, toggleFullscreen, "()V");
		if(DpiFix.fsSaveFix)
		{
			InsnList l = new InsnList();
			l.add(new LabelNode());
			l.add(newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixCoreMod", "syncFullScreen", "()V", false));
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
						list.add(newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixCoreMod", "rfalse", "()Z", false));
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
						minsn.owner = "jredfox/DpiFixCoreMod";
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
		
		//DpiFixCoreMod.patchSplash(this.mcDataDir);
		if(DpiFix.fsSplashFix && ForgeVersion.getMajorVersion() >= 10 && ForgeVersion.getBuildVersion() >= 1389)
		{
			MethodNode in = getMethodNode(classNode, getObfString("init", "func_71384_a"), "()V");
			if(in == null)
				in = getMethodNode(classNode, "startGame", "()V");
			
			InsnList ilist = new InsnList();
			ilist.add(new VarInsnNode(Opcodes.ALOAD, 0));
			ilist.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/client/Minecraft", getObfString("mcDataDir", "field_71412_D"), "Ljava/io/File;"));
			ilist.add(newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixCoreMod", "patchSplash", "(Ljava/io/File;)V", false));
			in.instructions.insert(nextLabelNode(in.instructions.getFirst()), ilist);
		}
		
		if(DpiFix.isLinux ? DpiFix.fsMouseFixLinux : DpiFix.fsMouseFixOther)
		{	
			/**
			 * DpiFixCoreMod.fsMousePre(this);
			 */
			MethodInsnNode vsync = getMethodInsnNode(m, Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "setVSyncEnabled", "(Z)V", false);
			InsnList fspre = new InsnList();
			fspre.add(new VarInsnNode(Opcodes.ALOAD, 0));
			fspre.add(newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixCoreMod", "fsMousePre", "(Lnet/minecraft/client/Minecraft;)V", false));
			m.instructions.insert(vsync, fspre);
			
			/**
			 * DpiFixCoreMod.fsMousePost(this);
			 */
			MethodNode runGame = getMethodNode(classNode, getObfString("runGameLoop", onesixnotch ? "S" : "func_71411_J"), "()V");
			InsnList fspost = new InsnList();
			fspost.add(new VarInsnNode(Opcodes.ALOAD, 0));
			fspost.add(newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixCoreMod", "fsMousePost", "(Lnet/minecraft/client/Minecraft;)V", false));
			AbstractInsnNode spot = getLastMethodInsn(runGame, Opcodes.INVOKEVIRTUAL, onesixnotch ? (ForgeVersion.getMinorVersion() == 11 ? "lv" : ForgeVersion.getMinorVersion() == 10 ? "lu" : "ls") : "net/minecraft/profiler/Profiler", getObfString("endSection", onesixnotch ? "b" : "func_76319_b"), "()V", false);
			runGame.instructions.insert(spot, fspost);
		}
	}
	
	public void patchMaxResFix(String notch_mc, ClassNode classNode) 
	{
		if(!DpiFix.maximizeFix || ForgeVersion.getMajorVersion() >= 10)
			return;
		
		String mcClass = onesixnotch ? notch_mc : "net/minecraft/client/Minecraft";
		
		//DpiFixCoreMod#tickDisplay(this);
		MethodNode loadscreen = getMethodNode(classNode, getObfString("loadScreen", onesixnotch ? "R" : "func_71357_I"), "()V");
		MethodInsnNode updateInsn = getLastMethodInsn(loadscreen, Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "update", "()V", false);
		disableDisplayUpdate(loadscreen);
		InsnList lslist = new InsnList();
		lslist.add(new VarInsnNode(Opcodes.ALOAD, 0));
		lslist.add(newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixCoreMod", "tickDisplay", "(Lnet/minecraft/client/Minecraft;)V", false));
		loadscreen.instructions.insert(getLastLabelNode(loadscreen, false), lslist);
		
		//Disable buggy calls of Display#update
		//DpiFixCoreMod#tickDisplay(this);
		MethodNode runGame = getMethodNode(classNode, getObfString("runGameLoop", onesixnotch ? "S" : "func_71411_J"), "()V");
		MethodInsnNode keyInsn = newMethodInsnNode(Opcodes.INVOKESTATIC, "org/lwjgl/input/Keyboard", "isKeyDown", "(I)Z", false);
		MethodInsnNode resizeInsn = newMethodInsnNode(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "wasResized", "()Z", false);
		MethodInsnNode yeildInsn = newMethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Thread", "yield", "()V", false);
		boolean hasYeild = false;
		AbstractInsnNode ab = runGame.instructions.getFirst();
		while(ab != null)
		{
			if(ab instanceof IntInsnNode && ((IntInsnNode)ab).operand == 65 && ab.getNext() instanceof MethodInsnNode)
			{
				MethodInsnNode minsn = (MethodInsnNode) ab.getNext();
				if(equals(keyInsn, minsn))
				{
					MethodInsnNode displayInsn = nextMethodInsnNode(minsn, Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "update", "()V", false);
					displayInsn.owner = "jredfox/DpiFixCoreMod";
					displayInsn.name = "disabled";
				}
			}
			else if(ab instanceof MethodInsnNode)
			{
				MethodInsnNode minsn = (MethodInsnNode) ab;
				if(equals(resizeInsn, minsn))
				{
					minsn.owner = "jredfox/DpiFixCoreMod";
					minsn.name = "rfalse";
				}
				else if(!hasYeild && equals(yeildInsn, minsn))
				{
					hasYeild = true;//only insert 1 tickDisplay
					InsnList li = new InsnList();
					li.add(new VarInsnNode(Opcodes.ALOAD, 0));
					li.add(newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixCoreMod", "tickDisplay", "(Lnet/minecraft/client/Minecraft;)V", false));
					runGame.instructions.insertBefore(minsn, li);
				}
			}
			ab = ab.getNext();
		}
		
		MethodNode fullscreen = getMethodNode(classNode, getObfString("toggleFullscreen", onesixnotch ? "j" : "func_71352_k"), "()V");
		disableDisplayUpdate(fullscreen);//Disable all calls of Display#update
		//DpiFixCoreMod#tickDisplay(this);
		InsnList fsli = new InsnList();
		fsli.add(new VarInsnNode(Opcodes.ALOAD, 0));
		fsli.add(newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixCoreMod", "tickDisplay", "(Lnet/minecraft/client/Minecraft;)V", false));
		fullscreen.instructions.insert(getLastMethodInsn(fullscreen, Opcodes.INVOKESTATIC, "jredfox/DpiFixCoreMod", "disabled", "()V", false), fsli);
		
		MethodNode resize = getMethodNode(classNode, getObfString("resize", onesixnotch ? "a" : "func_71370_a"), "(II)V");
		resize.access = Opcodes.ACC_PUBLIC;//Make the method public
		
		//DpiFixCoreMod#updateViewPort
		InsnList viewport = new InsnList();
		viewport.add(new VarInsnNode(Opcodes.ALOAD, 0));
		viewport.add(newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixCoreMod", "updateViewPort", "(Lnet/minecraft/client/Minecraft;)V", false));
		resize.instructions.insert(getFieldInsnNode(resize, Opcodes.PUTFIELD, mcClass, getObfString("displayHeight", onesixnotch ? (ForgeVersion.getMinorVersion() == 11 ? "e" : ForgeVersion.getMinorVersion() == 10 ? "e" : "d") : "field_71440_d"), "I"), viewport);
		
		//this.loadingScreen = new LoadingScreenRenderer(this);
		InsnList resizeList = new InsnList();
		resizeList.add(new LabelNode());
		resizeList.add(new VarInsnNode(Opcodes.ALOAD, 0));
		resizeList.add(new TypeInsnNode(Opcodes.NEW, "net/minecraft/client/gui/LoadingScreenRenderer"));
		resizeList.add(new InsnNode(Opcodes.DUP));
		resizeList.add(new VarInsnNode(Opcodes.ALOAD, 0));
		resizeList.add(newMethodInsnNode(Opcodes.INVOKESPECIAL, "net/minecraft/client/gui/LoadingScreenRenderer", "<init>", "(Lnet/minecraft/client/Minecraft;)V", false));
		resizeList.add(new FieldInsnNode(Opcodes.PUTFIELD, "net/minecraft/client/Minecraft", getObfString("loadingScreen", "field_71461_s"), "Lnet/minecraft/client/gui/LoadingScreenRenderer;"));
		resize.instructions.insertBefore(getLastInstruction(resize, Opcodes.RETURN), resizeList);
		
	}
	
	public void patchLoadingScreenRenderer(String name, ClassNode classNode)
	{
		if(!DpiFix.maximizeFix || ForgeVersion.getMajorVersion() >= 10)
			return;
		
		System.out.println("Patching: LoadingScreenRenderer");
		
		String cname = onesixnotch ? name : "net/minecraft/client/gui/LoadingScreenRenderer";
		MethodNode m = getMethodNode(classNode, getObfString("setLoadingProgress", onesixnotch ? "a" : "func_73718_a"), "(I)V");
		
		//Disable all Display#update calls
		disableDisplayUpdate(m);
		
		//DpiFixCoreMod.tickDisplay(this.mc);
		InsnList li = new InsnList();
		li.add(new VarInsnNode(Opcodes.ALOAD, 0));
		li.add(new FieldInsnNode(Opcodes.GETFIELD, cname, getObfString("mc", "field_73725_b"), "Lnet/minecraft/client/Minecraft;"));
		li.add(newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixCoreMod", "tickDisplay", "(Lnet/minecraft/client/Minecraft;)V", false));
		m.instructions.insertBefore(getLastMethodInsn(m, Opcodes.INVOKESTATIC, "java/lang/Thread", "yield", "()V", false), li);
	}
	
	private void disableDisplayUpdate(MethodNode m)
	{
		//Disable all calls of Display#update
		MethodInsnNode updateInsn = newMethodInsnNode(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "update", "()V", false);
		AbstractInsnNode ab = m.instructions.getFirst();
		while(ab != null)
		{
			if(ab instanceof MethodInsnNode && equals(updateInsn, (MethodInsnNode) ab))
			{
				MethodInsnNode minsn = (MethodInsnNode) ab;
				minsn.owner = "jredfox/DpiFixCoreMod";
				minsn.name = "disabled";
			}
			ab = ab.getNext();
		}
	}
	
	public static void disabled() {}
	
	/**
	 * Gets the Last LabelNode either before the return of the method or last label
	 */
	public static LabelNode getLastLabelNode(MethodNode method, boolean afterReturn)
	{
		AbstractInsnNode[] arr = method.instructions.toArray();
		boolean found = afterReturn;
		for(int i=arr.length-1;i>=0;i--)
		{
			AbstractInsnNode ab = arr[i];
			if(!found && isReturnOpcode(ab.getOpcode()))
				found = true;
			
			if(found && ab instanceof LabelNode)
			{
				return (LabelNode) ab;
			}
		}
		return null;
	}
	
	public static boolean isReturnOpcode(int opcode)
	{
		return opcode == Opcodes.RETURN || opcode == Opcodes.ARETURN || opcode == Opcodes.DRETURN || opcode == Opcodes.FRETURN || opcode == Opcodes.IRETURN || opcode == Opcodes.LRETURN;
	}
	
	public static MethodInsnNode nextMethodInsnNode(AbstractInsnNode pretarg, int opcode, String owner, String name, String desc, boolean itf) 
	{
		MethodInsnNode look = newMethodInsnNode(opcode, owner, name, desc, itf);
		AbstractInsnNode ab = pretarg;
		while(ab != null)
		{
			ab = ab.getNext();
			if(ab instanceof MethodInsnNode && equals(look, (MethodInsnNode) ab))
				return (MethodInsnNode) ab;
		}
		return null;
	}
	
	public static FieldInsnNode getFieldInsnNode(MethodNode node, int opcode, String owner, String name, String desc)
	{
		AbstractInsnNode[] arr = node.instructions.toArray();
		FieldInsnNode compare = new FieldInsnNode(opcode, owner, name, desc);
		for(AbstractInsnNode ab : arr)
		{
			if(ab instanceof FieldInsnNode && equals(compare, (FieldInsnNode)ab))
			{
				return (FieldInsnNode)ab;
			}
		}
		return null;
	}
	
	public static boolean equals(FieldInsnNode obj1, FieldInsnNode obj2)
	{
		return obj1.getOpcode() == obj2.getOpcode() && obj1.name.equals(obj2.name) && obj1.desc.equals(obj2.desc) && obj1.owner.equals(obj2.owner);
	}
	
	/**
	 * optimized way of getting a last instruction
	 */
	public static AbstractInsnNode getLastInstruction(MethodNode method, int opCode) 
	{
		AbstractInsnNode[] arr = method.instructions.toArray();
		for(int i=arr.length-1;i>=0;i--)
		{
			AbstractInsnNode node = arr[i];
			if(node.getOpcode() == opCode)
				return node;
		}
		return null;
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
		mc.gameSettings.fullScreen = !mc.fullscreen;//1.6.4 doesn't have AT's so use the getter method and hope it's not overriden
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
	        	setIngameNotInFocus(mc);
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
		        	setIngameFocus(mc);
		        	mouseFlag = false;
		        }
			}
		}
	}
	
	public static boolean oldGuiScreen = ForgeVersion.getMajorVersion() < 10;
	
    /**
     * Will set the focus to ingame if the Minecraft window is the active with focus. Also clears any GUI screen
     * currently displayed
     */
    public static void setIngameFocus(Minecraft mc)
    {
    	if (!mc.inGameHasFocus)
        {
            mc.inGameHasFocus = true;
            mc.mouseHelper.grabMouseCursor();
            if(oldGuiScreen)
            	mc.func_71373_a((GuiScreen)null);
            else
            	mc.displayGuiScreen((GuiScreen)null);
            mc.leftClickCounter = 10000;
        }
    }
    
    public static void setIngameNotInFocus(Minecraft mc)
    {
        if (mc.inGameHasFocus)
        {
            mc.inGameHasFocus = false;
            mc.mouseHelper.ungrabMouseCursor();
        }
    }
    
    public static void tickDisplay(Minecraft mc)
    {
        Display.update();

        if (!mc.fullscreen && Display.wasResized())
        {
            int i = mc.displayWidth;
            int j = mc.displayHeight;
            mc.displayWidth = Display.getWidth();
            mc.displayHeight = Display.getHeight();

            if (mc.displayWidth != i || mc.displayHeight != j)
            {
                if (mc.displayWidth <= 0)
                {
                	mc.displayWidth = 1;
                }

                if (mc.displayHeight <= 0)
                {
                	mc.displayHeight = 1;
                }

                mc.resize(mc.displayWidth, mc.displayHeight);
            }
        }
    }
    
    public static void updateViewPort(Minecraft mc) 
    {
        ScaledResolution scaledresolution = new ScaledResolution(mc.gameSettings, mc.displayWidth, mc.displayHeight);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0D, scaledresolution.getScaledWidth_double(), scaledresolution.getScaledHeight_double(), 0.0D, 1000.0D, 3000.0D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glTranslatef(0.0F, 0.0F, -2000.0F);
        GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight);
        GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
	}
	
	//##############################  End Functions  ##############################\\
	

}
