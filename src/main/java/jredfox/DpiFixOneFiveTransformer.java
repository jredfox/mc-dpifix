package jredfox;

import java.lang.reflect.Method;

import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.ow2.asm.Opcodes;
import org.ow2.asm.tree.AbstractInsnNode;
import org.ow2.asm.tree.ClassNode;
import org.ow2.asm.tree.FieldInsnNode;
import org.ow2.asm.tree.FieldNode;
import org.ow2.asm.tree.FrameNode;
import org.ow2.asm.tree.InsnList;
import org.ow2.asm.tree.InsnNode;
import org.ow2.asm.tree.JumpInsnNode;
import org.ow2.asm.tree.LabelNode;
import org.ow2.asm.tree.LdcInsnNode;
import org.ow2.asm.tree.LineNumberNode;
import org.ow2.asm.tree.MethodInsnNode;
import org.ow2.asm.tree.MethodNode;
import org.ow2.asm.tree.TypeInsnNode;
import org.ow2.asm.tree.VarInsnNode;

import jredfox.clfix.LaunchClassLoaderFix;
import net.minecraftforge.common.ForgeVersion;

public class DpiFixOneFiveTransformer implements IDpiFixTransformer {
	
	@Override
	public void transform(String notch_mc, int index, ClassNode classNode)
	{
		switch(index)
		{
			case 0:
            	System.out.println("Patching: Minecraft Fullscreen to fix MC-68754, MC-111419, MC-160054");
				patchMC(notch_mc, classNode);
			break;
			
			case 1:
				patchLoadingScreenRenderer(notch_mc, classNode);
			break;
			
			case 2:
				DpiFixAnn.patchAtMod(classNode);
			break;
			
			case 3:
				removeAppletShutdown(classNode);
			break;
			
			case 4:
				patchAppletImplShutdown(classNode);
			break;
			
			case 5:
				patchMouseHelper(classNode);
			break;
			
			case 6:
				disableThreadDownloadResources(classNode);
			break;
			
			case 7:
				disableThreadSpamResources(classNode);
			break;
			
			case 8:
				optifineCompat(classNode);
			break;
			
			case 9:
				optifineAntiAlisCompat(classNode);
			break;
			
			case 10:
				optifineConfigProxy(classNode);
			break;
			
			case 11:
				optifineDisplayCreate(classNode);
			break;
			
			case 12:
				optifineCompatGuiModList(classNode);
			break;
			
			case 13:
			case 14:
				pubMinusFinal(classNode);
			break;

			default:
				break;
		}
	}

	public final String mcAppletF = CoreUtils.getObfString("mcApplet", "A");//field_71473_z
	public final String mcCanvasF = CoreUtils.getObfString("mcCanvas", "m");//field_71447_l
	public final String leftClickCounterF = CoreUtils.getObfString("leftClickCounter", "Y");//field_71429_W
	public final String fullScreenF = CoreUtils.getObfString("fullscreen", "S");//field_71431_Q
	public final String tempDisplayWidthF = CoreUtils.getObfString("tempDisplayWidth", "Z");//field_71436_X
	public final String tempDisplayHeightF = CoreUtils.getObfString("tempDisplayHeight", "aa");//field_71435_Y
	public final String displayHeightF = CoreUtils.getObfString("displayHeight", "d");//field_71440_d
	public final String displayWidthF = CoreUtils.getObfString("displayWidth", "c");//field_71443_c
	public final String loadingScreenF = CoreUtils.getObfString("loadingScreen", "t");//field_71461_s
	
	public final String tempDisplayWidthF_SRG = CoreUtils.getObfString("tempDisplayWidth", "field_71436_X");
	public final String tempDisplayHeightF_SRG = CoreUtils.getObfString("tempDisplayHeight", "field_71435_Y");
	public final String loadingScreenF_SRG = CoreUtils.getObfString("loadingScreen", "field_71461_s");
	
	public final String[] fields_at = new String[] {
			mcAppletF,
			mcCanvasF,
			leftClickCounterF,
			fullScreenF,
			tempDisplayWidthF,
			tempDisplayHeightF,
			displayHeightF,
			displayWidthF,
			loadingScreenF
	};
	
	public final String startGameMethod = CoreUtils.getObfString("startGame", "a");//func_71384_a
	public final String loadScreenMethod = CoreUtils.getObfString("loadScreen", "J");//func_71357_I
	public final String runGameLoopMethod = CoreUtils.getObfString("runGameLoop", "K");//func_71411_J
	public final String toggleFullScreenMethod = CoreUtils.getObfString("toggleFullscreen", "k");//func_71352_k
	public final String resizeMethod = CoreUtils.getObfString("resize", "a");//func_71370_a
	
