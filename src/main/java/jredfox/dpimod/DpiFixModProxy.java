package jredfox.dpimod;

import jredfox.DpiFix;
import jredfox.clfix.LaunchClassLoaderFix;

public class DpiFixModProxy {
	
	public static void modPreInit()
	{
		//Windows locks Setting Process Priority During ASM Sometimes Especially if the ClassLoader gets Replaced
		//While we could get around this they also lock background processes to for Priority. So there would be a CLI annoying popup each time and that's just not ok
		if(DpiFix.isWindows && !DpiFix.agentmode && DpiFix.highPriority)
			DpiFix.setHighProcessPriority();
	}
	
	public static void modInit(ClassLoader clforge) 
	{
		System.out.println("DPI-Fix Mod Init");
		LaunchClassLoaderFix.stopMemoryOverflowFoamFix(clforge);
	}

}
