package jredfox;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.ForgeVersion;

public class DpiFixProxy {
	
	public static boolean oldGuiScreen = ForgeVersion.getMajorVersion() < 10;
	
    /**
     * Will set the focus to ingame if the Minecraft window is the active with focus. Also clears any GUI screen
     * currently displayed
     */
    public static void setIngameFocus(Minecraft mc)
    {
    	if (!mc.inGameHasFocus)
        {
            mc.inGameHasFocus = true;
            mc.mouseHelper.grabMouseCursor();
            if(oldGuiScreen)
            	mc.func_71373_a((GuiScreen)null);
            else
            	mc.displayGuiScreen((GuiScreen)null);
            mc.leftClickCounter = 10000;
        }
    }
    
    public static void setIngameNotInFocus(Minecraft mc)
    {
        if (mc.inGameHasFocus)
        {
            mc.inGameHasFocus = false;
            mc.mouseHelper.ungrabMouseCursor();
        }
    }

	public static void syncFullScreen()
	{
		Minecraft mc = Minecraft.getMinecraft();
		mc.gameSettings.fullScreen = !mc.isFullScreen();//1.6.4 doesn't have AT's so use the getter method and hope it's not overriden
		mc.gameSettings.saveOptions();
	}

}
