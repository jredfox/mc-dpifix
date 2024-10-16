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
		BufferedImage[] icons = createIcons();
        try
        {
            Display.setIcon(new ByteBuffer[] {readImage(icons[0]), readImage(icons[1])});
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
	
	public static int[] arr1 = { 723204,2759435,3285262,2892562,722948,3417104,1262957851,-636219641,-634181864,1247298340,5785127,1315082,4009755,6967855,6442541,854790,788740,2825228,3087885,2300942,1628178437,-548390627,-10270939,-14344945,-13687529,-6588088,-545364677,1628639241,3221012,6704940,6508334,854790,591619,169742854,2050105615,-281794283,-14805235,-7312324,-9746393,-15200252,-14736861,-6717863,-5339835,-13228011,-297125599,2037077041,173557545,723204,1745356034,-11453665,-8757967,-11452895,-15725562,-10205407,-9744600,-14806011,-14079447,-7043205,-8100301,-14871545,-7770810,-5602482,-10138069,1712459530,-1642523637,-10204375,-10467803,-13293035,-16120060,-10333894,-8032433,-14543613,-14803686,-4080975,-7113665,-14870772,-12437728,-7576269,-6785980,-1642260207,-1642983672,-12637931,-11323618,-11649504,-16645888,-10462638,-10465219,-14675456,-15198960,-2763564,-5400975,-13886976,-11912671,-9016489,-8823241,-1642852856,-1643312635,-9218514,-9218771,-12833257,-16777216,-13621477,-11583705,-13885169,-16449280,-9738910,-7242138,-14938368,-11978210,-7435127,-7899814,-1642590205,-1643115513,-10598361,-11191777,-14083315,-14804720,-11519972,-12309229,-14870773,-14016492,-15990527,-11256799,-12702187,-9217738,-10400461,-10005449,-1641866223,-1643312635,-9940693,-9679831,-13689841,-14869745,-10402524,-10797284,-14739704,-11189460,-6259897,-8954314,-15132916,-8033725,-8166857,-10598616,-1641931758,-1642918392,-10730205,-11716837,-13622509,-16777216,-12965102,-11451608,-10332608,-9411765,-6849453,-8493766,-15925248,-11057881,-9809364,-9874387,-1642523380,-1642984184,-11256545,-10270174,-14544120,-14021875,-7706549,-5800610,-3499407,-4222101,-6260644,-5869490,-10738410,-12048109,-8296650,-8428229,-1642786805,-1642655736,-8298187,-11057361,-9153215,-2974608,-2187406,-3304857,-5013412,-5803952,-3831198,-2449807,-1925519,-6921401,-10136009,-6389940,-1641998065,1696340247,-10003898,-6518680,-1397384,-2780057,-7841471,-5473704,-5473189,-5604519,-5341606,-7314359,-3700385,-1726089,-6979229,-10332091,1679760409,4407098,138493240,1984447291,-326670257,-2844822,-2385560,-4356514,-5673137,-5013669,-3829658,-2583451,-3371934,-326538671,1967275577,137638709,4538427,4209204,4275253,3354928,2041129,1581201975,-593791399,-4946850,-3895709,-3829146,-5012642,-594054568,1564753464,2304041,3092015,3683379,4275253,4143667,4406582,4077877,4209205,2698030,2041128,1194734384,-663328431,-680105904,1177760048,1449767,2960943,4472118,3749427,3749171,4209461};
	public static int[] arr2 = { 16777215,131328,3417105,3088398,2035460,3548947,3943703,2300429,0,2300173,3745812,3417105,4074519,37563671,1346055447,-972749822,-989592830,1331053349,38813475,4272666,5785127,6245420,3550233,0,3944220,6047785,5653285,6507821,6311212,6245419,262914,16777215,16777215,131328,3417105,3088398,2035460,3548947,3943703,2300429,0,2300173,3745812,137634833,1715416087,-667013608,-12176100,-16316925,-16317181,-10203602,-682541021,1698771482,140002855,6245420,3550233,0,3944220,6047785,5653285,6507821,6311212,6245419,262914,16777215,16777215,131328,3417105,3088398,2035460,3548947,3943703,2300429,0,304290061,2084186388,-399170287,-12506089,-11321824,-9349585,-16120060,-16251132,-6390449,-7639740,-10795223,-396736728,2069843244,288762905,0,3944220,6047785,5653285,6507821,6311212,6245419,262914,16777215,16777215,131328,3417105,3088398,2035460,3548947,3943703,522394125,-1895825408,-215672306,-11519457,-10729948,-11521253,-11848164,-10729691,-16185596,-16448510,-11123418,-8756934,-6259122,-7508159,-9414092,-214422247,-1912602624,507260700,6047785,5653285,6507821,6311212,6245419,262914,16777215,16777215,131328,3417105,3088398,2035460,842409747,-1522782953,-81585650,-16777216,-12636390,-8955598,-9218512,-9878747,-10796764,-9284048,-16119804,-16382973,-9808069,-7240851,-7770306,-7244733,-6850488,-10203858,-16251648,-96653284,-1537390294,827736869,6507821,6311212,6245419,262914,16777215,16777215,131328,20194321,1160781838,-1155526652,-12965356,-11321566,-12439269,-16777216,-12570598,-9152719,-9152719,-9878491,-10402265,-12242407,-16448767,-16580095,-11977433,-4739931,-8037326,-7507391,-6456758,-10138325,-15529216,-10401235,-8362947,-10663639,-1167831507,1147227436,6245419,262914,16777215,16777215,1442971904,-835443440,-13031406,-11519971,-9809878,-9809621,-12242149,-16777216,-12570598,-9940949,-10993117,-12373735,-12307685,-10796253,-16185596,-16448510,-11122897,-8224900,-13557233,-8756424,-7770305,-10860762,-15595008,-10072528,-5930414,-7310268,-9547218,-10203347,-849326804,1426326274,16777215,16777215,-16580095,-10533338,-9087183,-9612755,-9941207,-9547221,-12701928,-16777216,-13885423,-12834028,-12308200,-10401754,-9415892,-9875414,-16185596,-16448510,-10530763,-6119265,-7437191,-9480141,-10137298,-12175586,-16251904,-10400720,-6061999,-6127792,-8365261,-8165318,-7770813,-16448510,16777215,16777215,-16514303,-9743828,-10335193,-10533339,-10466521,-10729435,-13621997,-16777216,-14279666,-10335704,-9546962,-9741241,-10202572,-9940949,-16120060,-16448510,-10465226,-5658714,-4213333,-6851257,-7573951,-12438242,-16777216,-11781083,-7638974,-6455988,-7050949,-9743569,-7113145,-16382717,16777215,16777215,-16514303,-10138070,-12111077,-11324900,-11912931,-12701927,-14016495,-16777216,-13819118,-10201532,-12239828,-9804964,-8888254,-9809875,-16251389,-16448510,-10465482,-5856093,-5002076,-9677006,-7507391,-11255004,-16251904,-12898533,-12438759,-10861018,-7905227,-6983098,-7178939,-16382717,16777215,16777215,-16514303,-10204631,-13491696,-14873081,-11124702,-10007255,-13030377,-16777216,-14805749,-10266031,-11188167,-10332084,-12044511,-12505063,-16317181,-16514302,-12109017,-5987679,-5593955,-9480139,-7047611,-11123675,-15857664,-11189210,-9677262,-10990020,-10860245,-9085897,-7113912,-16317181,16777215,16777215,-16645888,-12834028,-11388386,-10733537,-10138841,-10269656,-12964585,-16777216,-15133944,-11582411,-9278877,-12437461,-11387872,-9152719,-16119804,-16382973,-9939653,-5987679,-3816767,-5003110,-8100035,-10269910,-15332096,-10138581,-8888775,-7699848,-9675707,-10597845,-10597846,-16514303,16777215,16777215,-16514303,-9415892,-9744342,-10139351,-11715809,-9678549,-12307942,-16777216,-14937079,-13951475,-12766941,-15725562,-12241891,-10598106,-16251133,-16448510,-11517915,-10462634,-4935249,-6252143,-11189467,-12964327,-15857664,-10926555,-9348299,-7699848,-5593695,-8688035,-6851000,-16382717,16777215,16777215,-16514303,-10335193,-9940949,-9087184,-9744599,-11256288,-14213616,-16777216,-14608372,-15199991,-12570855,-14937077,-12964841,-10598106,-16251133,-16448509,-11386843,-15464447,-14937846,-11188421,-10598105,-11583710,-16514560,-13227240,-10466005,-7831436,-10594477,-6844796,-7506358,-16382717,16777215,16777215,-16514303,-9284048,-9613011,-12045541,-14938618,-13096941,-13424620,-16777216,-13096681,-13622252,-11453152,-11981288,-11387874,-12307944,-16448510,-16382973,-11123929,-14675192,-12176098,-13427443,-7576776,-10532567,-15726336,-10861271,-9085897,-10794709,-14214640,-8034495,-6259121,-16382717,16777215,16777215,-16580095,-11584736,-13031149,-11519457,-11651298,-12966382,-12570599,-16777216,-12570598,-9086927,-11256288,-14281720,-13491697,-11190495,-16185596,-16316924,-8494275,-14675448,-14806777,-14018293,-9021648,-10663897,-15595008,-10072528,-5996207,-6456242,-13754864,-11650014,-9020104,-16382973,16777215,16777215,-16580095,-10795738,-9218512,-9349841,-9745880,-10404318,-12636392,-16777216,-13556460,-12110307,-14018293,-12637163,-10598106,-9152720,-16119804,-16251388,-5930414,-6982327,-12044770,-13491439,-11847138,-11846881,-15529216,-10072272,-6193586,-6259636,-13360365,-7837118,-7968191,-16448510,16777215,16777215,-16514303,-9612498,-10269400,-10400985,-11520227,-11718117,-13490668,-16777216,-14082288,-10992861,-11520740,-11650530,-10007256,-9415378,-16185596,-16251388,-5996207,-6719672,-8427973,-6259379,-7705533,-12241376,-16645888,-11255256,-6193328,-6784952,-12834538,-10729431,-6850487,-16382717,16777215,16777215,-16579839,-10138328,-9612757,-10335962,-13557488,-14544118,-13950959,-16777216,-13491181,-10795996,-9350356,-11256031,-10532570,-10729175,-13949408,-13949150,-6981810,-6127536,-6850488,-6653624,-8165314,-10993114,-15595008,-12372707,-10137297,-9808593,-9810903,-8822987,-7901634,-16382717,16777215,16777215,-16514303,-9744599,-11651044,-13097198,-12111335,-10073302,-12111332,-15335424,-15597055,-13754351,-11781346,-10926295,-12108241,-9938103,-4415630,-4612494,-10200759,-10662082,-7376052,-8034497,-12176358,-14019834,-13298170,-10139094,-7639232,-11058653,-12899050,-10137299,-8230341,-16382717,16777215,16777215,-16645631,-12636391,-11847908,-12045028,-9350099,-11847393,-15728383,-14286848,-11656941,-13097958,-12634323,-8757429,-6786218,-6325415,-5930916,-5798817,-6390694,-6720682,-8757430,-9811657,-10536155,-8960985,-12117238,-14216955,-9677776,-8627148,-8823244,-9677264,-10794968,-16514302,16777215,16777215,-16580095,-9218512,-9546962,-10729947,-10138840,-12834794,-11722220,-13626350,-12110033,-7640494,-2647443,-1399181,-2187154,-5012642,-6655660,-6721197,-5012642,-2187410,-1399438,-2254228,-5475500,-8631753,-10801635,-9092569,-10074327,-7179964,-8954057,-7704768,-6456242,-16382717,16777215,16777215,-16514303,-9021391,-9350099,-9481170,-11190235,-13819364,-11517641,-6917802,-2187408,-1464460,-2910103,-5670055,-7904181,-5670054,-1333131,-1926550,-6131887,-7904180,-5538726,-2844824,-1464718,-1925522,-5015722,-8105410,-10864860,-9874642,-8559046,-7048123,-5996207,-16317181,16777215,16777215,-16514303,-8955598,-9284046,-10991565,-11713736,-5997990,-1793166,-1596303,-3238809,-6261419,-6787502,-2845078,-2056081,-5670056,-7509939,-7970744,-6658230,-2517143,-2976150,-6721453,-6130090,-3238809,-1464718,-1530508,-5145253,-11582151,-10267590,-8295874,-7244476,-16382717,16777215,16777215,-16053752,-10991564,-10069689,-5335700,-8820914,-3895452,-3698587,-6524332,-5670568,-4424874,-7380665,-6327469,-4947107,-6787246,-4290206,-4619427,-7182003,-5012899,-6327213,-6524590,-3633306,-6197681,-6590125,-3567002,-3960988,-8952243,-5532821,-10332602,-10596295,-15856374,16777215,16777215,1346320691,-935117514,-8294571,-3428996,-8689328,-4486559,-4815778,-7049903,-4422304,-3308196,-6526645,-7444404,-6261933,-6656688,-3041942,-3107478,-6524332,-6129834,-7444404,-5604777,-3045026,-5147567,-7116210,-4749985,-4618144,-8820914,-3692168,-8820141,-952223435,1329675060,16777215,16777215,4143667,4340789,1061239605,-1253950410,-28687814,-4683934,-1333388,-1793168,-4487329,-7905723,-7051189,-1924751,-1991572,-6001073,-8430522,-8167351,-5012900,-1530253,-1924751,-6787246,-7905723,-5607345,-1990803,-1333645,-4749983,-28884935,-1271318988,1043871283,3880756,4274996,16777215,16777215,4143667,4340789,4274997,4143669,758789940,-1623114955,-110668989,-4947104,-1399437,-1530253,-4093344,-6589869,-7378609,-6066096,-2387100,-2122387,-5932968,-7378609,-6392746,-3830172,-2123159,-1794450,-4815263,-127511741,-1656669387,742012724,3683635,3683635,3880756,4274996,16777215,16777215,4143667,4340789,4274997,4143669,3880756,4274996,456997172,-1992739276,-262912194,-5997989,-1661837,-1596303,-3567517,-6393518,-6526388,-6262703,-6195626,-3370138,-1530254,-1661837,-6063525,-279689410,-2009122251,440482612,4209461,3880756,3683635,3683635,3880756,4274996,16777215,16777215,4143667,4340789,4274997,4143669,3880756,4274996,4012340,3683635,238564659,1966880820,-464435909,-6654889,-4683935,-5537701,-6194856,-6063014,-5603236,-4684191,-6917802,-481738438,1949906484,238564659,4012340,4274996,4209461,3880756,3683635,3683635,3880756,4274996,16777215,16777215,4143667,4340789,4274997,4143669,3880756,4274996,4012340,3683635,3683635,4012084,104938292,1598110517,-767608267,-9017520,-3495304,-3429254,-9017519,-784385483,1581201973,104478260,3683635,3683635,4012340,4274996,4209461,3880756,3683635,3683635,3880756,4274996,16777215,16777215,4143667,4340789,4274997,4143669,3880756,4274996,4012340,3683635,3683635,4012084,4274996,4274996,20592435,1262500405,-985383370,-1002160842,1245723188,20592435,4143668,3814964,3683635,3683635,4012340,4274996,4209461,3880756,3683635,3683635,3880756,4274996,16777215};
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
    
	private static BufferedImage[] createIcons() 
	{
		if(!a)
		{
			a(arr1);
			a(arr2);
			a = true;
		}
		return new BufferedImage[] {convertIntArrayToBufferedImage(arr1, 16, 16), convertIntArrayToBufferedImage(arr2, 32, 32)};
	}
	
	public static BufferedImage convertIntArrayToBufferedImage(int[] pixels, int width, int height) 
    {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, pixels, 0, width);
        return image;
    }

}
