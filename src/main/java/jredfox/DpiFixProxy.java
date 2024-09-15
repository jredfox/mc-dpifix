package jredfox;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.ForgeVersion;

public class DpiFixProxy {
	
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
            if(ForgeVersion.getMajorVersion() < 10)
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

}
