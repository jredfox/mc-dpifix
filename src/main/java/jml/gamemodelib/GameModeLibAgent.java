package jml.gamemodelib;

import java.lang.instrument.Instrumentation;

import jredfox.DpiFix;

public class GameModeLibAgent {
	
	public static void premain(String agentArgs, Instrumentation inst)
	{
		System.setProperty("gamemodelib.agent", "true");
		boolean fixDPI = Boolean.parseBoolean(System.getProperty("gamemodelib.dpi", "false"));
		boolean highPriority = Boolean.parseBoolean(System.getProperty("gamemodelib.high", "false"));
		GameModeLib.load();
		try
		{
			if(fixDPI)
				GameModeLib.fixDPI();
			if(highPriority)
				GameModeLib.setHighPriority();
		}
		catch(Throwable t)
		{
			t.printStackTrace();//Handle Natives not found for the OS
		}
	}
	
	public static void agentmain(String agentArgs, Instrumentation inst) 
	{
		
	}

}