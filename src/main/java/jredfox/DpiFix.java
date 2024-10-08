package jredfox;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import net.minecraftforge.common.ForgeVersion;

/**
 * TODO: macos intel(x64) and silicon (arm64)
 * TODO: linux ubuntu / mint x86, x64, arm64
 */
public class DpiFix implements IFMLLoadingPlugin, net.minecraftforge.fml.relauncher.IFMLLoadingPlugin {

	public static boolean dpifix = true;
	public static boolean highPriority = true;
	public static int nicenessMac;
	public static int nicenessLinux;
	public static boolean hasNatives = true;
	
	public DpiFix()
	{
		//only load the mod if it hasn't been loaded by the javaagent already
		if(!Boolean.parseBoolean(System.getProperty("gamemodelib.agent", "false")))
			this.loadMod();
		else
			this.loadConfig(); //If we are still in the mods folder load the coremod fixes without the Process fixes as they have already been applied manually
	}

	public void loadMod()
	{
		try 
		{
			ARCH arch = getARCH();
			System.out.println("Dpi-Fix Loading Natives:" + arch);
			this.loadConfig();
			this.loadNatives(arch, 0);
			if(this.dpifix)
				this.fixProcessDPI();
			if(this.highPriority)
				this.setHighProcessPriority();
		}
		catch (Throwable t)
		{
			t.printStackTrace();
		}
	}
	
	public static void load()
	{
		try
		{
			ARCH arch = getARCH();
			System.out.println("GamemodeLib Loading Natives:" + arch);
			loadNatives(arch, 0);
		}
		catch (Throwable t)
		{
			t.printStackTrace();
		}
	}

	//ASM config
	public static final boolean onefive = ForgeVersion.getMajorVersion() < 8;
	public static final boolean isClient = onefive ? DpiFix.class.getClassLoader().getSystemClassLoader().getResource("net/minecraft/client/Minecraft.class") != null : DpiFix.class.getClassLoader().getSystemClassLoader().getResource("net/minecraft/client/main/Main.class") != null;
	public static boolean coremod;
	public static boolean fsSaveFix;
	public static boolean fsTabFix;
	public static boolean fsResizeableFix;
	public static boolean fsSplashFix;
	public static boolean fsMouseFixLinux;
	public static boolean fsMouseFixOther;
	public static boolean maximizeFix;
	public static boolean deawt_windows;
	public static boolean deawt_mac;
	public static boolean deawt_linux;
	public static boolean guiMouseFix;
	public void loadConfig()
	{
		PropertyConfig cfg = new PropertyConfig(new File("config/DpiFix", "DpiFix.cfg"));
		cfg.load();
		
		this.dpifix = cfg.get("Process.DpiFix");
		this.highPriority = cfg.get("Process.HighPriority");
		nicenessMac = toNiceness(cfg.getInt("Process.HighPriority.Niceness.Mac", -5));
		nicenessLinux = toNiceness(cfg.getInt("Process.HighPriority.Niceness.Linux", -5));
		
		coremod = cfg.get("CoreMod.Enabled");
		fsSaveFix = cfg.get("Coremod.FullScreen.SaveFix");
		fsTabFix = cfg.get("Coremod.FullScreen.TabFix");
		fsResizeableFix = cfg.get("Coremod.FullScreen.ResizeableFix");
		fsSplashFix = cfg.get("Coremod.FullScreen.SplashFix");
		fsMouseFixLinux = cfg.get("Coremod.FullScreen.MouseFix.Linux");
		fsMouseFixOther = cfg.get("Coremod.FullScreen.MouseFix.OtherOS", false);
		maximizeFix = cfg.get("Coremod.MaximizeResFix");
		guiMouseFix = cfg.get("Coremod.GUI.MouseFix");
		deawt_windows = cfg.get("Coremod.DeAWT.Windows", false);
		deawt_mac = cfg.get("Coremod.DeAWT.Mac");
		deawt_linux = cfg.get("Coremod.DeAWT.Linux", false);
		cfg.save();
	}

