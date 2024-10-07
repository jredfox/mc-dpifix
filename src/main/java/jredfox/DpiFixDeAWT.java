package jredfox;

import java.awt.Canvas;
import java.awt.Container;
import java.awt.Frame;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MinecraftApplet;

public class DpiFixDeAWT {
	
    public static void hide(Container c)
    {
    	while (c != null)
    	{
    		c.setVisible(false);
    		Container nc = c.getParent();
    		if(c == nc)
    			break;//prevent infinite loops
    		c = nc;
    	}
    }
    
    public static void dispose(Container c)
    {
    	while (c != null)
    	{
    		if(c instanceof Frame)
    			((Frame)c).dispose();
    		Container nc = c.getParent();
    		if(c == nc)
    			break;//prevent infinite loops
    		c = nc;
    	}
    }
    
	public static void hide(Canvas canvas, MinecraftApplet applet) 
	{  
		if(canvas != null) 
		{
			Container c = canvas.getParent();
			hide(c);
			dispose(c);
			dispose(c);
		}
		
		if(applet != null)
			hide(applet);
	}
    
    public static void hide(Minecraft mc)
    {
    	hide(mc.mcCanvas, mc.mcApplet);
    }

}
