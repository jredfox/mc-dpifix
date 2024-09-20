package jml.gamemodelib;

import jredfox.DpiFix;

public class GameModeLib {

	public static void load() 
	{
		DpiFix.load();
	}

	public static void fixDPI()
	{
		DpiFix.fixProcessDPI();
	}

	public static void setHighPriority() 
	{
		DpiFix.setHighProcessPriority();
	}

}