	public static void loadNatives(ARCH arch, int pass) throws IOException 
	{
		if(pass == 0)
			DpiFix.loadReNicer();
		String strNativeName = "mc-dpifix-" + arch.toString().toLowerCase() + (isWindows7() ? "-7" : "") + (isWindows ? ".dll" : isMacOs ? ".jnilib" : ".so");
		File fnative = new File("natives/jredfox", strNativeName).getAbsoluteFile();
		//load the natives if they do not exist
		InputStream in = null;
		FileOutputStream out = null;
		if (!fnative.exists()) 
		{
			try
			{
				fnative.getParentFile().mkdirs();
				in = DpiFix.class.getClassLoader().getResourceAsStream("natives/jredfox/" + strNativeName);
				if(in == null)
				{
					System.err.println("Error Missing Natives:" + strNativeName + " ISA:" + arch);
					hasNatives = false;
					return;
				}
				out = new FileOutputStream(fnative);
				copy(in, out);
			}
			catch(Throwable t)
			{
				t.printStackTrace();
			}
			finally
			{
				closeQuietly(in);
				closeQuietly(out);
			}
		}
		
		try
		{
			System.load(fnative.getPath());
		}
		catch (Throwable t) 
		{
			if (pass == 0) 
			{
				fnative.delete();
				loadNatives(arch, 1);
			} 
			else
			{
				t.printStackTrace();
			}
		}
	}
	
	public static File renicer = new File("/usr/local/bin/renicer_bin/renicer");
	public static File changeNiceness = new File("/usr/local/bin/change_niceness");
	public static boolean hasRenicer;
	public static boolean hasChangeNiceness;
	public static void loadReNicer()
	{
		if(isLinux)
		{
			hasRenicer = renicer.exists();
			if(!hasRenicer)
			{
				System.err.println("renicer command not found! To get High Process Priority Please install it from https://github.com/jredfox/renicer/releases");
			}
		}
		else if(isMacOs)
		{
			hasChangeNiceness = changeNiceness.exists();
			if(!hasChangeNiceness)
			{
				System.err.println("change_niceness command not found! To get High Process Priority Please install it from https://github.com/jredfox/change_niceness/releases");
			}
		}
	}

	/**
	 * fix High DPI Issues
	 */
	public native static void fixDPI();
	/**
	 * automatically sets minecraft's process to high priority
	 */
	public native static void setHighPriority();
	
	public static void fixProcessDPI()
	{
		if(DpiFix.hasNatives)
			DpiFix.fixDPI();
	}
	
