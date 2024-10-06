package jredfox.dpimod;

import jredfox.clfix.LaunchClassLoaderFix;

public class DpiFixModProxy {
	
	public static void modInit(ClassLoader clforge) 
	{
		System.out.println("DPI-Fix Mod Init");
		LaunchClassLoaderFix.stopMemoryOverflowFoamFix(clforge);
	}

}
