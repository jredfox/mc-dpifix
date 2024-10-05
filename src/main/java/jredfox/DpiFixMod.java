package jredfox;

import net.minecraftforge.fml.common.Mod;

@Mod(
		modid = "dpi-fix", 
		name = "DPI-Fix", 
		version = "1.5.0", 
		acceptedMinecraftVersions = "*",
		acceptableRemoteVersions = "*"
	)
public class DpiFixMod {
	
	public DpiFixMod()
	{
		DpiFixModProxy.modInit();
	}

}
