package jredfox.dpimod.gui;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.client.TextureFXManager;
import jredfox.DpiFix;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderEngine;

public class GuiModListOneFive {
	
    public static Dimension cachedDim = null;
    public static String logoLoc = null;
    public static Dimension getDim(String logoFile)
    {
		if(cachedDim == null)
		{
			InputStream in = null;
			try
			{
				in = Minecraft.getMinecraft().renderEngine.texturePack.getSelectedTexturePack().getResourceAsStream(logoFile);
				BufferedImage img = ImageIO.read(in);
				cachedDim = new Dimension(img.getWidth(), img.getHeight());
				logoLoc = logoFile;
			}
			catch(IOException io)
			{
				io.printStackTrace();
			}
			finally
			{
				DpiFix.closeQuietly(in);
			}
		}
		return cachedDim;
	}
    
	public static void cleanup() 
	{
		if(logoLoc == null)
			return;
		
        deleteTexture(logoLoc);
        logoLoc = null;
        cachedDim = null;
        System.gc();
	}
    
    /**
     * Fully Deletes a Texture
     */
    public static void deleteTexture(String loc)
    {
    	RenderEngine manager = Minecraft.getMinecraft().renderEngine;
        Integer textureId = (Integer) manager.textureMap.get(loc);
        
    	manager.textureMap.remove(loc);
        manager.textureContentsMap.remove(loc);
        TextureFXManager.instance().texturesByName.remove(loc);

        if (textureId != null)
        {
            manager.textureNameToImageMap.removeObject(textureId);
            TextureFXManager.instance().texturesById.remove(textureId);
            GL11.glDeleteTextures(textureId);
        }
    }

}
