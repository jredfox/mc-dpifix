package jredfox.dpimod;

import jredfox.DpiFix;
import jredfox.clfix.LaunchClassLoaderFix;

public class DpiFixModProxy {
	
	public static void modInit(ClassLoader clforge) 
	{
		System.out.println("DPI-Fix Mod Init");
		LaunchClassLoaderFix.stopMemoryOverflowFoamFix(clforge);
	}
	
	public static void modLoadComplete(ClassLoader clforge)
	{
		System.out.println("DPI-Fix Mod Load Complete");
		LaunchClassLoaderFix.verify(clforge);
	}

}
