package jml.gamemodelib;

import jredfox.DpiFix;

public class GameModeLib {

	public static void load() 
	{
		//Use Mod config useful for when both the javaagent and mod are used at the same time
		if(Boolean.parseBoolean(System.getProperty("gamemodelib.cfg", "false")))
			DpiFix.loadConfig();
		
		DpiFix.load();
	}

	public static void fixDPI()
	{
		if(DpiFix.dpifix)
			DpiFix.fixProcessDPI();
	}

	public static void setHighPriority() 
	{
		if(DpiFix.highPriority)
			DpiFix.setHighProcessPriority();
	}

}
