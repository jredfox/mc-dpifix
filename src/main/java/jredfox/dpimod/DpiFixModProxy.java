package jredfox.dpimod;

import jredfox.DpiFix;
import jredfox.clfix.LaunchClassLoaderFix;

public class DpiFixModProxy {
	
	public static void modInit(ClassLoader clforge) 
	{
		System.out.println("DPI-Fix Mod Init");
		LaunchClassLoaderFix.stopMemoryOverflowFoamFix(clforge);
	}
	
	public static void modPostInit(ClassLoader clforge)
	{
		System.out.println("DPI-Fix Mod PostInit");
		LaunchClassLoaderFix.verify(clforge);
	}

}
