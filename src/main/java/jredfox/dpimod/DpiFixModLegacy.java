package jredfox.dpimod;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.FMLModContainer;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.common.ForgeVersion;

@Mod(
		modid = "dpi-fix", 
		name = "DPI-Fix", 
		version = "1.5.0", 
		acceptedMinecraftVersions = "",
		acceptableRemoteVersions = "*"
	)
public class DpiFixModLegacy {
	
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
