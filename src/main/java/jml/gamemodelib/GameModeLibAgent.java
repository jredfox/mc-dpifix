package jml.gamemodelib;

import java.io.File;
import java.lang.instrument.Instrumentation;

import jredfox.DeAWTTransformer;
import jredfox.clfix.LaunchWrapperTransformer;

public class GameModeLibAgent {
	
	public static void premain(String agentArgs, Instrumentation inst)
	{
		try
		{
			GameModeLib.init();
			GameModeLib.load();
			GameModeLib.fixDPI();
			GameModeLib.setHighPriority();
			DeAWTTransformer.init(inst);
			LaunchWrapperTransformer.init(inst);
		}
		catch(Throwable t)
		{
			t.printStackTrace();//Handle Natives not found for the OS
		}
		
//		GameModeLib.removeAgent(); //TODO: Re-Implement removal of JavaAgent and instead save the DummyMap / DummySet In Memory so it can find it later
	}

	public static void agentmain(String agentArgs, Instrumentation inst) 
	{
		
	}

}