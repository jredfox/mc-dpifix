package jredfox.dpimod;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import jml.gamemodelib.GameModeLib;
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
		//The Coremod not loading can only means they are in 1.5x or they deleted or modified the meta-inf or are in java agent only mode while forge is loaded
		if(!DpiFix.coremodLoaded)
		{
			if(DpiFix.onefive && GameModeLib.isInMods())
				throw new IllegalArgumentException("Dpi-Fix Mod Must be put in your coremods Folder!");
			else if(!DpiFix.agentmode)
				throw new IllegalArgumentException("Dpi-Fix CoreMod Cannot Be Loaded! Someone Must have Tampered with the META-INF. PLEASE RE-INSTALL DPI-Fix Mod");
			else
				System.err.println("Dpi-Fix is in Java Agent Only Mode! This Means all CoreMod Functionality and All @Mod 1.5x Functionality Does not Work on Forge");
		}
	}

//	@PreInit replaces @EventHandler at runtime dynamically if it's needed
	@Mod.EventHandler
	public void preinit(FMLPreInitializationEvent pre)
	{
		DpiFixModProxy.modInit(this.getClass().getClassLoader());
		if(ForgeVersion.getMajorVersion() <= 7)
		{
			ModContainer container = (ModContainer) FMLCommonHandler.instance().findContainerFor(this);
			ModMetadata meta = container.getMetadata();
			meta.logoFile = "/" + meta.logoFile;
		}
	}
	
	@Mod.EventHandler
	public void modinit(FMLInitializationEvent e)
	{
		//Patch 1.6x Mod's Logo Path since they may still try to use the 1.5.2 format of prepending the "/"
		if(DpiFix.modLogoFix && ForgeVersion.getMajorVersion() > 7 && ForgeVersion.getMajorVersion() < 10)
		{
			for(ModContainer con : Loader.instance().getModList())
			{
				ModMetadata meta = con.getMetadata();
				if(meta.logoFile == null)
				{
					meta.logoFile = "";
					continue;
				}
				
				String logoFile = meta.logoFile;
				if(logoFile.startsWith("/"))
				{
					logoFile = logoFile.substring(1).replace("\"", "").replace("'", "").trim();
					if(!logoFile.isEmpty())
						meta.logoFile = logoFile;
				}
			}
		}
	}

}
