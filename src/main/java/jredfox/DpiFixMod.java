package jredfox;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(
		modid = "dpi-fix", 
		name = "DPI-Fix", 
		version = "1.5.0", 
		acceptedMinecraftVersions = "*",
		acceptableRemoteVersions = "*",
		dependencies = "before:foamfix"
	)
public class DpiFixMod {
	
	@Mod.EventHandler
	public void postinit(FMLPreInitializationEvent e)
	{
		DpiFixModProxy.modInit();
	}

}
