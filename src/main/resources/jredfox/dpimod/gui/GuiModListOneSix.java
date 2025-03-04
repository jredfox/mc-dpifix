package jredfox.dpimod.gui;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import javax.imageio.ImageIO;

import org.lwjgl.opengl.GL11;

import com.google.common.base.Strings;

import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.client.GuiModList;
import cpw.mods.fml.client.GuiSlotModList;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSmallButton;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureObject;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.ResourcePack;
import net.minecraft.util.ResourceLocation;

public class GuiModListOneSix extends GuiModList
{
    public BufferedImage cachedLogo;
    public Dimension cachedDim;
    public ResourceLocation cachedLoc;
    
    public GuiModListOneSix(GuiModList ml)
    {
        super(ml.mainMenu);
    }

    @Override
    public void drawScreen(int p_571_1_, int p_571_2_, float p_571_3_)
    {
        this.modList.drawScreen(p_571_1_, p_571_2_, p_571_3_);
        this.drawCenteredString(this.fontRenderer, "Mod List", this.width / 2, 16, 0xFFFFFF);
        int offset = this.listWidth  + 20;
        if (selectedMod != null) {
            GL11.glEnable(GL11.GL_BLEND);
            if (!selectedMod.getMetadata().autogenerated) {
                int shifty = 35;
                String logoFile = selectedMod.getMetadata().logoFile;
                if (!logoFile.isEmpty())
                {
                    GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                    TextureManager tm = mc.renderEngine;
                    ResourcePack pack = FMLClientHandler.instance().getResourcePackFor(selectedMod.getModId());
                    try
                    {
                    	if(this.cachedLoc == null)
                    	{
                    		try
                    		{
                				//shave off the full path as we are not using the system class loader
                				if(logoFile.startsWith("/"))
                					logoFile = logoFile.substring(1).replace("\"", "").replace("'", "").trim();
                				//break statement to handle dumb mods
                				if(logoFile.isEmpty())
                					throw new FileNotFoundException();
                				
                				if(pack != null)
                				{
                					try
                					{
                						this.cachedLogo = pack.getPackImage();
                					}
                					catch(IOException io)
                					{
                						
                					}
                					catch(Exception e)
                					{
                						e.printStackTrace();
                					}
                				}
                				
                				if(cachedLogo == null)
                					cachedLogo = this.readImg(logoFile);
                				
                    			cachedDim = new Dimension(cachedLogo.getWidth(), cachedLogo.getHeight());
                    			cachedLoc = tm.getDynamicTextureLocation("modlogo", new DynamicTexture(this.cachedLogo));
                    		}
                    		catch(IOException e)
                    		{
                    			missingTexture();
                    		}
                    		catch(IllegalArgumentException ill)
                    		{
                    			missingTexture();
                    		}
                    		catch(Exception e)
                    		{
                    			e.printStackTrace();
                    			missingTexture();
                    		}
                    	}
                    	
                        if (this.cachedLogo != null && this.cachedLoc != missing_texture)
                        {
                            this.mc.renderEngine.bindTexture(this.cachedLoc);
                            Dimension dim = this.cachedDim;
                            double scaleX = dim.width / 200.0;
                            double scaleY = dim.height / 65.0;
                            double scale = 1.0;
                            if (scaleX > 1 || scaleY > 1)
                            {
                                scale = 1.0 / Math.max(scaleX, scaleY);
                            }
                            dim.width *= scale;
                            dim.height *= scale;
                            int top = 32;
                            Tessellator tess = Tessellator.instance;
                            tess.startDrawingQuads();
                            tess.addVertexWithUV(offset,             top + dim.height, zLevel, 0, 1);
                            tess.addVertexWithUV(offset + dim.width, top + dim.height, zLevel, 1, 1);
                            tess.addVertexWithUV(offset + dim.width, top,              zLevel, 1, 0);
                            tess.addVertexWithUV(offset,             top,              zLevel, 0, 0);
                            tess.draw();

                            shifty += 65;
                        }
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                }
                this.fontRenderer.drawStringWithShadow(selectedMod.getMetadata().name, offset, shifty, 0xFFFFFF);
                shifty += 12;

                shifty = drawLine(String.format("Version: %s (%s)", selectedMod.getDisplayVersion(), selectedMod.getVersion()), offset, shifty);
                shifty = drawLine(String.format("Mod ID: '%s' Mod State: %s", selectedMod.getModId(), Loader.instance().getModState(selectedMod)), offset, shifty);
                if (!selectedMod.getMetadata().credits.isEmpty()) {
                   shifty = drawLine(String.format("Credits: %s", selectedMod.getMetadata().credits), offset, shifty);
                }
                shifty = drawLine(String.format("Authors: %s", selectedMod.getMetadata().getAuthorList()), offset, shifty);
                shifty = drawLine(String.format("URL: %s", selectedMod.getMetadata().url), offset, shifty);
                shifty = drawLine(selectedMod.getMetadata().childMods.isEmpty() ? "No child mods for this mod" : String.format("Child mods: %s", selectedMod.getMetadata().getChildModList()), offset, shifty);
                int rightSide = this.width - offset - 20;
                if (rightSide > 20)
                {
                    this.fontRenderer.drawSplitString(selectedMod.getMetadata().description, offset, shifty + 10, rightSide, 0xDDDDDD);
                }
            } else {
                offset = ( this.listWidth + this.width ) / 2;
                this.drawCenteredString(this.fontRenderer, selectedMod.getName(), offset, 35, 0xFFFFFF);
                this.drawCenteredString(this.fontRenderer, String.format("Version: %s",selectedMod.getVersion()), offset, 45, 0xFFFFFF);
                this.drawCenteredString(this.fontRenderer, String.format("Mod State: %s",Loader.instance().getModState(selectedMod)), offset, 55, 0xFFFFFF);
                this.drawCenteredString(this.fontRenderer, "No mod information found", offset, 65, 0xDDDDDD);
                this.drawCenteredString(this.fontRenderer, "Ask your mod author to provide a mod mcmod.info file", offset, 75, 0xDDDDDD);
            }
            GL11.glDisable(GL11.GL_BLEND);
        }
        this.drawButtons(p_571_1_, p_571_2_, p_571_3_);
    }

    public BufferedImage readImg(String logoFile) throws Exception
    {
    	InputStream in = null;
    	try
    	{
    		in = this.getClass().getClassLoader().getResourceAsStream(logoFile);
    		return ImageIO.read(in);
    	}
    	catch(Exception t)
    	{
    		throw t;
    	}
    	finally
    	{
    		if(in != null)
    		{
    			try 
    			{
					in.close();
				} catch (Exception e) {}
    		}
    	}
	}

	public static final ResourceLocation missing_texture = new ResourceLocation("dpi-fix", "missing_texture");
    public void missingTexture() 
    {
		this.cachedLoc = missing_texture;
	}

    @Override
    public void selectModIndex(int var1)
    {
        super.selectModIndex(var1);
        this.cleanup();
    }
    
    @Override
    public void onGuiClosed()
    {
    	super.onGuiClosed();
    	this.cleanup();
    }

    public void cleanup() 
    {
    	if(this.cachedLoc != null && this.cachedLoc != missing_texture)
    		this.deleteTexture(this.cachedLoc);
    	this.cachedLoc = null;
    	this.cachedDim = null;
    	this.cachedLogo = null;
    	System.gc();
	}
    
    public void deleteTexture(ResourceLocation textureLocation)
    {
    	TextureManager manager = this.mc.renderEngine;
        TextureObject itextureobject = manager.getTexture(textureLocation);

        if (itextureobject != null)
        {
            manager.mapTextureObjects.remove(textureLocation);
            GL11.glDeleteTextures(itextureobject.getGlTextureId());
        }
    }
}
