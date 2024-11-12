package jredfox.dpimod;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(
		modid = DpiFixModVars.MODID,
		name =  DpiFixModVars.NAME,
		version = DpiFixModVars.VERSION,
		acceptedMinecraftVersions = "*",
		acceptableRemoteVersions = "*",
		dependencies = "before:foamfix"
	)
public class DpiFixMod {
	
	@Mod.EventHandler
	public void preinit(FMLPreInitializationEvent e)
	{
		DpiFixModProxy.modInit(this.getClass().getClassLoader());
	}
	
	@Mod.EventHandler
	public void loadcomplete(FMLLoadCompleteEvent e)
	{
		DpiFixModProxy.modLoadComplete(this.getClass().getClassLoader());
	}

}
