package jredfox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.management.ManagementFactory;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import jml.gamemodelib.GameModeLib;
import jml.gamemodelib.GameModeLibAgent;
import jredfox.clfix.LaunchClassLoaderFix;

/**
 * TODO: macos intel(x64) and silicon (arm64)
 * TODO: linux ubuntu / mint x86, x64, arm64
 */
public class DpiFix implements IFMLLoadingPlugin, net.minecraftforge.fml.relauncher.IFMLLoadingPlugin {

	public static boolean agentmode = Boolean.parseBoolean(System.getProperty("gamemodelib.agent", "false"));
	public static boolean syncCfg = agentmode ? Boolean.parseBoolean(System.getProperty("gamemodelib.cfg", "false")) : true;
	public static boolean dpifix = agentmode ? Boolean.parseBoolean(System.getProperty("gamemodelib.dpi", "false")) : true;
	public static boolean highPriority = agentmode ? Boolean.parseBoolean(System.getProperty("gamemodelib.high", "false")) : true;
	public static int nicenessMac = agentmode ? toNiceness(Integer.parseInt(System.getProperty("gamemodelib.niceness.mac", "-5"))) : -5;
	public static int nicenessLinux = agentmode ? toNiceness(Integer.parseInt(System.getProperty("gamemodelib.niceness.linux", "-5"))) : -5;
	public static boolean hasNatives = Boolean.parseBoolean(System.getProperty("dpifix.hasNatives", "false"));
	public static boolean coremodLoaded = Boolean.parseBoolean(System.getProperty("dpifix.coremod.loaded", "false"));
	
	public DpiFix()
	{
		//only load the mod if it hasn't been loaded by the javaagent already
		if(!agentmode)
			this.loadMod();
		else
			this.loadConfig(); //If we are still in the mods folder load the coremod fixes without the Process fixes as they have already been applied manually
		
		coremodLoaded = true;
		System.setProperty("dpifix.coremod.loaded", "true");
	}