	public final String renderEngine = CoreUtils.getObfString("net/minecraft/client/renderer/RenderEngine", ForgeVersion.getBuildVersion() <= 598 ? "bfy" : ForgeVersion.getMinorVersion() < 8 ? "bgf" : "bge");
	
    /**
     * get De-AWT boolean based on the OS
     */
	public boolean hasDeAWT() 
	{
		return DpiFix.isWindows ? DpiFix.deawt_windows : DpiFix.isMacOs ? DpiFix.deawt_mac : DpiFix.isLinux ? DpiFix.deawt_linux : true;
	}

	public void patchMC(String notch_mc, ClassNode classNode)
	{
		String mcClazz = CoreUtils.getObfString("net/minecraft/client/Minecraft", notch_mc);
		
		//Manual Access Transformer (AT) for 1.5x
		for(FieldNode f : classNode.fields)
		{
			for(String fname : fields_at) 
			{
				if(f.name.equals(fname))
				{
					f.access = Opcodes.ACC_PUBLIC;
					break;
				}
			}
		}
		
		this.patchMemCache(mcClazz, classNode);
		
		/**
		 * De-AWT which includes patches De-AWT & MaxResFix if enabled. De-AWT cannot be applied without the MaxResFix
		 * MaxResFix without De-AWT is useless in 1.5x
		 */
		this.patchDeAWT(mcClazz, classNode);
		
		MethodNode runGameLoop = CoreUtils.getMethodNode(classNode, runGameLoopMethod, "()V");
		
		//fix MC-160054 tabbing out or showing desktop results in minimized MC < 1.8
		if(DpiFix.fsTabFix)
		{
			if(CoreUtils.getMethodInsnNode(runGameLoop, Opcodes.INVOKEVIRTUAL, mcClazz, toggleFullScreenMethod, "()V", false) != null)
			{
				MethodInsnNode spotTab = CoreUtils.getMethodInsnNode(runGameLoop, Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "isActive", "()Z", false);
				if(spotTab != null)
				{
					JumpInsnNode jump2 = CoreUtils.nextJumpInsnNode(spotTab);
					if(jump2 != null)
					{
						InsnList list = new InsnList();
						list.add(new InsnNode(Opcodes.ICONST_0));
						list.add(new JumpInsnNode(Opcodes.IFEQ, jump2.label));
						runGameLoop.instructions.insert(CoreUtils.prevLabelNode(spotTab), list);
					}
				}
			}
		}
		
		MethodNode nodeFS = CoreUtils.getMethodNode(classNode, toggleFullScreenMethod, "()V");
		nodeFS.access = Opcodes.ACC_PUBLIC;
		
		//fix MC-111419 by injecting DpiFixCoreMod#syncFullScreen
		if(DpiFix.fsSaveFix)
		{
			InsnList l = new InsnList();
			l.add(new LabelNode());
			l.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixCoreMod", "syncFullScreen", "()V", false));
			nodeFS.instructions.insert(l);
		}
		
		if(DpiFix.isLinux ? DpiFix.fsMouseFixLinux : DpiFix.fsMouseFixOther)
		{	
			/**
			 * DpiFixCoreMod.fsMousePre(this);
			 */
			MethodInsnNode vsync = CoreUtils.getMethodInsnNode(nodeFS, Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "setVSyncEnabled", "(Z)V", false);
			InsnList fspre = new InsnList();
			fspre.add(new VarInsnNode(Opcodes.ALOAD, 0));
			fspre.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixCoreMod", "fsMousePre", "(Lnet/minecraft/client/Minecraft;)V", false));
			nodeFS.instructions.insert(vsync, fspre);
			
			/**
			 * DpiFixCoreMod.fsMousePost(this);
			 */
			InsnList fspost = new InsnList();
			fspost.add(new VarInsnNode(Opcodes.ALOAD, 0));
			fspost.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixCoreMod", "fsMousePost", "(Lnet/minecraft/client/Minecraft;)V", false));
			AbstractInsnNode spot = CoreUtils.getLastMethodInsn(runGameLoop, Opcodes.INVOKEVIRTUAL, CoreUtils.getObfString("net/minecraft/profiler/Profiler", "la"), CoreUtils.getObfString("endSection", "b"), "()V", false);
			runGameLoop.instructions.insert(spot, fspost);
		}
	}
	
	public void patchMemCache(String mcClazz, ClassNode classNode)
	{
		MethodNode clinit = CoreUtils.getMethodNode(classNode, "<clinit>", "()V");
		if(clinit == null)
			return;
		
		AbstractInsnNode ab = clinit.instructions.getFirst();
		while(ab != null)
		{
			if(ab instanceof LdcInsnNode)
			{
				LdcInsnNode dc = (LdcInsnNode) ab;
				if(dc.cst instanceof Integer && dc.cst.equals(new Integer(10485760)) && ab.getNext().getOpcode() == Opcodes.NEWARRAY)
				{
					dc.cst = new Integer(0);
					break;
				}
			}
			ab = ab.getNext();
		}
	}

