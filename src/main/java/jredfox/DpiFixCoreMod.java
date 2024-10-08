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
			"jredfox.dpimod.DpiFixModLegacy",
			"net.minecraft.client.MinecraftApplet",
			"net.minecraft.util.MouseHelper"
	});
	
	/**
	 * Check if we are in 1.5x so we can configure the transformer for 1.5x
	 */
	public static boolean onefive = ForgeVersion.getMajorVersion() < 8;
	
	public IDpiFixTransformer dpifixTransformer = null;
	public boolean i = false;

	@Override
	public byte[] transform(String name, String transformedName, byte[] basicClass) 
	{
		int index = cls.indexOf(transformedName);
		if(index != -1)
		{
			try
			{
				if(!this.i)
					this.init();
				name = name.replace(".", "/");
				transformedName = transformedName.replace(".", "/");
				ClassNode classNode = CoreUtils.getClassNode(basicClass);
	            
				dpifixTransformer.transform(name, index, classNode);
	            
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
	
	public void init() 
	{
		if(this.i) return;
		this.dpifixTransformer = onefive ? new DpiFixOneFiveTransformer() : new DpiFixTransformer();
		this.i = true;
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
