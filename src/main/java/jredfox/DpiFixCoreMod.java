package jredfox;

import java.io.File;
import java.util.List;

import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.PixelFormat;
import org.ow2.asm.ClassWriter;
import org.ow2.asm.tree.ClassNode;

import jredfox.clfix.LaunchClassLoaderFix;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.common.ForgeVersion;

public class DpiFixCoreMod implements IClassTransformer {
	
	public DpiFixCoreMod()
	{
		LaunchClassLoaderFix.stopMemoryOverflow(null);
	}
	
	/**
	 * Check if we are in 1.5x so we can configure the transformer for 1.5x
	 */
	public static boolean onefive = ForgeVersion.getMajorVersion() < 8;
	
	public static List<String> cls =  DpiFix.asStringList( getCls() );
	
	public IDpiFixTransformer dpifixTransformer = null;
	public boolean i = false;

	@Override
	public byte[] transform(String name, String transformedName, byte[] basicClass) 
	{
		int index = cls.indexOf(transformedName);
		if(index != -1 && basicClass != null)
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
				CoreUtils.dumpFileErr(transformedName, basicClass);
				t.printStackTrace();
			}
		}
		return basicClass;
	}
	
	private static String[] getCls() 
	{
		if(onefive)
		{
			return new String[] {
				"net.minecraft.client.Minecraft", 
				"net.minecraft.client.gui.LoadingScreenRenderer",
				"jredfox.dpimod.DpiFixModLegacy",
				"net.minecraft.client.MinecraftApplet",
				"net.minecraft.client.MinecraftAppletImpl",
				"net.minecraft.util.MouseHelper",
				"net.minecraft.util.ThreadDownloadResources",
				"net.minecraft.client.gui.RunnableTitleScreen",
				"net.minecraft.client.renderer.EntityRenderer",//START Optifine Compat
				"net.minecraft.client.renderer.RenderEngine",
				"jredfox.OptifineCompat",
				"net.minecraftforge.client.ForgeHooksClient",
				"cpw.mods.fml.client.GuiModList",
				"net.minecraft.client.renderer.RenderEngine",
				"cpw.mods.fml.client.TextureFXManager"//END Optifine Compat
			};
		}
		return new String[] {
			"net.minecraft.client.Minecraft", 
			"net.minecraft.client.gui.LoadingScreenRenderer",
			"jredfox.dpimod.DpiFixModLegacy",
			"net.minecraft.client.renderer.texture.TextureManager",
			"cpw.mods.fml.client.GuiModList",
			"net.minecraft.client.gui.GuiMainMenu",
			"Config"//Optifine Support for macOS
		};
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
		if(DpiFix.isMacOs)
		{
			PropertyConfig splash = new PropertyConfig(new File(mcDataDir, "config/splash.properties"));
			splash.load();
			boolean hasPatched = splash.get("dpifix.patched", false);
			if(!hasPatched)
			{
				splash.properties.setProperty("enabled", "false");
				splash.properties.setProperty("dpifix.patched", "true");
			}
			splash.save();
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
    	if(!Display.isCreated())
    		return;
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

    /**
     * Creates a shutdown thread that will force exit in 10s if minecraft can't save
     * Used Only for Minecraft 1.5x with De-AWT Enabled
     */
	public static void shutdown()
	{
		Thread t = new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				try 
				{
					Thread.sleep(10000);
					System.exit(-1);
				}
				catch (InterruptedException e) 
				{
					
				}
			}
		});
		t.setDaemon(true);
		t.start();
	}
	
	/**
	 * sleep in ms
	 */
	public static void sleep(long ms)
	{
		try
		{
			Thread.sleep(ms);
		}
		catch(Throwable t)
		{
			t.printStackTrace();
		}
	}
	
	/**
	 * Set FullScreen DisplayMode with optifine compat
	 */
	public static void setFSDisplayMode(DisplayMode fsmode) throws LWJGLException 
	{
		DisplayMode ofmode = OptifineCompat.getDisplayMode();
    	Display.setDisplayMode(ofmode != null ? ofmode : fsmode);
	}
	
	/**
	 * Creates Display with Anti-Aliasing Support for Optifine 1.5x
	 */
	public static boolean createOptifineDisplay()
	{
		int samples = OptifineCompat.getAntialiasingLevel();
		//if Anti-Aliasing is Disabled Let normal Flow of Code Happen
		if(samples <= 0)
			return false;
		
		PixelFormat pixelformat = new PixelFormat().withDepthBits(24).withSamples(samples);
		try
		{
			try
			{
				Display.create(pixelformat.withStencilBits(8));//create pixelformat with 8 stencil bits
				LaunchClassLoaderFix.setPrivate(null, 8, ForgeHooksClient.class, "stencilBits");
				System.out.println("Display#create with stencilBits:8 Samples:" + samples);
				return true;
			}
			catch(Exception e)
			{
				System.out.println("Display#create with Samples:" + samples);
				Display.create(pixelformat);//on failure of stencil bits try with just the sampling
				return true;
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		return false;
	}
	
	//##############################  End Functions  ##############################\\
	

}
