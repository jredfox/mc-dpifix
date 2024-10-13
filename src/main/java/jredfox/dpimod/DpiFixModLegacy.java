package jredfox.dpimod;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.FMLModContainer;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import jredfox.DpiFix;
import net.minecraftforge.common.ForgeVersion;

@Mod(
		modid = DpiFixModVars.MODID,
		name =  DpiFixModVars.NAME,
		version = DpiFixModVars.VERSION,
		acceptedMinecraftVersions = "",
		acceptableRemoteVersions = "*"
	)
public class DpiFixModLegacy {
	
	public DpiFixModLegacy()
	{
		//The Coremod not loading can only mean one of two things they are in 1.5x or they deleted or modified the meta-inf
		if(!DpiFix.coremodLoaded)
			throw new IllegalArgumentException("Dpi-Fix Mod Must be put in your coremods Folder!");
	}
	
//	@PreInit replaces @EventHandler at runtime dynamically if it's needed
	@Mod.EventHandler
	public void preinit(FMLPreInitializationEvent pre)
	{
		DpiFixModProxy.modInit(this.getClass().getClassLoader());
		if(ForgeVersion.getMajorVersion() <= 7)
		{
			FMLModContainer container = (FMLModContainer) FMLCommonHandler.instance().findContainerFor(this);
			ModMetadata meta = container.getMetadata();
			meta.logoFile = "/" + meta.logoFile;
		}
	}

}
