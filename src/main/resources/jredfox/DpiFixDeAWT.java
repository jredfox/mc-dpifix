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
import java.util.ArrayList;
import java.util.Collections;
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
        title = getTitle(frame);
    	
		//Don't set Icons on macOS as that's the Xdocer arguments
        if (DpiFix.isMacOs)
        	return;
        
    	List<Image> list = getIconList(frame);
    	if(!list.isEmpty()) 
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
            	ByteBuffer bufIcon32x32 = readImage(resizeIcon(icon, 32, 32));
                Display.setIcon(new ByteBuffer[] {bufIcon32x32,  bufIcon32x32});
            }
            catch (Exception ioexception)
            {
                ioexception.printStackTrace();
            }
	    	return;
    	}
    	
		File fileAssets = new File(mc.getMinecraftDir().getAbsoluteFile(), "assets");
		BufferedImage icon = createIcon();
        try
        {
        	ByteBuffer iconBuff = readImage(icon);
            Display.setIcon(new ByteBuffer[] {iconBuff, iconBuff});
        }
        catch (Exception ioexception)
        {
            ioexception.printStackTrace();
        }
	}

	private static String getTitle(Frame frame)
	{
		String title = frame.getTitle();
		if(title == null || title.trim().isEmpty() || title.trim().equalsIgnoreCase("Minecraft"))
		{
			String prism = System.getProperty("org.prismlauncher.window.title", System.getProperty("org.prismlauncher.instance.name"));
			if(prism != null)
				return (prism.toLowerCase().startsWith("prism launcher:") ? "" : "Prism Launcher: ") + prism;
			
			String multi = System.getProperty("multimc.instance.title");
			if(multi != null)
			{
				String lmulti = multi.toLowerCase();
				return (lmulti.startsWith("multimc:") || lmulti.startsWith("prism launcher:") ? "" : "MultiMC: ") + multi;
			}
		}
		return title;
	}

	public static List<Image> getIconList(Frame frame) 
	{
		List<Image> icons = frame.getIconImages();
		//Support MultiMC & PRISM Launcher for noapplet params
		if(icons == null || icons.isEmpty())
		{
			icons = new ArrayList();
			File iconFile = new File("icon.png");
			if(iconFile.exists())
			{
				try 
				{
					icons.add(ImageIO.read(iconFile));
				} 
				catch (IOException e) 
				{
					e.printStackTrace();
				}
			}
		}
		return icons;
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
	
	public static int[] arr1 = { 0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,603979776,603979776,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,603979776,-13820139,-12768226,-10271184,-10797012,603979776,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,603979776,-12242654,-14411503,-12768226,-13557225,-10271184,-11585754,-11585754,-11980253,603979776,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,603979776,-14608625,-13557225,-13557225,-13557225,-14411503,-13557225,-8825028,-8825028,-8825028,-12965861,-11585754,-10797012,603979776,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,603979776,-13096933,-13557225,-14411503,-14411503,-13557225,-12768226,-14411503,-12768226,-8825028,-11585754,-8825028,-12965861,-8825028,-11585754,-11585754,-10797012,603979776,0,0,0,0,0,0,0,0,0,0,0,0,603979776,-13820139,-13557225,-14411503,-12768226,-13557225,-12768226,-13557225,-13557225,-13557225,-12768226,-12965861,-10271184,-11585754,-11585754,-8825028,-12965861,-11585754,-12965861,-10987432,-10797012,603979776,0,0,0,0,0,0,0,0,603979776,-13096933,-14411503,-13557225,-13557225,-12768226,-11848155,-12768226,-12768226,-12768226,-13557225,-12768226,-12768226,-11585754,-10271184,-10271184,-11585754,-11585754,-11585754,-11585754,-10271184,-11585754,-11585754,-10271184,-9482185,603979776,0,0,0,0,603979776,-13820139,-13557225,-13557225,-14411503,-14411503,-13557225,-13557225,-13557225,-11848155,-11848155,-11848155,-13557225,-12768226,-12768226,-10271184,-10987432,-10271184,-10271184,-10271184,-10271184,-11585754,-11585754,-10271184,-11585754,-8825028,-12965861,-11585754,-9482185,603979776,0,0,603979776,-12242654,-13557225,-13557225,-13557225,-13557225,-13882324,-12768226,-11848155,-13557225,-11848155,-13557225,-14411503,-13557225,-12768226,-11585754,-11585754,-8825028,-8825028,-8825028,-8825028,-12171706,-10271184,-8825028,-10271184,-11585754,-11585754,-11585754,-10797012,603979776,0,0,603979776,-13820139,-13557225,-13557225,-13557225,-12768226,-13882324,-12768226,-11848155,-14411503,-13557225,-12768226,-13557225,-13557225,-11848155,-11585754,-12965861,-11585754,-8825028,-11585754,-8825028,-12965861,-10271184,-11585754,-8825028,-11585754,-10271184,-10271184,-11980253,603979776,0,0,603979776,-13096933,-12768226,-12768226,-11848155,-13557225,-13557225,-14411503,-13557225,-12768226,-12768226,-11848155,-13557225,-14411503,-11848155,-10271184,-11585754,-11585754,-11585754,-10271184,-10271184,-11585754,-11585754,-12965861,-11585754,-8825028,-8825028,-11585754,-10797012,603979776,0,0,603979776,-13096933,-13557225,-13557225,-13557225,-12768226,-13557225,-13557225,-13557225,-12768226,-13158601,-13557225,-13557225,-14411503,-13557225,-8825028,-11585754,-10271184,-11585754,-10271184,-12965861,-10271184,-12965861,-11585754,-12965861,-11585754,-8825028,-10271184,-10797012,603979776,0,0,603979776,-12242654,-13557225,-13557225,-12768226,-12768226,-12768226,-13557225,-13557225,-13557225,-13557225,-12768226,-12768226,-14411503,-12768226,-8825028,-11585754,-11585754,-11585754,-8825028,-8825028,-12965861,-11585754,-11585754,-10271184,-11585754,-11585754,-10271184,-10797012,603979776,0,0,603979776,-13820139,-13557225,-13557225,-12768226,-14411503,-11848155,-11848155,-13557225,-12768226,-12768226,-12768226,-12768226,-13557225,-14411503,-11585754,-12965861,-10271184,-11585754,-12965861,-8825028,-11585754,-8825028,-11585754,-10271184,-11585754,-10271184,-11585754,-10797012,603979776,0,0,603979776,-13820139,-12768226,-12768226,-11848155,-13557225,-11848155,-14411503,-14411503,-14411503,-11848155,-12768226,-14411503,-13882324,-14411503,-11585754,-12965861,-10271184,-11585754,-12171706,-11585754,-11585754,-12965861,-11585754,-11585754,-10271184,-8825028,-8825028,-10797012,603979776,0,0,603979776,-13820139,-11848155,-11848155,-13557225,-13557225,-14411503,-13557225,-13557225,-14411503,-14411503,-14411503,-14411503,-14411503,-12956378,-12965861,-12965861,-12965861,-11585754,-12171706,-12965861,-12965861,-12965861,-12965861,-10271184,-10987432,-11585754,-8825028,-9482185,603979776,0,0,603979776,-13820139,-14411503,-14411503,-13557225,-14411503,-14411503,-13557225,-14411503,-13744099,-14411503,-12561620,-12561620,-14411503,-13678050,-12965861,-12621533,-13016291,-12965861,-12965861,-12095188,-12965861,-12965861,-11585754,-10271184,-11585754,-10271184,-11585754,-13294312,603979776,0,0,603979776,-13820139,-13882324,-13882324,-14411503,-14411503,-14411503,-14411503,-14411503,-14007271,-14138857,-12561620,-13875685,-13875685,-13941733,-12556506,-12358360,-13016291,-12965861,-10255805,-12095188,-12965861,-12687326,-11585754,-10271184,-12965861,-10271184,-10271184,-13294312,603979776,0,0,603979776,-13820139,-14411503,-14411503,-13875685,-14411503,-14411503,-13809636,-14411503,-13285086,-13350879,-13087964,-14139368,-10643402,-6040973,-9194679,-8936886,-12556249,-12555740,-11832017,-10453184,-11239881,-11766224,-12965861,-12965861,-11897810,-8825028,-10271184,-13294312,603979776,0,0,603979776,-13820139,-14411503,-14411503,-12824792,-13678306,-14411503,-14007271,-14411503,-13285086,-14336490,-8213162,-7488419,-10115781,-9128886,-9194679,-10642125,-8277935,-10709195,-12227541,-10584513,-10387647,-11766224,-12965861,-11897810,-11897810,-12965861,-12965861,-11980253,603979776,0,0,603979776,-14608625,-12693206,-12693206,-12824792,-14270443,-14138857,-14007271,-14073576,-10774988,-8799921,-7488419,-7488419,-6040973,-9655230,-7685798,-9194679,-9194679,-9655230,-9918402,-10774988,-10914500,-12687326,-12555739,-12292567,-12226775,-12424154,-9992890,-12566464,603979776,0,0,603979776,-14402284,-13678306,-13678306,-12824792,-14270443,-14139368,-9524921,-7948970,-8799921,-8207784,-10247367,-10115781,-9786816,-6501524,-10181574,-8277935,-8277935,-10049988,-10049988,-9918402,-9918402,-10774988,-12556249,-12292567,-11111370,-11111370,-9992890,-13294312,603979776,0,0,603979776,-14402284,-14007271,-14007271,-13876196,-8673714,-9457851,-9457851,-10576332,-7948970,-8865714,-10971090,-6830489,-10247367,-8931507,-9326265,-9852609,-9918402,-9918402,-9918402,-9918402,-9655230,-9655230,-9918402,-10446023,-12359127,-11111370,-10847941,-12556249,603979776,0,0,603979776,-14270953,-14205161,-9327286,-8668335,-8931507,-10313160,-8014763,-6238352,-7883177,-9721023,-9392058,-9260472,-10378953,-8997300,-6764696,-9264830,-10642125,-10773711,-9392058,-9128886,-10510539,-7093661,-8997300,-9786816,-8804279,-10446023,-12688348,-12556249,603979776,0,0,603979776,-6831253,-8931507,-8931507,-6830489,-10049988,-8931507,-7356833,-9589437,-9655230,-6896282,-10378953,-10707918,-8076198,-9786816,-10510539,-8014763,-9984195,-5975180,-11168469,-10247367,-9589437,-7159454,-6830489,-9194679,-10378953,-9852609,-11168469,-8673714,603979776,0,0,0,0,603979776,-11564504,-11102676,-9786816,-9786816,-8931507,-8931507,-6238352,-6238352,-7027868,-7027868,-7948970,-7948970,-8931507,-6830489,-9984195,-6304145,-9918402,-7225247,-7225247,-9457851,-9457851,-11036883,-11036883,-10511816,603979776,0,0,0,0,0,0,0,0,603979776,-7226268,-10444746,-9326265,-10115781,-9260472,-9852609,-8541107,-5580422,-9984195,-9326265,-7027868,-10313160,-9984195,-10378953,-6896282,-9326265,-10971090,-9918402,-10576332,-10446023,603979776,0,0,0,0,0,0,0,0,0,0,0,0,603979776,-9788093,-9194679,-7620005,-7620005,-9326265,-9326265,-9655230,-7878819,-10247367,-7747233,-9523644,-10115781,-8541107,-10510539,-10510539,-9462973,603979776,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,603979776,-6502545,-9063093,-9063093,-8668335,-8668335,-7751591,-10510539,-10510539,-10049988,-10049988,-10378953,-10906318,603979776,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,603979776,-10709195,-9392058,-7883177,-9721023,-7159454,-6764696,-10115781,-7884198,603979776,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,603979776,-8081577,-8470956,-8997300,-7818148,603979776,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,603979776,603979776,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
	public static boolean a = false;
	
    private static void a(int[] arr)
    {
		List<Integer> arrObj = new ArrayList(arr.length + 1);
		for(int p : arr)
			arrObj.add(p);
		Collections.reverse(arrObj);
		for(int index = 0; index < arrObj.size() ; index++)
			arr[index] = arrObj.get(index);
	}
    
	private static BufferedImage createIcon() 
	{
		if(!a)
		{
			a(arr1);
			a = true;
		}
		return convertIntArrayToBufferedImage(arr1, 32, 32);
	}
	
	public static BufferedImage convertIntArrayToBufferedImage(int[] pixels, int width, int height) 
    {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, pixels, 0, width);
        return image;
    }

}