	public static void setHighProcessPriority()
	{
		if(DpiFix.isWindows)
		{
			DpiFix.setHighPriority();
		}
		else if(DpiFix.hasChangeNiceness)
		{
			try
			{
				System.out.print("Setting High Priority" + System.lineSeparator());
				String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
				Runtime.getRuntime().exec(changeNiceness.getPath() + " " + nicenessMac + " " + pid);
			}
			catch(Throwable t)
			{
				t.printStackTrace();
			}
		}
		else if(DpiFix.hasRenicer)
		{
			try
			{
				System.out.print("Setting High Priority" + System.lineSeparator());
				String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];//keep pid as string as we don't have unsigned long in java
				Runtime.getRuntime().exec(renicer.getPath() + " " + nicenessLinux + " -p " + pid);//-5 is "high" for windows. Anything lower and it will be out of sync with the keyboard and mouse and graphics drivers causing input lag
			}
			catch (Throwable e)
			{
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Allows values from 0 to -20. Do not allow users to troll and make minecraft slower then normal
	 */
	public int toNiceness(int val)
	{
		return Math.min(0, Math.max(-20, val));
	}
	
	public static String osName = System.getProperty("os.name").toLowerCase();
	public static boolean isWindows = osName.startsWith("windows");
	public static boolean isLinux = osName.contains("linux") || osName.contains("nux") || osName.contains("aix");
	public static boolean isMacOs = !isLinux && (osName.contains("mac") || osName.contains("osx") || osName.contains("darwin"));
//	public static boolean isChromeOs = osName.contains("google") || osName.contains("chromeos") || osName.contains("pixelbook");;
	
	public static boolean isWindows7()
	{
		try
		{
			return isWindows && Double.parseDouble(System.getProperty("os.version")) <= 6.1D;
		}
		catch(Exception e) {}
		
		return false;
	}

	/**
	 * gets the real arch based on the string. STOP MAKING ABIGUOUS NAMES FOR
	 * THE SAME Instruction Set Architectures!!!!!!!!!!
	 */
	private static ARCH getARCH()
	{
		String arc = System.getProperty("os.arch");
		// error handling
		if (arc == null || arc.trim().isEmpty())
		{
			System.err.println("ERROR os.arch IS UNSET ASSUMING x64!");
			return ARCH.X64;
		}
		arc = arc.trim().toLowerCase();
		if (arc.equals("x64") || arc.equals("amd64") || arc.equals("x86_64"))
		{
			return ARCH.X64;
		} 
		else if (arc.equals("x86") || arc.length() > 3 && arc.startsWith("i") && arc.substring(2, arc.length()).equals("86")) 
		{
			return ARCH.X86;
		} 
		else if (arc.startsWith("arm64") || arc.startsWith("aarch64") || arc.startsWith("armv8") || arc.startsWith("armv9")) 
		{
			return ARCH.ARM64;
		}
		// do not make this check sooner then arm64 or armv will return true and armv8-armv9 will never return AMR64
		else if (arc.equals("arm") || arc.equals("arm32") || arc.startsWith("aarch32") || arc.startsWith("armv")) 
		{
			System.err.println("ARM32 DETECTED REPORT THIS AS A BUG TO THE MOD AUTHOR! JAVA ISN'T EXPECTED TO RUN ON ARM32 HOW?");
			return ARCH.ARM32;
		}
		return ARCH.UNSUPPORTED;
	}

	public static enum ARCH 
	{
		X64, 
		X86, 
		ARM32, 
		ARM64, 
		UNSUPPORTED
	}

	@Override
	public String[] getASMTransformerClass() {
		String cof = onefive ? "OF" : "";
		return (coremod && isClient) ? new String[]{"jredfox.DpiFixCoreMod" + cof} : new String[] {"jredfox.DpiFixAnn" + cof};
	}

	//___________________________________________START Dummy Methods for IMPL of Coremod______________________________\\
	@Override
	public String getModContainerClass() {
		return null;
	}

	@Override
	public String getSetupClass() {
		return null;
	}

	@Override
	public String getAccessTransformerClass() {
		return null;
	}
	
    public static boolean isObf = true;
	@Override
    public void injectData(Map<String, Object> data)
    {
    	isObf = (Boolean) data.get("runtimeDeobfuscationEnabled");
    }
	
	@Override
	public String[] getLibraryRequestClass() {
		return null;
	}
	
	//_______________________________________________START IOUTILS METHODS REQUIRED______________________________\\
	public static final int BUFFER_SIZE = 1048576/2;
	/**
	 * enforce thread safety with per thread local variables
	 */
	public static final ThreadLocal<byte[]> bufferes = new ThreadLocal<byte[]>()
	{
        @Override
        protected byte[] initialValue() 
        {
			return new byte[BUFFER_SIZE];
        }
	};
	
	public static void copy(InputStream in, OutputStream out) throws IOException
	{
		byte[] buffer = bufferes.get();
		int length;
   	 	while ((length = in.read(buffer)) >= 0)
		{
			out.write(buffer, 0, length);
		}
	}
	
	public static void closeQuietly(Closeable clos)
	{
		try 
		{
			if(clos != null)
				clos.close();
		}
		catch (IOException e)
		{
			
		}
	}
	
	/**
	 * Overwrites entire file default behavior no per line modification removal/addition
	 */
	public static void saveFileLines(List<String> list,File f)
	{
		BufferedWriter writer = null;
		try
		{
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f),StandardCharsets.UTF_8 ) );
			for(String s : list)
				writer.write(s + System.lineSeparator());
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			closeQuietly(writer);
		}
	}

    public static List<String> asStringList(String... arr) {
        List li = new ArrayList(arr.length);
        for(String s : arr)
        	li.add(s);
        return li;
    }

}
