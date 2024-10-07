package jredfox;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

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

import jredfox.clfix.LaunchClassLoaderFix;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraftforge.common.ForgeVersion;

public class DpiFixCoreMod implements IClassTransformer, cpw.mods.fml.relauncher.IClassTransformer {
	
	static
	{
		LaunchClassLoaderFix.stopMemoryOverflow(null);
	}
	
	public static List<String> cls = DpiFix.asStringList(new String[] {
			"net.minecraft.client.Minecraft", 
			"net.minecraft.client.gui.LoadingScreenRenderer", 
			"jredfox.dpimod.DpiFixModLegacy"
	});
	
	/**
	 * check if notch names should be used without loading any minecraft classes
	 */
	public static boolean onesixnotch = ForgeVersion.getMajorVersion() < 9 || ForgeVersion.getMajorVersion() == 9 && ForgeVersion.getMinorVersion() <= 11 && ForgeVersion.getBuildVersion() < 937;
	
	@Override
	public byte[] transform(String name, String transformedName, byte[] basicClass) 
	{
		int index = cls.indexOf(transformedName);
		if(index != -1)
		{
			try
			{
				name = name.replace(".", "/");
				transformedName = transformedName.replace(".", "/");
				ClassNode classNode = CoreUtils.getClassNode(basicClass);
	            
	            if(index == 0)
	            {
	            	System.out.println("Patching: Minecraft Fullscreen to fix MC-68754, MC-111419, MC-160054");
	            	patchFullScreen(name, classNode);
	            	patchMaxResFix( name, classNode);
	            }
	            else if(index == 1)
	            {
	            	patchLoadingScreenRenderer(name, classNode);
	            }
	            else
	            {
	            	DpiFixAnnotation.patchAtMod(classNode);
	            }
	            
	            ClassWriter cw = CoreUtils.getClassWriter(classNode, ClassWriter.COMPUTE_MAXS);
	            return CoreUtils.toByteArray(cw, transformedName);
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
			if(f.name.equals(CoreUtils.getObfString("leftClickCounter", onesixnotch ? notch_leftClickCounter : "field_71429_W")) || f.name.equals(CoreUtils.getObfString("fullscreen", onesixnotch ? notch_fullscreen : "field_71431_Q")) )
			{
				f.access = Opcodes.ACC_PUBLIC;
			}
		}
		
		//fix MC-111419 by injecting DpiFixCoreMod#syncFullScreen
		String toggleFullscreen = CoreUtils.getObfString("toggleFullscreen", onesixnotch ? "j" : "func_71352_k");
		MethodNode m = CoreUtils.getMethodNode(classNode, toggleFullscreen, "()V");
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
	
	//##############################  START Functions  ##############################\\
	
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
