package jredfox.dpifix.compat;

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
			Method m2 = cfgClazz.getDeclaredMethod("getFullscreenDimension");
			m2.setAccessible(true);
			Dimension dim = (Dimension) m2.invoke(null);
			Method getDisplayMode = cfgClazz.getDeclaredMethod("getDisplayMode", Dimension.class);
			return (DisplayMode) getDisplayMode.invoke(null, dim);
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
			Method m2 = cfgClazz.getDeclaredMethod("getDesktopDisplayMode", DisplayMode.class);
			m2.setAccessible(true);
			return (DisplayMode) m2.invoke(null);
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
			Method m2 = cfgClazz.getDeclaredMethod("setDesktopDisplayMode", DisplayMode.class);
			m2.setAccessible(true);
			m2.invoke(null, mode);
			System.out.println(getDisplayMode());
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}

}