	private void patchDeAWT(String mcClazz, ClassNode classNode) 
	{
		if(!this.hasDeAWT())
			return;
		
		System.out.println("Patching: Minecraft Using De-AWT Transformer");
		MethodNode ctr = CoreUtils.getFirstConstructor(classNode);
		
		//Find Injection point of first-ish line of the constructor
		AbstractInsnNode spotPre = null;
		AbstractInsnNode abctr = ctr.instructions.getFirst();
		while(abctr != null)
		{
			if(abctr instanceof VarInsnNode && abctr.getOpcode() == Opcodes.ALOAD || abctr.getOpcode() == Opcodes.ILOAD)
			{
				VarInsnNode var = (VarInsnNode) abctr;
				if(var.var > 0)
				{
					spotPre = CoreUtils.nextLabelNode(abctr);
					break;
				}
			}
			abctr = abctr.getNext();
		}
		
		//DpiFixDeAWT.hide(par1Canvas, par2MinecraftApplet);
		//this.tempDisplayWidth = par3;
		//this.tempDisplayHeight = par4;
		InsnList lctr = new InsnList();
		lctr.add(new VarInsnNode(Opcodes.ALOAD, 1));
		lctr.add(new VarInsnNode(Opcodes.ALOAD, 2));
		lctr.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixDeAWT", "hide", "(Ljava/awt/Canvas;Lnet/minecraft/client/MinecraftApplet;)V", false));
		lctr.add(new VarInsnNode(Opcodes.ALOAD, 0));
		lctr.add(new VarInsnNode(Opcodes.ILOAD, 3));
		lctr.add(new FieldInsnNode(Opcodes.PUTFIELD, "net/minecraft/client/Minecraft", tempDisplayWidthF_SRG, "I"));
		lctr.add(new VarInsnNode(Opcodes.ALOAD, 0));
		lctr.add(new VarInsnNode(Opcodes.ILOAD, 4));
		lctr.add(new FieldInsnNode(Opcodes.PUTFIELD, "net/minecraft/client/Minecraft", tempDisplayHeightF_SRG, "I"));
		lctr.add(new LabelNode());
		ctr.instructions.insert(spotPre, lctr);
		
		//DpiFixDeAWT.hide(this);
		InsnList lctrLast = new InsnList();
		lctrLast.add(new VarInsnNode(Opcodes.ALOAD, 0));
		lctrLast.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixDeAWT", "hide", "(Lnet/minecraft/client/Minecraft;)V", false));
		ctr.instructions.insert(CoreUtils.getLastInstruction(ctr, Opcodes.PUTFIELD), lctrLast);
		
		MethodNode startGame = CoreUtils.getMethodNode(classNode, startGameMethod, "()V");
//		DpiFixCoreMod.sleep(250);
//    	Display.setParent(null);
//    	DpiFixDeAWT.fixIcons(this);
//    	DpiFixDeAWT.hide(this);
//		java.awt.Component.canSetVisible = true;
		InsnList startList = new InsnList();
		startList.add(new LdcInsnNode(new Long(250L)));
		startList.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixCoreMod", "sleep", "(J)V", false));
		startList.add(new InsnNode(Opcodes.ACONST_NULL));
		startList.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "setParent", "(Ljava/awt/Canvas;)V", false));
		startList.add(new VarInsnNode(Opcodes.ALOAD, 0));
		startList.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixDeAWT", "fixIcons", "(Lnet/minecraft/client/Minecraft;)V", false));
		startList.add(new VarInsnNode(Opcodes.ALOAD, 0));
		startList.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixDeAWT", "hide", "(Lnet/minecraft/client/Minecraft;)V", false));
		startList.add(new LabelNode());
		//only inject the code if the agent has transformed java.awt.Component
		if(DpiFix.agentmode && Boolean.parseBoolean(System.getProperty("gamemodelib.deawt", "false")) )
		{
			startList.add(new InsnNode(Opcodes.ICONST_1));
			startList.add(new FieldInsnNode(Opcodes.PUTSTATIC, "java/awt/Component", "canSetVisible", "Z"));
			startList.add(new LabelNode());
		}
		startGame.instructions.insert(CoreUtils.getFirstInstruction(startGame), startList);
		
		//if(false && this.mcCanvas != null)
		FieldInsnNode id = CoreUtils.getFieldInsnNode(startGame, Opcodes.GETFIELD, mcClazz, mcCanvasF, "Ljava/awt/Canvas;" );
		JumpInsnNode jump = CoreUtils.nextJumpInsnNode(id);
		LabelNode startIf = CoreUtils.prevLabel(id);
		InsnList startIfList = new InsnList();
		startIfList.add(new InsnNode(Opcodes.ICONST_0));
		startIfList.add(new JumpInsnNode(Opcodes.IFEQ, jump.label));
		startGame.instructions.insert(startIf, startIfList);
		
		//Display.setResizable(true);
		MethodInsnNode spotDisplay = CoreUtils.getMethodInsnNode(startGame, Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "setDisplayMode", "(Lorg/lwjgl/opengl/DisplayMode;)V", false);
		InsnList spotDisplayList = new InsnList();
		spotDisplayList.add(new InsnNode(Opcodes.ICONST_1));
		spotDisplayList.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "setResizable", "(Z)V", false));
		startGame.instructions.insert(spotDisplay, spotDisplayList);
		
		//DpiFixDeAWT.fixTitle();
		MethodInsnNode startTitle = CoreUtils.getMethodInsnNode(startGame, Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "setTitle", "(Ljava/lang/String;)V", false);
		InsnList startTitleList = new InsnList();
		startTitleList.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixDeAWT", "fixTitle", "()V", false));
		startGame.instructions.insert(startTitle, startTitleList);
		
		//this.fullscreen = false;
		//this.toggleFullscreen();
		//Wrap if(false) around the rest of the code inside the block as this.toggleFullscreen() has already been called
		LabelNode sfsLabel = CoreUtils.prevLabel(CoreUtils.prevLabelNode(startTitle));
		InsnList sfs = new InsnList();
		sfs.add(new VarInsnNode(Opcodes.ALOAD, 0));
		sfs.add(new InsnNode(Opcodes.ICONST_0));
		sfs.add(new FieldInsnNode(Opcodes.PUTFIELD, "net/minecraft/client/Minecraft", CoreUtils.getObfString("fullscreen", "field_71431_Q"), "Z"));
		sfs.add(new VarInsnNode(Opcodes.ALOAD, 0));
		sfs.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/client/Minecraft", CoreUtils.getObfString("toggleFullscreen", "func_71352_k"), "()V", false));
		sfs.add(new LabelNode());
		sfs.add(new InsnNode(Opcodes.ICONST_0));
		sfs.add(new JumpInsnNode(Opcodes.IFEQ, sfsLabel));
		sfs.add(new LabelNode());
		sfs.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
		AbstractInsnNode sfsSpot = CoreUtils.getMethodInsnNode(startGame, Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "setFullscreen", "(Z)V", false);
		startGame.instructions.insert(sfsSpot, sfs);
		
		if(ForgeVersion.getBuildVersion() <= 689 && OptifineCompat.hasOFAA)
		{
			//if(!DpiFixCoreMod.createOptifineDisplay())
			AbstractInsnNode liOFAASpot = CoreUtils.prevLabelNode(CoreUtils.getMethodInsnNode(startGame, Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "create", "(Lorg/lwjgl/opengl/PixelFormat;)V", false));
			if(liOFAASpot != null)
			{
				InsnList liOFAA = new InsnList();
				liOFAA.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixCoreMod", "createOptifineDisplay", "()Z", false));
				liOFAA.add(new JumpInsnNode(Opcodes.IFNE, CoreUtils.nextJumpInsnNode(liOFAASpot).label));
				liOFAA.add(new LabelNode());
				liOFAA.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
				startGame.instructions.insert(liOFAASpot, liOFAA);
			}
			else
			{
				System.err.println("Error Missing Display#create Method call inside of Minecraft#startGame. Optifine Anti-Alias Support is not possible!");
			}
		}
		
		//DpiFixDeAWT.loadScreen();
		//return;
		InsnList loadList = new InsnList();
		loadList.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixDeAWT", "loadScreen", "()V", false));
		loadList.add(new InsnNode(Opcodes.RETURN));
		loadList.add(new LabelNode());
		MethodNode loadScreen = CoreUtils.getMethodNode(classNode, loadScreenMethod, "()V");
		loadScreen.instructions.insert(CoreUtils.getFirstInstruction(loadScreen), loadList);
		
		MethodNode runGameLoop = CoreUtils.getMethodNode(classNode, runGameLoopMethod, "()V");
		
		//disable mcApplet check
		FieldInsnNode appletCheck = CoreUtils.getFieldInsnNode(runGameLoop, Opcodes.GETFIELD, mcClazz, mcAppletF, "Lnet/minecraft/client/MinecraftApplet;");
		if(appletCheck != null)
		{
			InsnList appletList = new InsnList();
			appletList.add(new InsnNode(Opcodes.ICONST_0));
			appletList.add(new JumpInsnNode(Opcodes.IFEQ, CoreUtils.nextJumpInsnNode(appletCheck).label));
			runGameLoop.instructions.insert(CoreUtils.prevLabelNode(appletCheck), appletList);
		}
		
		//remove canvas check
		AbstractInsnNode closeRequest = CoreUtils.getMethodInsnNode(runGameLoop, Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "isCloseRequested", "()Z", false);
		if(closeRequest != null)
		{
			FieldInsnNode canvasInsn = CoreUtils.previousFieldInsnNode(closeRequest, Opcodes.GETFIELD, mcClazz, mcCanvasF, "Ljava/awt/Canvas;");
			if(canvasInsn != null)
			{
				runGameLoop.instructions.remove(canvasInsn.getNext());//Remove JumpInsnNode
				runGameLoop.instructions.remove(canvasInsn.getPrevious());//Remove ALOAD 0
				runGameLoop.instructions.remove(canvasInsn);//Remove check
			}
			else
				System.err.println("Unable to Remove Minecraft#runGameLoop mcCanvas Check. The X Button may not work now :(");
		}
		
		//Disable Display Updates
		this.disableDisplayUpdate(runGameLoop);
		
		//DpiFixCoreMod#tickDisplay(this);
		LineNumberNode yeild = CoreUtils.prevLabelNode(CoreUtils.getMethodInsnNode(runGameLoop, Opcodes.INVOKESTATIC, "java/lang/Thread", "yield", "()V", false));
		InsnList yeildList = new InsnList();
		yeildList.add(new VarInsnNode(Opcodes.ALOAD, 0));
		yeildList.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixCoreMod", "tickDisplay", "(Lnet/minecraft/client/Minecraft;)V", false));
		runGameLoop.instructions.insert(yeild, yeildList);
		
		//Disable AWT resize method
		FieldInsnNode canvasInsn2 = CoreUtils.getFieldInsnNode(runGameLoop, Opcodes.GETFIELD, mcClazz, mcCanvasF, "Ljava/awt/Canvas;");
		if(canvasInsn2 != null)
		{
			InsnList liResize = new InsnList();
			liResize.add(new InsnNode(Opcodes.ICONST_0));
			liResize.add(new JumpInsnNode(Opcodes.IFEQ, CoreUtils.nextJumpInsnNode(canvasInsn2).label));
			runGameLoop.instructions.insert(CoreUtils.prevLabelNode(canvasInsn2), liResize);
		}
		
		MethodNode nodeFS = CoreUtils.getMethodNode(classNode, toggleFullScreenMethod, "()V");
		
		//Disable display update calls
		this.disableDisplayUpdate(nodeFS);
		
		//Disable mcCanvas check
		FieldInsnNode canvasInsnNode = CoreUtils.getFieldInsnNode(nodeFS, Opcodes.GETFIELD, mcClazz, mcCanvasF, "Ljava/awt/Canvas;");
		if(canvasInsnNode != null)
		{
			InsnList fsIfList = new InsnList();
			fsIfList.add(new InsnNode(Opcodes.ICONST_0));
			fsIfList.add(new JumpInsnNode(Opcodes.IFEQ, CoreUtils.nextJumpInsnNode(canvasInsnNode).label));
			nodeFS.instructions.insertBefore(canvasInsnNode.getPrevious(), fsIfList);//inject before ALOAD 0 we can't use prevLabel as there is a frame and we can't recompute frames
		}
		
		//DpiFixDeAWT.setDisplayMode(this);
		FieldInsnNode fsSpot = CoreUtils.nextFieldInsnNode(CoreUtils.getFieldInsnNode(nodeFS, Opcodes.GETFIELD, mcClazz, tempDisplayHeightF, "I"), Opcodes.PUTFIELD, mcClazz, displayHeightF, "I");
		InsnList fsSpotList = new InsnList();
		fsSpotList.add(new VarInsnNode(Opcodes.ALOAD, 0));
		fsSpotList.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixDeAWT", "setDisplayMode", "(Lnet/minecraft/client/Minecraft;)V", false));
		nodeFS.instructions.insert(fsSpot, fsSpotList);
		
		//DpiFixCoreMod#tickDisplay(this);
		InsnList fsli = new InsnList();
		fsli.add(new VarInsnNode(Opcodes.ALOAD, 0));
		fsli.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixCoreMod", "tickDisplay", "(Lnet/minecraft/client/Minecraft;)V", false));
		nodeFS.instructions.insert(CoreUtils.getLastMethodInsn(nodeFS, Opcodes.INVOKESTATIC, "jredfox/CoreUtils", "disabled", "()V", false), fsli);
		
		//DpiFixCoreMod#setFSDisplayMode(Display#getDesktopDisplayMode());
		MethodInsnNode setDisplayModeInsn = CoreUtils.getMethodInsnNode(nodeFS, Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "setDisplayMode", "(Lorg/lwjgl/opengl/DisplayMode;)V", false);
		setDisplayModeInsn.owner = "jredfox/DpiFixCoreMod";
		setDisplayModeInsn.name = "setFSDisplayMode";
		
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
			MethodInsnNode startResize = CoreUtils.getMethodInsnNode(nodeFS, Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "setResizable", "(Z)V", false);
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
			
			nodeFS.instructions.insert(CoreUtils.getMethodInsnNode(nodeFS, Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "setFullscreen", "(Z)V", false), li);
		}
		
		MethodNode resize = CoreUtils.getMethodNode(classNode, resizeMethod, "(II)V");
		resize.access = Opcodes.ACC_PUBLIC;//Make the method public
		
		//DpiFixCoreMod#updateViewPort
		InsnList viewport = new InsnList();
		viewport.add(new VarInsnNode(Opcodes.ALOAD, 0));
		viewport.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixCoreMod", "updateViewPort", "(Lnet/minecraft/client/Minecraft;)V", false));
		resize.instructions.insert(CoreUtils.getFieldInsnNode(resize, Opcodes.PUTFIELD, mcClazz, displayHeightF, "I"), viewport);
		
		//this.loadingScreen = new LoadingScreenRenderer(this);
		InsnList resizeList = new InsnList();
		resizeList.add(new LabelNode());
		resizeList.add(new VarInsnNode(Opcodes.ALOAD, 0));
		resizeList.add(new TypeInsnNode(Opcodes.NEW, "net/minecraft/client/gui/LoadingScreenRenderer"));
		resizeList.add(new InsnNode(Opcodes.DUP));
		resizeList.add(new VarInsnNode(Opcodes.ALOAD, 0));
		resizeList.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESPECIAL, "net/minecraft/client/gui/LoadingScreenRenderer", "<init>", "(Lnet/minecraft/client/Minecraft;)V", false));
		resizeList.add(new FieldInsnNode(Opcodes.PUTFIELD, mcClazz, loadingScreenF_SRG, "Lnet/minecraft/client/gui/LoadingScreenRenderer;"));
		resize.instructions.insertBefore(CoreUtils.getLastInstruction(resize, Opcodes.RETURN), resizeList);
		
		//Disable FMLReEntry from ever appearing
		MethodNode fmlReEntry = CoreUtils.getMethodNode(classNode, "fmlReentry", "(Lcpw/mods/fml/relauncher/ArgsWrapper;)V");
		MethodInsnNode fmlReEntryInsn = CoreUtils.getMethodInsnNode(fmlReEntry, Opcodes.INVOKEVIRTUAL, "java/awt/Frame", "setVisible", "(Z)V", false);
		fmlReEntry.instructions.remove(fmlReEntryInsn.getPrevious());
		fmlReEntry.instructions.insertBefore(fmlReEntryInsn, new InsnNode(Opcodes.ICONST_0));
	}

	public void patchLoadingScreenRenderer(String name, ClassNode classNode)
	{
		if(!this.hasDeAWT())
			return;
		
		System.out.println("Patching: LoadingScreenRenderer Using De-AWT Transformer");
		MethodNode m = CoreUtils.getMethodNode(classNode, CoreUtils.getObfString("setLoadingProgress", "a"), "(I)V"); //func_73718_a
		
		//Disable all Display#update calls
		disableDisplayUpdate(m);
		
		//DpiFixCoreMod.tickDisplay(this.mc);
		InsnList li = new InsnList();
		li.add(new VarInsnNode(Opcodes.ALOAD, 0));
		li.add(new FieldInsnNode(Opcodes.GETFIELD, name, CoreUtils.getObfString("mc", "field_73725_b"), "Lnet/minecraft/client/Minecraft;"));
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
	
	public void removeAppletShutdown(ClassNode classNode) 
	{
		if(!this.hasDeAWT())
			return;
		
		System.out.println("Removing: Applet Shutdown");
		MethodNode m = CoreUtils.getMethodNode(classNode, CoreUtils.getObfString("shutdown", "b"), "()V"); //func_71480_b
		InsnList li = new InsnList();
		LabelNode l0 = new LabelNode();
		li.add(l0);
		li.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/CoreUtils", "rtrue", "()Z", false));
		LabelNode l1 = new LabelNode();
		li.add(new JumpInsnNode(Opcodes.IFEQ, l1));
		li.add(new InsnNode(Opcodes.RETURN));
		li.add(l1);
		li.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
		m.instructions.insert(li);
	}
	
	public void patchAppletImplShutdown(ClassNode classNode) 
	{
		if(!this.hasDeAWT())
			return;
		
		System.out.println("Patching: MinecraftAppletImpl#displayCrashReportInternal Using De-AWT Transformer");
		MethodNode m = CoreUtils.getMethodNode(classNode, CoreUtils.getObfString("displayCrashReportInternal", "d"), CoreUtils.getObfString("(Lnet/minecraft/crash/CrashReport;)V", "(Lb;)V"));
		InsnList li = new InsnList();
		li.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixCoreMod", "shutdown", "()V", false));
		li.add(new VarInsnNode(Opcodes.ALOAD, 0));
		li.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/client/MinecraftAppletImpl", CoreUtils.getObfString("shutdownMinecraftApplet", "func_71405_e"), "()V", false));
		li.add(new InsnNode(Opcodes.ICONST_M1));
		li.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/System", "exit", "(I)V", false));
		m.instructions.insert(CoreUtils.getLastInstruction(m), li);
	}
	
	public void patchMouseHelper(ClassNode classNode)
	{
		if(!DpiFix.guiMouseFix)
			return;
		
		System.out.println("Patching: MouseHelper");
		//Display.setCursorPosition(Display.getWidth() / 2, Display.getHeight() / 2);
		//Display.setGrabbed(false);
		//return;
		MethodNode m = CoreUtils.getMethodNode(classNode, CoreUtils.getObfString("ungrabMouseCursor", "b"), "()V"); //func_74373_b
		InsnList l = new InsnList();
		LabelNode l0 = new LabelNode();
		l.add(l0);
		l.add(new LineNumberNode(42, l0));
		l.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "getWidth", "()I", false));
		l.add(new InsnNode(Opcodes.ICONST_2));
		l.add(new InsnNode(Opcodes.IDIV));
		l.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "getHeight", "()I", false));
		l.add(new InsnNode(Opcodes.ICONST_2));
		l.add(new InsnNode(Opcodes.IDIV));
		l.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "org/lwjgl/input/Mouse", "setCursorPosition", "(II)V", false));
		
		LabelNode l1 = new LabelNode();
		l.add(l1);
		l.add(new LineNumberNode(43, l1));
		l.add(new InsnNode(Opcodes.ICONST_0));
		l.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "org/lwjgl/input/Mouse", "setGrabbed", "(Z)V", false));
		l.add(new LabelNode());
		
		l.add(new InsnNode(Opcodes.RETURN));
		l.add(new LabelNode());
		m.instructions.insert(l);
	}
	
	public void disableThreadDownloadResources(ClassNode classNode) 
	{
		if(!DpiFix.fixResourceThread)
			return;
		
		MethodNode run = CoreUtils.getMethodNode(classNode, "run", "()V");
		InsnList li = new InsnList();
		li.add(new LabelNode());
		li.add(new VarInsnNode(Opcodes.ALOAD, 0));
		li.add(new VarInsnNode(Opcodes.ALOAD, 0));
		li.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/util/ThreadDownloadResources", CoreUtils.getObfString("resourcesFolder", "field_74579_a"), "Ljava/io/File;"));
		li.add(new LdcInsnNode(""));
		li.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESPECIAL, "net/minecraft/util/ThreadDownloadResources", CoreUtils.getObfString("loadResource", "func_74576_a"), "(Ljava/io/File;Ljava/lang/String;)V", false));
		li.add(new InsnNode(Opcodes.RETURN));
		li.add(new LabelNode());
		run.instructions.insert(li);
	}
	
	public void disableThreadSpamResources(ClassNode classNode) 
	{
		if(!DpiFix.fixResourceThread)
			return;
		
		MethodNode run = CoreUtils.getMethodNode(classNode, "run", "()V");
		InsnList li = new InsnList();
		li.add(new LabelNode());
		li.add(new InsnNode(Opcodes.RETURN));
		li.add(new LabelNode());
		run.instructions.insert(li);
	}
	
	public void optifineAntiAlisCompat(ClassNode classNode)
	{
		if(!this.hasDeAWT()) 
			return;
		
		MethodNode m = CoreUtils.getFirstConstructor(classNode);
		InsnList l = new InsnList();
		MethodInsnNode cfgInsn = CoreUtils.getMethodInsnNode(m, Opcodes.INVOKESTATIC, "Config", "isMultiTexture", "()Z", false);
		//if optifine is not installed return
		if(cfgInsn == null)
			return;
		
		//Disable need for creating LWJGL Frame as we are already in De-AWT
		System.out.println("Transforming RenderEngine for Optifine 1.5x Compat");
		AbstractInsnNode spot = CoreUtils.prevLabelNode(cfgInsn);
		JumpInsnNode jump = CoreUtils.nextJumpInsnNode(cfgInsn);
		InsnList li = new InsnList();
		li.add(new InsnNode(Opcodes.ICONST_0));
		li.add(new JumpInsnNode(Opcodes.IFEQ, jump.label));
		m.instructions.insert(spot, li);
	}
	
	public void optifineCompat(ClassNode classNode)
	{
		if(!this.hasDeAWT()) 
			return;
		MethodNode m = CoreUtils.getMethodNode(classNode, "checkDisplayMode", "()V");
		if(m == null)
			return;
		
		System.out.println("Transforming EntityRenderer for Optifine 1.5x Compat");
		OptifineCompat.setDesktopDisplayMode(Display.getDesktopDisplayMode());
		InsnList l = new InsnList();
		l.add(new LabelNode());
		l.add(new InsnNode(Opcodes.RETURN));
		l.add(new LabelNode());
		m.instructions.insert(l);
	}
	
	/**
	 * Transform our Optifine Proxy so it actually works with no reflection :)
	 */
	public void optifineConfigProxy(ClassNode classNode) 
	{
		for(MethodNode m : classNode.methods)
		{
			AbstractInsnNode ab = m.instructions.getFirst();
			while(ab != null)
			{
				if(ab instanceof MethodInsnNode && ((MethodInsnNode)ab).owner.equals("jredfox/OptifineConfig") )
				{
					MethodInsnNode mInsn = (MethodInsnNode) ab;
					mInsn.owner = "Config";
				}
				else if(ab instanceof FieldInsnNode && ((FieldInsnNode)ab).owner.equals("jredfox/OptifineConfig") )
				{
					FieldInsnNode fInsn = (FieldInsnNode) ab;
					fInsn.owner = "Config";
				}
				ab = ab.getNext();
			}
		}
	}
	
	public void optifineDisplayCreate(ClassNode classNode)
	{
		if(!this.hasDeAWT() || !OptifineCompat.hasOFAA)
			return;
		
		MethodNode m = CoreUtils.getMethodNode(classNode, "createDisplay", "()V");
		if(m == null)
			return;
		
		InsnList li = new InsnList();
		li.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixCoreMod", "createOptifineDisplay", "()Z", false));
		LabelNode l5 = new LabelNode();
		li.add(new JumpInsnNode(Opcodes.IFEQ, l5));
		LabelNode l6 = new LabelNode();
		li.add(l6);
		li.add(new InsnNode(Opcodes.RETURN));
		li.add(l5);
		li.add(new LabelNode());	
		li.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
		m.instructions.insert(CoreUtils.getMethodInsnNode(m, Opcodes.INVOKESTATIC, "javax/imageio/ImageIO", "setUseCache", "(Z)V", false), li);
	}
	
	public void optifineCompatGuiModList(ClassNode classNode)
	{
		if(!DpiFix.modLogoFix)
			return;
		
		System.out.println("Patching GuiModList");
		
		//GuiModListOneFive#cleanup
		MethodNode m = CoreUtils.getMethodNode(classNode, "selectModIndex", "(I)V");
		InsnList li = new InsnList();
		li.add(new LabelNode());
		li.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/dpimod/gui/GuiModListOneFive", "cleanup", "()V", false));
		m.instructions.insert(li);
		
		//remove: this.mc.bindTexture();
		//dim = GuiModListOneFive#getDim(logoFile, this.selectedMod);
		MethodNode draw = CoreUtils.getMethodNode(classNode, CoreUtils.getObfString("drawScreen", "a"), "(IIF)V");
		AbstractInsnNode targ = CoreUtils.nextLabelNode(CoreUtils.getMethodInsnNode(draw, Opcodes.INVOKEVIRTUAL, "cpw/mods/fml/client/TextureFXManager", "getTextureDimensions", "(Ljava/lang/String;)Ljava/awt/Dimension;", false));
		CoreUtils.deleteLine(draw, CoreUtils.getMethodInsnNode(draw, Opcodes.INVOKEVIRTUAL, this.renderEngine, CoreUtils.getObfString("bindTexture", "b"), "(Ljava/lang/String;)V", false));
		InsnList drawList = new InsnList();
		drawList.add(new VarInsnNode(Opcodes.ALOAD, 6));
		drawList.add(new VarInsnNode(Opcodes.ALOAD, 0));
		drawList.add(new FieldInsnNode(Opcodes.GETFIELD, "cpw/mods/fml/client/GuiModList", "selectedMod", "Lcpw/mods/fml/common/ModContainer;"));
		drawList.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/dpimod/gui/GuiModListOneFive", "getDim", "(Ljava/lang/String;Lcpw/mods/fml/common/ModContainer;)Ljava/awt/Dimension;", false));
		drawList.add(new VarInsnNode(Opcodes.ASTORE, 7));
		draw.instructions.insert(targ, drawList);
	}
	
	public void pubMinusFinal(ClassNode classNode)
	{
		for(FieldNode f : classNode.fields)
		{
		    // Get the current access flags
		    int access = f.access;
		    
		    // Remove conflicting access modifiers
		    access &= ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED);
		    
		    // Remove the final modifier
		    access &= ~Opcodes.ACC_FINAL;
		    
		    // Set the public modifier
		    access |= Opcodes.ACC_PUBLIC;
		    
		    // Update the field's access flags
		    f.access = access;
		}
	}

}
