package jredfox;

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
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import net.minecraftforge.common.ForgeVersion;

public class DpiFixTransformer implements IDpiFixTransformer {
	
	/**
	 * check if notch names should be used without loading any minecraft classes
	 */
	public static boolean onesixnotch = !DpiFixCoreMod.onefive && (ForgeVersion.getMajorVersion() < 9 || ForgeVersion.getMajorVersion() == 9 && ForgeVersion.getMinorVersion() <= 11 && ForgeVersion.getBuildVersion() < 937);
	
	@Override
	public void transform(String notch_mc, int index, ClassNode classNode)
	{
		switch(index)
		{
			case 0:
            	System.out.println("Patching: Minecraft Fullscreen to fix MC-68754, MC-111419, MC-160054");
				patchFullScreen(notch_mc, classNode);
				patchMaxResFix( notch_mc, classNode);
			break;
			
			case 1:
				patchLoadingScreenRenderer(notch_mc, classNode);
			break;
			
			case 2:
				DpiFixAnnotation.patchAtMod(classNode);
			break;
			
			default:
				break;
		}
	}
	
	/**
	 * patches fullscreen for versions of forge that hasn't patched in in 1.12.2
	 */
	public void patchFullScreen(String notch_mc, ClassNode classNode) 
	{
		//Universal AT 1.6 - 1.12.2
		String notch_leftClickCounter = (ForgeVersion.getMinorVersion() == 11 ? "W" : ForgeVersion.getMinorVersion() == 10 ? "W" : "V");
		String notch_fullscreen = (ForgeVersion.getMinorVersion() == 11 ? "P" : ForgeVersion.getMinorVersion() == 10 ? "P" : "O");
		for(FieldNode f : classNode.fields)
		{
			if(f.name.equals(CoreUtils.getObfString("leftClickCounter", onesixnotch ? notch_leftClickCounter : "field_71429_W")) || f.name.equals(CoreUtils.getObfString("fullscreen", onesixnotch ? notch_fullscreen : "field_71431_Q")) )
			{
				f.access = Opcodes.ACC_PUBLIC;
			}
		}
		
		//fix MC-111419 by injecting DpiFixCoreMod#syncFullScreen
		String toggleFullscreen = CoreUtils.getObfString("toggleFullscreen", onesixnotch ? "j" : "func_71352_k");
		MethodNode m = CoreUtils.getMethodNode(classNode, toggleFullscreen, "()V");
		m.access = Opcodes.ACC_PUBLIC;
		if(DpiFix.fsSaveFix)
		{
			InsnList l = new InsnList();
			l.add(new LabelNode());
			l.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixCoreMod", "syncFullScreen", "()V", false));
			m.instructions.insert(l);
		}
		
		//fix MC-160054 tabbing out or showing desktop results in minimized MC < 1.8
		if(DpiFix.fsTabFix)
		{
			MethodNode m2 = CoreUtils.getMethodNode(classNode, CoreUtils.getObfString("runGameLoop", onesixnotch ? "S" : "func_71411_J"), "()V");
			if(CoreUtils.getMethodInsnNode(m2, Opcodes.INVOKEVIRTUAL, onesixnotch ? notch_mc : "net/minecraft/client/Minecraft", toggleFullscreen, "()V", false) != null)
			{
				MethodInsnNode spot = CoreUtils.getMethodInsnNode(m2, Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "isActive", "()Z", false);
				if(spot != null)
				{
					JumpInsnNode jump = CoreUtils.nextJumpInsnNode(spot);
					if(jump != null)
					{
						InsnList list = new InsnList();
						list.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/CoreUtils", "rfalse", "()Z", false));
						list.add(new JumpInsnNode(Opcodes.IFEQ, jump.label));
						m2.instructions.insert(CoreUtils.prevLabelNode(spot), list);
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
			MethodInsnNode startResize = CoreUtils.getMethodInsnNode(m, Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "setResizable", "(Z)V", false);
			if(startResize != null)
			{
				System.err.println("Disabling Forge's \"FIX\" for Fullscreen!");
				MethodInsnNode resizeInsn = CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "setResizable", "(Z)V", false);
				AbstractInsnNode ab = startResize;
				while(ab != null)
				{
					if(ab instanceof MethodInsnNode && CoreUtils.equals(resizeInsn, (MethodInsnNode) ab))
					{
						MethodInsnNode minsn = (MethodInsnNode)ab;
						minsn.owner = "jredfox/DpiFixCoreMod";
					}
					ab = ab.getNext();
				}
			}
			
			InsnList li = new InsnList();
			li.add(new VarInsnNode(Opcodes.ALOAD, 0));
			li.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/client/Minecraft", CoreUtils.getObfString("fullscreen", "field_71431_Q").toString(), "Z"));
			LabelNode l26 = new LabelNode();
			li.add(new JumpInsnNode(Opcodes.IFNE, l26));
			if(DpiFix.isWindows)
			{
				LabelNode l27 = new LabelNode();
				li.add(l27);
				li.add(new InsnNode(Opcodes.ICONST_0));
				li.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "setResizable", "(Z)V", false));
			}
			LabelNode l28 = new LabelNode();
			li.add(l28);
			li.add(new InsnNode(Opcodes.ICONST_1));
			li.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "setResizable", "(Z)V", false));
			li.add(l26);
			//since we can't compute frames for mc 1.6.4 manually add the frame
			li.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
			
