package jredfox.dpimod.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import javax.imageio.ImageIO;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.client.TextureFXManager;
import cpw.mods.fml.common.ModContainer;
import jml.gamemodelib.GameModeLib;
import jredfox.DpiFix;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderEngine;

public class GuiModListOneFive {
	
    public static Dimension cachedDim = null;
    public static BufferedImage cachedImg = null;
    public static int cachedImgGL = 0;
    
    public static final Dimension missingDim = new Dimension(1, 1);//GuiModList
    public static final BufferedImage missingo = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    
    static
    {
        Graphics2D g2d = missingo.createGraphics();
        g2d.setColor(new Color(0, 0, 0, 0)); // RGBA (0, 0, 0, 0) is fully transparent
        g2d.fillRect(0, 0, 1, 1);
        g2d.dispose();
    }
    
    public static Dimension getDim(String logoFile, ModContainer container)
    {
		RenderEngine manager = Minecraft.getMinecraft().renderEngine;
		if(cachedDim == null)
		{
			InputStream in = null;
			try
			{
				//shave off the full path as we are not using the system class loader
				if(logoFile.startsWith("/"))
					logoFile = logoFile.substring(1).replace("\"", "").replace("'", "").trim();
				//break statement to handle dumb mods
				if(logoFile.isEmpty())
					throw new FileNotFoundException();
				
				if(container != null && container.getMod() != null)
				{
					try
					{
						Class containerClazz = container.getMod().getClass();
						File jar = GameModeLib.getFileFromClass(containerClazz);
						if(jar.getPath().endsWith(".jar") || jar.getPath().endsWith(".zip"))
						{
							URL url = new URL("jar:" + jar.toURI().toURL().toString().replace("file:///", "file:/").replace("file://", "file:/") + "!/" + logoFile);
					        URLConnection connection = url.openConnection();
					        in = connection.getInputStream();
						}
						else if(jar.getPath().endsWith(".class"))
						{
							File jarDir = new File(GameModeLib.getDerpJar(jar, containerClazz), logoFile);
							if(jarDir.exists())
								in = new FileInputStream(jarDir);
						}
					}
					catch(IOException io)
					{
						//Don't print file not found exceptions
					}
					catch(Exception e)
					{
						e.printStackTrace();
					}
				}
				
				//Handle dummy mods
				if(in == null)
					in = manager.texturePack.getSelectedTexturePack().getResourceAsStream("/" + logoFile);
				
				BufferedImage img = ImageIO.read(in);
				cachedImg = img;
				cachedDim = new Dimension(img.getWidth(), img.getHeight());
				cachedImgGL = manager.allocateAndSetupTexture(cachedImg);
			}
			catch(IOException io)
			{
				missingTexture();
			}
			catch(IllegalArgumentException i)
			{
				missingTexture();
			}
			catch(Exception e)
			{
				e.printStackTrace();
				missingTexture();
			}
			finally
			{
				DpiFix.closeQuietly(in);
			}
		}
		if(cachedDim != null && cachedImgGL != 0)
		{
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, cachedImgGL);
            manager.resetBoundTexture();
		}
		return cachedDim;
	}

	private static void missingTexture() 
	{
		cachedDim = missingDim;
		cachedImg = missingo;
		cachedImgGL = Minecraft.getMinecraft().renderEngine.allocateAndSetupTexture(cachedImg);
	}

	public static void cleanup() 
	{
        cachedDim = null;
        cachedImg = null;
        if(cachedImgGL != 0)
        	deleteTexture(cachedImgGL);
        cachedImgGL = 0;
        System.gc();
	}
	
    /**
     * Deletes a texture only Allocated with BufferedImage
     */
    public static void deleteTexture(int textureId)
    {
    	RenderEngine manager = Minecraft.getMinecraft().renderEngine;
        manager.textureNameToImageMap.removeObject(textureId);
        TextureFXManager.instance().texturesById.remove(textureId);
        GL11.glDeleteTextures(textureId);
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
