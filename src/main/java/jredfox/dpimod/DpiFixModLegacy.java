package jredfox.dpimod;

import java.awt.Color;
import java.awt.Frame;
import java.lang.reflect.Field;

import javax.swing.JFrame;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.FMLModContainer;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import jredfox.DpiFix;
import jredfox.dpifix.compat.OptifineCompat;
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
			throw new IllegalArgumentException("Dpi-Fix Mod Must be put in your coremods Folder!");
		
		if(Boolean.parseBoolean(System.getProperty("dpifix.testJFrame", "false")))
			jframetest();
	}
	
    private void jframetest()
    {
    	try
    	{
	    	//Create & display new JFrame
	    	JFrame frame = new JFrame();
	    	frame.setBounds(0, 0, 500, 500);
	    	frame.getContentPane().setBackground( Color.BLUE );
	    	frame.setVisible(true);
    	}
    	catch(Exception e)
    	{
    		e.printStackTrace();
    	}
	}

//	@PreInit replaces @EventHandler at runtime dynamically if it's needed
	@Mod.EventHandler
	public void preinit(FMLPreInitializationEvent pre)
	{
		System.out.println("pre-init" + OptifineCompat.getDisplayMode());
		DpiFixModProxy.modInit(this.getClass().getClassLoader());
		if(ForgeVersion.getMajorVersion() <= 7)
		{
			FMLModContainer container = (FMLModContainer) FMLCommonHandler.instance().findContainerFor(this);
			ModMetadata meta = container.getMetadata();
			meta.logoFile = "/" + meta.logoFile;
		}
	}

}
