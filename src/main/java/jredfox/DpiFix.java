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

/**
 * TODO: macos intel(x64) and silicon (arm64)
 * TODO: linux ubuntu / mint x86, x64, arm64
 */
//1.6.4-1.7.10 forge's annotations
@IFMLLoadingPlugin.Name("mc-dpifix")
@IFMLLoadingPlugin.SortingIndex(1005)
@IFMLLoadingPlugin.TransformerExclusions("jredfox.DpiFix")
//newer minecraft forge's annotations
@net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.Name("mc-dpifix")
@net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.SortingIndex(1001)
@net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.TransformerExclusions("jredfox.DpiFix")
public class DpiFix implements IFMLLoadingPlugin, net.minecraftforge.fml.relauncher.IFMLLoadingPlugin {

	public boolean dpifix = true;
	public boolean highPriority = true;
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
	public static final boolean isClient = DpiFix.class.getClassLoader().getSystemClassLoader().getResource("net/minecraft/client/main/Main.class") != null;
	public static boolean coremod;
	public static boolean fsSaveFix;
	public static boolean fsTabFix;
	public static boolean fsResizeableFix;
	public static boolean fsSplashFix;
	public static boolean fsMouseFixLinux;
	public static boolean fsMouseFixOther;
	public static boolean maximizeFix;
	
	public void loadConfig()
	{
		PropertyConfig cfg = new PropertyConfig(new File("config/DpiFix", "DpiFix.cfg"));
		cfg.load();
		
		this.dpifix = cfg.get("Process.DpiFix");
		this.highPriority = cfg.get("Process.HighPriority");
		
		this.coremod = cfg.get("CoreMod.Enabled");
		this.fsSaveFix = cfg.get("Coremod.FullScreen.SaveFix");
		this.fsTabFix = cfg.get("Coremod.FullScreen.TabFix");
		this.fsResizeableFix = cfg.get("Coremod.FullScreen.ResizeableFix");
		this.fsSplashFix = cfg.get("Coremod.FullScreen.SplashFix");
		this.fsMouseFixLinux = cfg.get("Coremod.FullScreen.MouseFix.Linux");
		this.fsMouseFixOther = cfg.get("Coremod.FullScreen.MouseFix.OtherOS", false);
		this.maximizeFix = cfg.get("Coremod.MaximizeResFix");
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
	
	public static File renicer = new File("/usr/local/bin/renicer/renicer");
	public static boolean hasRenicer;
	public static void loadReNicer()
	{
		if(!isWindows)
		{
			try
			{
				hasRenicer = renicer.exists();
				File install_sh = new File(  "config/DpiFix/renicer-install.sh").getAbsoluteFile();
				File uninstall_sh = new File("config/DpiFix/renicer-uninstall.sh").getAbsoluteFile();
				
				if(!hasRenicer)
				{
					System.err.println("renicer command not found! Please install it by running: sh '" + install_sh + "'");
				}
				
				//create renicer-install.sh
				if(!install_sh.exists() || !uninstall_sh.exists())
				{
					//enforce that the config directory exists
					install_sh.getParentFile().mkdirs();
					
					List<String> li = new ArrayList();
					String nl = System.lineSeparator();
					li.add("#!/bin/sh" + nl +
							"echo \"Installing renicer\"" + nl +
							"sudo mkdir -p /usr/local/bin/renicer" + nl +
							"sudo cp /usr/bin/renice /usr/local/bin/renicer/renicer #Copy renice" + nl +
							"if [ \"$(uname | tr '[:upper:]' '[:lower:]')\" = \"darwin\" ]; then" + nl +
							"	sudo chown root:wheel /usr/local/bin/renicer # Make Root owner for macOS" + nl +
							"else" + nl +
							"	sudo chown root:root /usr/local/bin/renicer # Make Root owner for linux" + nl +
							"fi" + nl +
							"sudo chmod 755 /usr/local/bin/renicer # Ensure Executable for all users but not Editable" + nl +
							"sudo chmod u+s /usr/local/bin/renicer/renicer # Run as Root"
							);
					DpiFix.saveFileLines(li, install_sh);
					
					//Create renicer-uninstall.sh
					List<String> li2 = new ArrayList();
					li2.add("echo \"Uninstalling renicer\"" + nl +
							"sudo rm -rf /usr/local/bin/renicer"
							);
					DpiFix.saveFileLines(li2, uninstall_sh);
				}
			}
			catch(Throwable t)
			{
				t.printStackTrace();
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
		else if(DpiFix.hasRenicer)
		{
			try
			{
				System.out.print("Setting High Priority" + System.lineSeparator());
				String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];//keep pid as string as we don't have unsigned long in java
				Runtime.getRuntime().exec(renicer.getPath() + " -5 -p " + pid);//-5 is "high" for windows. Anything lower and it will be out of sync with the keyboard and mouse and graphics drivers causing input lag
			}
			catch (Throwable e)
			{
				e.printStackTrace();
			}
		}
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
		return (this.coremod && isClient) ? new String[]{"jredfox.DpiFixCoreMod"} : null;
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

}
