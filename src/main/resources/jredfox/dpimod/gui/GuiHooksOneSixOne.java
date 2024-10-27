package jredfox.dpimod.gui;

import cpw.mods.fml.client.GuiModList;
import net.minecraft.client.gui.GuiScreen;

public class GuiHooksOneSixOne {
	
	public static GuiScreen hookGui(GuiScreen g)
	{
		return g instanceof GuiModList ? new GuiModListOneSixOne((GuiModList) g) : g;
	}

}