	public void loadMod()
	{
		try 
		{
			ARCH arch = getARCH();
			System.out.println("Dpi-Fix Loading Natives:" + arch);
			this.loadConfig();
			this.loadNatives(arch, 0);
			if(dpifix)
				this.fixProcessDPI();
			if(highPriority)
				this.setHighProcessPriority();
		}
		catch (Throwable t)
		{
			t.printStackTrace();
		}
		
		if(onefive && !agentmode && highPriority)
		{
			IllegalArgumentException e = new IllegalArgumentException("DPI-Fix High Process Priority for 1.5x Requies java agent mode!\nAdd these JVM Flags: -javaagent:coremods/" + GameModeLib.getFileFromClass(GameModeLibAgent.class).getName());
			e.printStackTrace();
			throw e;
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
	public static boolean hasForge = LaunchClassLoaderFix.forName("net.minecraftforge.common.ForgeVersion") != null;
	public static boolean onefive = hasForge && net.minecraftforge.common.ForgeVersion.getMajorVersion() < 8;
	public static boolean isClient = onefive ? DpiFix.class.getClassLoader().getSystemClassLoader().getResource("net/minecraft/client/Minecraft.class") != null : DpiFix.class.getClassLoader().getSystemClassLoader().getResource("net/minecraft/client/main/Main.class") != null;
	public static boolean coremod;
	public static boolean fsSaveFix;
	public static boolean fsTabFix;
	public static boolean fsResizeableFix;
	public static boolean fsSplashFix;
	public static boolean fsMouseFixLinux;
	public static boolean fsMouseFixOther;
	public static boolean maximizeFix;
	public static boolean mainMenu;
	public static boolean deawt_windows;
	public static boolean deawt_mac;
	public static boolean deawt_linux;
	public static boolean guiMouseFix;
	public static boolean fixResourceThread;
	public static boolean modLogoFix;
	public static void loadConfig()
	{
		PropertyConfig cfg = new PropertyConfig(new File("config", "DpiFix.cfg"));
		cfg.load();
		
		//Don't Overwrite Process Booleans from Java Agent
		if(syncCfg)
		{
			dpifix = cfg.get("Process.DpiFix");
			highPriority = cfg.get("Process.HighPriority");
			nicenessMac = toNiceness(cfg.getInt("Process.HighPriority.Niceness.Mac", -5));
			nicenessLinux = toNiceness(cfg.getInt("Process.HighPriority.Niceness.Linux", -5));
		}
		
		coremod = cfg.get("CoreMod.Enabled");
		fsSaveFix = cfg.get("Coremod.FullScreen.SaveFix");
		fsTabFix = cfg.get("Coremod.FullScreen.TabFix");
		fsResizeableFix = cfg.get("Coremod.FullScreen.ResizeableFix");
		fsSplashFix = cfg.get("Coremod.FullScreen.SplashFix");
		fsMouseFixLinux = cfg.get("Coremod.FullScreen.MouseFix.Linux");
		fsMouseFixOther = cfg.get("Coremod.FullScreen.MouseFix.OtherOS", false);
		maximizeFix = cfg.get("Coremod.OneSix.MaximizeResFix");
		mainMenu = cfg.get("Coremod.OneSix.MainMenuFix");
		guiMouseFix = cfg.get("Coremod.GUI.MouseFix");
		modLogoFix = cfg.get("Coremod.GUI.ModLogoFix");
		deawt_windows = cfg.get("Coremod.OneFive.DeAWT.Windows");
		deawt_mac = cfg.get("Coremod.OneFive.DeAWT.Mac");
		deawt_linux = cfg.get("Coremod.OneFive.DeAWT.Linux");
		fixResourceThread = cfg.get("Coremod.OneFive.ThreadResourcesFix");
		cfg.get("Coremod.OneFive.DeAWT.Compat.Technic");//Make this generate even when the agent isn't active
		cfg.save();
	}

	public static void loadNatives(ARCH arch, int pass) throws IOException 
	{
		if(pass == 0)
			DpiFix.loadChangeNiceness();
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
					System.setProperty("dpifix.hasNatives", "false");
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
			System.setProperty("dpifix.hasNatives", "true");
			hasNatives = true;
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
	public static boolean hasRenicer = renicer.exists();
	public static boolean hasChangeNiceness = changeNiceness.exists();
	public static void loadChangeNiceness()
	{
		if(isLinux)
		{
			if(!hasRenicer)
			{
				System.err.println("renicer command not found! To get High Process Priority Please install it from https://github.com/jredfox/renicer/releases");
			}
		}
		else if(isMacOs)
		{
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
				String pid = getPIDUnix();
				System.out.print("Setting High Priority " + pid + " niceness:" + nicenessMac + "\n");
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
				String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];//keep pid as string as we don't have unsigned long in java
				System.out.print("Setting High Priority " + pid + " niceness:" + nicenessLinux + "\n");
				Runtime.getRuntime().exec(renicer.getPath() + " " + nicenessLinux + " -p " + pid);//-5 is "high" for windows. Anything lower and it will be out of sync with the keyboard and mouse and graphics drivers causing input lag
			}
			catch (Throwable e)
			{
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * avoids a java bug which causes java to hang for 5,000MS on macOS monetary when trying to get the PID
	 * While this method is expensive 5-29ms it's compatible with java 6 or higher
	 */
	public static String getPIDUnix() throws IOException 
	{
		BufferedReader reader = null;
		try
		{
	        // Use ProcessBuilder to get the Parent Process ID this will always be java's Process Id
	        ProcessBuilder processBuilder = new ProcessBuilder("bash", "-c", "echo $(ps -o ppid= -p $$)");
	        Process process = processBuilder.start();
	
	        // Capture the output from the command
	        reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
	        String pid = reader.readLine().replace("\"", "").replace("'", "").trim();
	        closeQuietly(reader);
	        return pid;
		}
		catch(Exception e)
		{
			e.printStackTrace();
			closeQuietly(reader);
		}
		return null;
	}

	/**
	 * Allows values from 0 to -20. Do not allow users to troll and make minecraft slower then normal
	 */
	public static int toNiceness(int val)
	{
		return Math.min(0, Math.max(-20, val));
	}
	
	public static String osName = System.getProperty("os.name").toLowerCase();
	public static boolean isWindows = osName.startsWith("windows");
	public static boolean isLinux = osName.contains("linux") || osName.contains("nux") || osName.contains("aix");
	public static boolean isMacOs = !isLinux && (osName.contains("mac") || osName.contains("osx") || osName.contains("darwin"));
//	public static boolean isChromeOs = osName.contains("google") || osName.contains("chromeos") || osName.contains("pixelbook");
	
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
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), Charset.forName("UTF-8") ) );
			for(String s : list)
				writer.write(s + lineSeparator());
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
	
	public static String lineSeparator()
	{
		return System.getProperty("java.version").replace("'", "").replace("\"", "").trim().startsWith("1.6.") ? System.getProperty("line.separator") : System.lineSeparator();
	}

    public static List<String> asStringList(String... arr) {
        List li = new ArrayList(arr.length);
        for(String s : arr)
        	li.add(s);
        return li;
    }

}
