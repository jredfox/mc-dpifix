package jredfox;

import java.awt.Dimension;
import java.lang.reflect.Method;

import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;

import jredfox.clfix.LaunchClassLoaderFix;
import net.minecraft.client.Minecraft;

public class OptifineCompat {
	
	public static final Class cfgClazz = LaunchClassLoaderFix.forName("Config");
	public static boolean hasOF = cfgClazz != null;
	
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

}
