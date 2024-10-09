package jredfox;

import java.awt.Canvas;
import java.awt.Container;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.util.List;

import javax.imageio.ImageIO;

import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MinecraftApplet;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.Tessellator;

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
    
    public static String title = null;
    
    /**
     * Fix icons in De-AWT for non macOS
     */
	public static void fixIcons(Minecraft mc)
	{
		//Get the Top Frame for icons
		Container c = mc.mcCanvas == null ? null : mc.mcCanvas.getParent();
		Frame frame = null;
    	while (c != null)
    	{
    		if(c instanceof Frame)
    			frame = (Frame) c;
    		c = c.getParent();
    	}
    	
    	//Error has happened has another mod already De-AWT before US?
    	if(frame == null)
    		return;
    	
        //Set the Title
        title = frame.getTitle();
    	
		//Don't set Icons on macOS as that's the Xdocer arguments
        if (DpiFix.isMacOs)
        	return;
        
    	List<Image> list = frame.getIconImages();
    	if(list != null && !list.isEmpty()) 
    	{
    		Image img = null;
    		//Get Highest Resolution for Icon
	    	for(Image i : list)
	    	{
	    		if(img == null || i.getWidth(null) > img.getWidth(null) || i.getHeight(null) > img.getHeight(null))
	    			img = i;
	    	}
            try
            {
        		BufferedImage icon = convertToBufferedImage(img);//Convert to BufferedImage Required for next step
        		System.out.println("Icon Found:" + icon.getWidth() + "x" + icon.getHeight());
            	ByteBuffer bufIcon16x16 = readImage(resizeIcon(icon, 16, 16));
            	ByteBuffer bufIcon32x32 = readImage(resizeIcon(icon, 32, 32));
                Display.setIcon(new ByteBuffer[] {bufIcon16x16 ,  bufIcon32x32});
            }
            catch (Exception ioexception)
            {
                ioexception.printStackTrace();
            }
	    	return;
    	}
    	
		//Dynamically Download the Icons if required
		File fileAssets = new File(mc.getMinecraftDir().getAbsoluteFile(), "assets");
		File icon16 = new File(fileAssets, "icons/icon_16x16.png");
		File icon32 = new File(fileAssets, "icons/icon_32x32.png");
		File icon =   new File(fileAssets, "icons/minecraft.icns");
		if(!icon16.exists())
		{
			String url_16 =   "https://resources.download.minecraft.net/bd/bdf48ef6b5d0d23bbb02e17d04865216179f510a";
			String url_32 =   "https://resources.download.minecraft.net/92/92750c5f93c312ba9ab413d546f32190c56d6f1f";
			String url_icon = "https://resources.download.minecraft.net/99/991b421dfd401f115241601b2b373140a8d78572";
			dl(url_16, icon16);
			dl(url_32, icon32);
			dl(url_icon, icon);
		}
		
        try
        {
            Display.setIcon(new ByteBuffer[] {readImage(icon16), readImage(icon32)});
        }
        catch (IOException ioexception)
        {
            ioexception.printStackTrace();
        }
	}
	
	public static void fixTitle()
	{
		if(title != null && !title.isEmpty())
			Display.setTitle(title);
	}
	
    public static BufferedImage convertToBufferedImage(Image img) {
        if (img instanceof BufferedImage) {
            return (BufferedImage) img;  // If it's already a BufferedImage, return it directly
        }

        // Create a BufferedImage with the same width, height, and transparency type as the Image
        BufferedImage bufferedImage = new BufferedImage(
                img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);

        // Draw the original Image onto the new BufferedImage
        Graphics2D g2d = bufferedImage.createGraphics();
        g2d.drawImage(img, 0, 0, null);
        g2d.dispose();

        return bufferedImage;
    }
	
    public static BufferedImage resizeIcon(BufferedImage originalIcon, int targetWidth, int targetHeight) 
    {
    	if(originalIcon.getWidth() == targetWidth && originalIcon.getHeight() == targetHeight)
    		return originalIcon;
    	
        // Create a new BufferedImage for the resized icon
        BufferedImage resizedIcon = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);

        // Draw the original image resized to the new dimensions
        Graphics2D g2d = resizedIcon.createGraphics();
        Image scaledImage = originalIcon.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH | Image.SCALE_AREA_AVERAGING);
        g2d.drawImage(scaledImage, 0, 0, null);
        g2d.dispose();

        return resizedIcon;
    }
    
    private static ByteBuffer readImage(File par1File) throws IOException
    {
        BufferedImage bufferedimage = ImageIO.read(par1File);
        return readImage(bufferedimage);
    }
    
	public static ByteBuffer readImage(BufferedImage bufferedimage)
	{
        int[] aint = bufferedimage.getRGB(0, 0, bufferedimage.getWidth(), bufferedimage.getHeight(), (int[])null, 0, bufferedimage.getWidth());
        ByteBuffer bytebuffer = ByteBuffer.allocate(4 * aint.length);
        int[] aint1 = aint;
        int i = aint.length;

        for (int j = 0; j < i; ++j)
        {
            int k = aint1[j];
            bytebuffer.putInt(k << 8 | k >> 24 & 255);
        }

        bytebuffer.flip();
        return bytebuffer;
	}

	public static void dl(String sURL, File output)
	{
		output.getParentFile().getAbsoluteFile().mkdirs();
		URL url = null;
		URLConnection con = null;
		try
		{
			url = new URL(sURL);
			con = url.openConnection();
			con.setRequestProperty("User-Agent", "Mozilla");
			con.setConnectTimeout(1000 * 15);
			InputStream inputStream = con.getInputStream();
			directDL(inputStream, output);
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			if(con instanceof HttpURLConnection)
				((HttpURLConnection)con).disconnect();
		}
	}
    
	/**
	 * direct dl with safegaurds of corrupted downloads. it's private so you you call the other method to fix -1 timestamps
	 * @throws Exception 
	 */
	private static void directDL(InputStream inputStream, File output)
	{
		try
		{
			output.getParentFile().mkdirs();
			DpiFix.copy(inputStream, new FileOutputStream(output));
		}
		catch(Exception e)
		{
			if(output.exists())
				output.delete();
			
			e.printStackTrace();
		}
	}

    public static void loadScreen() throws LWJGLException
    {
    	Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution scaledresolution = new ScaledResolution(mc.gameSettings, mc.displayWidth, mc.displayHeight);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0D, scaledresolution.getScaledWidth_double(), scaledresolution.getScaledHeight_double(), 0.0D, 1000.0D, 3000.0D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glTranslatef(0.0F, 0.0F, -2000.0F);
        GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight);
        GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_FOG);
        mc.renderEngine.bindTexture("/title/mojang.png");
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorOpaque_I(16777215);
        tessellator.addVertexWithUV(0.0D, (double)mc.displayHeight, 0.0D, 0.0D, 0.0D);
        tessellator.addVertexWithUV((double)mc.displayWidth, (double)mc.displayHeight, 0.0D, 0.0D, 0.0D);
        tessellator.addVertexWithUV((double)mc.displayWidth, 0.0D, 0.0D, 0.0D, 0.0D);
        tessellator.addVertexWithUV(0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
        tessellator.draw();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        tessellator.setColorOpaque_I(16777215);
        short short1 = 256;
        short short2 = 256;
        mc.scaledTessellator((scaledresolution.getScaledWidth() - short1) / 2, (scaledresolution.getScaledHeight() - short2) / 2, 0, 0, short1, short2);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        DpiFixCoreMod.tickDisplay(mc);
    }

	public static void setDisplayMode(Minecraft mc) throws LWJGLException 
	{
    	Display.setDisplayMode(new DisplayMode(mc.tempDisplayWidth, mc.tempDisplayHeight));
	}

}
