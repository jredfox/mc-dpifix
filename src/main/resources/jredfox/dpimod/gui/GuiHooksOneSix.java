package jredfox.dpimod.gui;

import cpw.mods.fml.client.GuiModList;
import net.minecraft.client.gui.GuiScreen;

public class GuiHooksOneSix {
	
	public static GuiScreen hookGui(GuiScreen g)
	{
		return g instanceof GuiModList ? new GuiModListOneSix((GuiModList) g) : g;
	}

}
