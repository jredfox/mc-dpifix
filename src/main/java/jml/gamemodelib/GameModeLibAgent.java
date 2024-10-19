package jml.gamemodelib;

import java.io.Closeable;
import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import jredfox.DeAWTTransformer;

public class GameModeLibAgent {
	
	public static boolean debug;
	public static File jarFile;
	public static boolean hasForge;
	
	public static void premain(String agentArgs, Instrumentation inst)
	{
		try
		{
			System.setProperty("gamemodelib.agent", "true");
			debug = Boolean.parseBoolean(System.getProperty("gamemodelib.debug", "false"));
			jarFile = GameModeLib.getFileFromClass(GameModeLibAgent.class);
			hasForge = GameModeLib.forName("net.minecraftforge.common.ForgeVersion", GameModeLibAgent.class.getClassLoader()) != null;
			GameModeLib.load();
			GameModeLib.fixDPI();
			GameModeLib.setHighPriority();
			DeAWTTransformer.init(inst);
		}
		catch(Throwable t)
		{
			t.printStackTrace();//Handle Natives not found for the OS
		}
		
		try
		{
			//Remove agent from classpath so forge doesn't load our "@Mod" when not in coremods or mods folder
			if(Boolean.parseBoolean(System.getProperty("gamemodelib.removeAgent", "false")) || Boolean.parseBoolean(System.getProperty("gamemodelib.removeModAgent", "true")) && hasForge)
			{
				ClassLoader sy = ClassLoader.getSystemClassLoader();
				ClassLoader parent = GameModeLib.getParentCL(sy);
				ClassLoader context = Thread.currentThread().getContextClassLoader();
				GameModeLib.removeAgentClassPath(jarFile, true, sy, parent, (sy == context ? null : context));
			}
		}
		catch(Throwable t)
		{
			t.printStackTrace();
		}
	}

	public static void agentmain(String agentArgs, Instrumentation inst) 
	{
		
	}

}