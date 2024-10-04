package jredfox;

import jredfox.clfix.LaunchClassLoaderFix;

public class DpiFixModProxy {
	
	public static void modInit() 
	{
		System.out.println("DPI-Fix Mod Init");
		LaunchClassLoaderFix.stopMemoryOverflowFoamFix();
	}

}
