package jredfox;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import org.lwjgl.opengl.DisplayMode;

import jredfox.clfix.LaunchClassLoaderFix;
import net.minecraft.client.Minecraft;

public class OptifineCompat {
	
	public static final Class cfgClazz = LaunchClassLoaderFix.forName("Config");
	public static boolean hasOF = cfgClazz != null;
	public static boolean hasOFAA = hasOF && OptifineConfig.OF_EDITION.equalsIgnoreCase("HD_U");
	
	public static DisplayMode getDisplayMode()
	{
		if(!hasOF)
			return null;
		
		try
		{
			return (DisplayMode) OptifineConfig.getDisplayMode(OptifineConfig.getFullscreenDimension());
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		
		return null;
	}
	
	public static DisplayMode getDesktopDisplayMode()
	{
		if(!hasOF)
			return null;
		
		try
		{
			return OptifineConfig.getDesktopDisplayMode();
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		return null;
	}
	
	public static void setDesktopDisplayMode(DisplayMode mode)
	{
		if(!hasOF)
			return;
		
		try
		{
			OptifineConfig.setDesktopDisplayMode(mode);
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * Get Optifine's Anti-Aliasing level
	 */
	public static int getAntialiasingLevel() 
	{
		if(!hasOFAA)
			return 0;
		
		int aa = OptifineConfig.getAntialiasingLevel();
		if(aa != 0)
			return aa;
		
		Minecraft mc = Minecraft.getMinecraft();
		File optionsOf = new File(mc.mcDataDir, "optionsof.txt");
		if(optionsOf.exists())
		{
			BufferedReader bufferedreader = null;
			try
			{
				bufferedreader = new BufferedReader(new FileReader(optionsOf));
	            String s = "";
	            while ((s = bufferedreader.readLine()) != null) 
	            {
	            	final String[] as = s.split(":");
	            	if (as[0].equalsIgnoreCase("ofAaLevel") && as.length >= 2)
                        return Integer.valueOf(as[1]);
	            }
			}
			catch(Exception e)
			{
				e.printStackTrace();
			}
			finally
			{
				DpiFix.closeQuietly(bufferedreader);
			}
		}
		return 0;
	}

}
