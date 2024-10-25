package jredfox.dpimod;

import java.awt.Color;
import java.awt.Frame;
import java.lang.reflect.Field;

import javax.swing.JFrame;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.FMLModContainer;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import jredfox.DpiFix;
import jredfox.OptifineCompat;
import net.minecraftforge.common.ForgeVersion;

@Mod(
		modid = DpiFixModVars.MODID,
		name =  DpiFixModVars.NAME,
		version = DpiFixModVars.VERSION,
		acceptedMinecraftVersions = "",
		acceptableRemoteVersions = "*"
	)
public class DpiFixModLegacy {
	
	public DpiFixModLegacy() throws Exception
	{
		//The Coremod not loading can only mean one of two things they are in 1.5x or they deleted or modified the meta-inf
		if(!DpiFix.coremodLoaded)
			throw new IllegalArgumentException("Dpi-Fix Mod Must be put in your coremods Folder!");//GuiModList
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
	
//	@Init replaces @EventHandler at runtime dynamically if it's needed
	@Mod.EventHandler
	public void dpifixinit(FMLInitializationEvent pre)
	{
		if(ForgeVersion.getMajorVersion() <= 7 && DpiFix.fixLogoPaths)
		{
			for(ModContainer container : Loader.instance().getModObjectList().keySet())
			{
				ModMetadata meta = container.getMetadata();
				if(!meta.logoFile.startsWith("/"))
				{
					meta.logoFile = "/" + meta.logoFile;
					System.out.println("Patched Logo Path:" + meta.name + " logo:" + meta.logoFile);
				}
			}
		}
	}

}