			m.instructions.insert(CoreUtils.getMethodInsnNode(m, Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "setFullscreen", "(Z)V", false), li);
		}
		
		//DpiFixCoreMod.patchSplash(this.mcDataDir);
		if(DpiFix.fsSplashFix && ForgeVersion.getMajorVersion() >= 10 && ForgeVersion.getBuildVersion() >= 1389)
		{
			MethodNode in = CoreUtils.getMethodNode(classNode, CoreUtils.getObfString("init", "func_71384_a"), "()V");
			if(in == null)
				in = CoreUtils.getMethodNode(classNode, "startGame", "()V");
			
			InsnList ilist = new InsnList();
			ilist.add(new VarInsnNode(Opcodes.ALOAD, 0));
			ilist.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/client/Minecraft", CoreUtils.getObfString("mcDataDir", "field_71412_D"), "Ljava/io/File;"));
			ilist.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixCoreMod", "patchSplash", "(Ljava/io/File;)V", false));
			in.instructions.insert(CoreUtils.nextLabelNode(in.instructions.getFirst()), ilist);
		}
		
		if(DpiFix.isLinux ? DpiFix.fsMouseFixLinux : DpiFix.fsMouseFixOther)
		{	
			/**
			 * DpiFixCoreMod.fsMousePre(this);
			 */
			MethodInsnNode vsync = CoreUtils.getMethodInsnNode(m, Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "setVSyncEnabled", "(Z)V", false);
			InsnList fspre = new InsnList();
			fspre.add(new VarInsnNode(Opcodes.ALOAD, 0));
			fspre.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixCoreMod", "fsMousePre", "(Lnet/minecraft/client/Minecraft;)V", false));
			m.instructions.insert(vsync, fspre);
			
			/**
			 * DpiFixCoreMod.fsMousePost(this);
			 */
			MethodNode runGame = CoreUtils.getMethodNode(classNode, CoreUtils.getObfString("runGameLoop", onesixnotch ? "S" : "func_71411_J"), "()V");
			InsnList fspost = new InsnList();
			fspost.add(new VarInsnNode(Opcodes.ALOAD, 0));
			fspost.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixCoreMod", "fsMousePost", "(Lnet/minecraft/client/Minecraft;)V", false));
			AbstractInsnNode spot = CoreUtils.getLastMethodInsn(runGame, Opcodes.INVOKEVIRTUAL, onesixnotch ? (ForgeVersion.getMinorVersion() == 11 ? "lv" : ForgeVersion.getMinorVersion() == 10 ? "lu" : "ls") : "net/minecraft/profiler/Profiler", CoreUtils.getObfString("endSection", onesixnotch ? "b" : "func_76319_b"), "()V", false);
			runGame.instructions.insert(spot, fspost);
		}
	}
	
	public void patchMaxResFix(String notch_mc, ClassNode classNode) 
	{
		if(!DpiFix.maximizeFix || ForgeVersion.getMajorVersion() >= 10)
			return;
		
		String mcClass = onesixnotch ? notch_mc : "net/minecraft/client/Minecraft";
		
		//DpiFixCoreMod#tickDisplay(this);
		MethodNode loadscreen = CoreUtils.getMethodNode(classNode, CoreUtils.getObfString("loadScreen", onesixnotch ? "R" : "func_71357_I"), "()V");
		MethodInsnNode updateInsn = CoreUtils.getLastMethodInsn(loadscreen, Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "update", "()V", false);
		disableDisplayUpdate(loadscreen);
		InsnList lslist = new InsnList();
		lslist.add(new VarInsnNode(Opcodes.ALOAD, 0));
		lslist.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixCoreMod", "tickDisplay", "(Lnet/minecraft/client/Minecraft;)V", false));
		loadscreen.instructions.insert(CoreUtils.getLastLabelNode(loadscreen, false), lslist);
		
		//Disable buggy calls of Display#update
		//DpiFixCoreMod#tickDisplay(this);
		MethodNode runGame = CoreUtils.getMethodNode(classNode, CoreUtils.getObfString("runGameLoop", onesixnotch ? "S" : "func_71411_J"), "()V");
		MethodInsnNode keyInsn = CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "org/lwjgl/input/Keyboard", "isKeyDown", "(I)Z", false);
		MethodInsnNode resizeInsn = CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "wasResized", "()Z", false);
		MethodInsnNode yeildInsn = CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Thread", "yield", "()V", false);
		boolean hasYeild = false;
		AbstractInsnNode ab = runGame.instructions.getFirst();
		while(ab != null)
		{
			if(ab instanceof IntInsnNode && ((IntInsnNode)ab).operand == 65 && ab.getNext() instanceof MethodInsnNode)
			{
				MethodInsnNode minsn = (MethodInsnNode) ab.getNext();
				if(CoreUtils.equals(keyInsn, minsn))
				{
					MethodInsnNode displayInsn = CoreUtils.nextMethodInsnNode(minsn, Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "update", "()V", false);
					displayInsn.owner = "jredfox/CoreUtils";
					displayInsn.name = "disabled";
				}
			}
			else if(ab instanceof MethodInsnNode)
			{
				MethodInsnNode minsn = (MethodInsnNode) ab;
				if(CoreUtils.equals(resizeInsn, minsn))
				{
					minsn.owner = "jredfox/CoreUtils";
					minsn.name = "rfalse";
				}
				else if(!hasYeild && CoreUtils.equals(yeildInsn, minsn))
				{
					hasYeild = true;//only insert 1 tickDisplay
					InsnList li = new InsnList();
					li.add(new VarInsnNode(Opcodes.ALOAD, 0));
					li.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixCoreMod", "tickDisplay", "(Lnet/minecraft/client/Minecraft;)V", false));
					runGame.instructions.insertBefore(minsn, li);
				}
			}
			ab = ab.getNext();
		}
		
		MethodNode fullscreen = CoreUtils.getMethodNode(classNode, CoreUtils.getObfString("toggleFullscreen", onesixnotch ? "j" : "func_71352_k"), "()V");
		disableDisplayUpdate(fullscreen);//Disable all calls of Display#update
		//DpiFixCoreMod#tickDisplay(this);
		InsnList fsli = new InsnList();
		fsli.add(new VarInsnNode(Opcodes.ALOAD, 0));
		fsli.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixCoreMod", "tickDisplay", "(Lnet/minecraft/client/Minecraft;)V", false));
		fullscreen.instructions.insert(CoreUtils.getLastMethodInsn(fullscreen, Opcodes.INVOKESTATIC, "jredfox/CoreUtils", "disabled", "()V", false), fsli);
		
		MethodNode resize = CoreUtils.getMethodNode(classNode, CoreUtils.getObfString("resize", onesixnotch ? "a" : "func_71370_a"), "(II)V");
		resize.access = Opcodes.ACC_PUBLIC;//Make the method public
		
		//DpiFixCoreMod#updateViewPort
		InsnList viewport = new InsnList();
		viewport.add(new VarInsnNode(Opcodes.ALOAD, 0));
		viewport.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixCoreMod", "updateViewPort", "(Lnet/minecraft/client/Minecraft;)V", false));
		resize.instructions.insert(CoreUtils.getFieldInsnNode(resize, Opcodes.PUTFIELD, mcClass, CoreUtils.getObfString("displayHeight", onesixnotch ? (ForgeVersion.getMinorVersion() == 11 ? "e" : ForgeVersion.getMinorVersion() == 10 ? "e" : "d") : "field_71440_d"), "I"), viewport);
		
		//this.loadingScreen = new LoadingScreenRenderer(this);
		InsnList resizeList = new InsnList();
		resizeList.add(new LabelNode());
		resizeList.add(new VarInsnNode(Opcodes.ALOAD, 0));
		resizeList.add(new TypeInsnNode(Opcodes.NEW, "net/minecraft/client/gui/LoadingScreenRenderer"));
		resizeList.add(new InsnNode(Opcodes.DUP));
		resizeList.add(new VarInsnNode(Opcodes.ALOAD, 0));
		resizeList.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESPECIAL, "net/minecraft/client/gui/LoadingScreenRenderer", "<init>", "(Lnet/minecraft/client/Minecraft;)V", false));
		resizeList.add(new FieldInsnNode(Opcodes.PUTFIELD, "net/minecraft/client/Minecraft", CoreUtils.getObfString("loadingScreen", "field_71461_s"), "Lnet/minecraft/client/gui/LoadingScreenRenderer;"));
		resize.instructions.insertBefore(CoreUtils.getLastInstruction(resize, Opcodes.RETURN), resizeList);
		
	}
	
	public void patchLoadingScreenRenderer(String name, ClassNode classNode)
	{
		if(!DpiFix.maximizeFix || ForgeVersion.getMajorVersion() >= 10)
			return;
		
		System.out.println("Patching: LoadingScreenRenderer");
		
		String cname = onesixnotch ? name : "net/minecraft/client/gui/LoadingScreenRenderer";
		MethodNode m = CoreUtils.getMethodNode(classNode, CoreUtils.getObfString("setLoadingProgress", onesixnotch ? "a" : "func_73718_a"), "(I)V");
		
		//Disable all Display#update calls
		disableDisplayUpdate(m);
		
		//DpiFixCoreMod.tickDisplay(this.mc);
		InsnList li = new InsnList();
		li.add(new VarInsnNode(Opcodes.ALOAD, 0));
		li.add(new FieldInsnNode(Opcodes.GETFIELD, cname, CoreUtils.getObfString("mc", "field_73725_b"), "Lnet/minecraft/client/Minecraft;"));
		li.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixCoreMod", "tickDisplay", "(Lnet/minecraft/client/Minecraft;)V", false));
		m.instructions.insertBefore(CoreUtils.getLastMethodInsn(m, Opcodes.INVOKESTATIC, "java/lang/Thread", "yield", "()V", false), li);
	}
	
	private void disableDisplayUpdate(MethodNode m)
	{
		//Disable all calls of Display#update
		MethodInsnNode updateInsn = CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "update", "()V", false);
		AbstractInsnNode ab = m.instructions.getFirst();
		while(ab != null)
		{
			if(ab instanceof MethodInsnNode && CoreUtils.equals(updateInsn, (MethodInsnNode) ab))
			{
				MethodInsnNode minsn = (MethodInsnNode) ab;
				minsn.owner = "jredfox/CoreUtils";
				minsn.name = "disabled";
			}
			ab = ab.getNext();
		}
	}

}
