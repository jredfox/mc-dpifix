package jredfox.dpifix.compat;

import java.lang.reflect.Method;

import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

import jredfox.clfix.LaunchClassLoaderFix;

public class OptifineCompat {
	
	public static final Class cfgClazz = LaunchClassLoaderFix.forName("Config");
	
	public static DisplayMode getDesktopDisplayMode()
	{
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
		try
		{
			Method m2 = cfgClazz.getDeclaredMethod("setDesktopDisplayMode", DisplayMode.class);
			m2.setAccessible(true);
			m2.invoke(null, mode);
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}

}
