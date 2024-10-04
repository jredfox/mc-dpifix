package jredfox;

import cpw.mods.fml.common.Mod;

@Mod(
		modid = "dpi-fix", 
		name = "DPI-Fix", 
		version = "1.5.0", 
		acceptedMinecraftVersions = ""
	)
public class DpiFixModLegacy {
	
	public DpiFixModLegacy()
	{
		DpiFixModProxy.modInit();
	}

}
