package jredfox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

/**
 * TODO: x86 and x64 on windows 7
 * TODO: macos intel(x64) and silicon (arm64)
 * TODO: linux ubuntu / mint x86, x64, arm64
 * NOTE: ARM32 looks to not be supported by java so they would be running x86 on windows and who knows what on linux / android
 */
public class DpiFix implements IFMLLoadingPlugin, net.minecraftforge.fml.relauncher.IFMLLoadingPlugin {

	public boolean highPriority;
	public DpiFix()
	{
		this.load();
	}

	public void load()
	{
		try 
		{
			ARCH arch = getARCH();
			System.out.println(arch);
			this.loadConfig();
			this.loadNatives(arch, 0);
			this.fixDPI();
			if(this.highPriority)
				this.setHighPriority();
		}
		catch (Throwable t)
		{
			t.printStackTrace();
		}
	}

	//shit ASM config
	public void loadConfig() throws IOException 
	{
		long ms = System.currentTimeMillis();
		File file = new File("config", "DpiFix.cfg");
		if(!file.exists())
		{
			file.getParentFile().mkdirs();
			ArrayList l = new ArrayList();
			l.add("setHighPriority:true");
			saveFileLines(l, file, true);
		}
		
		List<String> lines = file.exists() ? getFileLines(file, true) : Collections.EMPTY_LIST;
		boolean found = false;
		for(String s : lines)
		{
			String l = s.trim();
			if(l.startsWith("setHighPriority:"))
			{
				this.highPriority = l.substring("setHighPriority:".length(), l.length()).toLowerCase().equals("true");
				found = true;
				break;
			}
		}
		if(!found)
		{
			file.delete();
			this.highPriority = true;
		}
		System.out.println("DpiFix.cfg took:" + (System.currentTimeMillis() - ms));
	}

	public void loadNatives(ARCH arch, int pass) throws IOException 
	{
		String strNativeName = "mc-dpifix-" + arch.toString().toLowerCase() + ".dll";
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
				this.loadNatives(arch, 1);
			} 
			else
			{
				t.printStackTrace();
			}
		}
	}

	/**
	 * fix High DPI Issues
	 */
	public native void fixDPI();
	/**
	 * automatically sets minecraft's process to high priority
	 */
	public native void setHighPriority();

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
		arc = arc.toLowerCase();
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

	// JUNK CODE We are simply using forge as a hijack hack to load the DPI
	// fix before the game app is visible
	@Override
	public String[] getASMTransformerClass() {
		return null;
	}

	@Override
	public String getModContainerClass() {
		return null;
	}

	@Override
	public String getSetupClass() {
		return null;
	}

	@Override
	public void injectData(Map<String, Object> data) {
	}

	@Override
	public String getAccessTransformerClass() {
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
	 * Equivalent to Files.readAllLines() but, works way faster
	 */
	public static List<String> getFileLines(File f,boolean utf8)
	{
		BufferedReader reader = null;
		List<String> list = null;
		try
		{
			if(!utf8)
			{
				reader = new BufferedReader(new FileReader(f));//says it's utf-8 but, the jvm actually specifies it even though the lang settings in a game might be different
			}
			else
			{
				reader = new BufferedReader(new InputStreamReader(new FileInputStream(f),StandardCharsets.UTF_8) );
			}
			
			list = new ArrayList();
			String s = reader.readLine();
			
			if(s != null)
			{
				list.add(s);
			}
			
			while(s != null)
			{
				s = reader.readLine();
				if(s != null)
				{
					list.add(s);
				}
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			if(reader != null)
			{
				try 
				{
					reader.close();
				} catch (IOException e) 
				{
					System.out.println("Unable to Close InputStream this is bad");
				}
			}
		}
		
		return list;
	}
	
	/**
	 * Overwrites entire file default behavior no per line modification removal/addition
	 */
	public static void saveFileLines(List<String> list,File f,boolean utf8)
	{
		BufferedWriter writer = null;
		try
		{
			if(!utf8)
			{
				writer = new BufferedWriter(new FileWriter(f));
			}
			else
			{
				writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f),StandardCharsets.UTF_8 ) );
			}
			
			for(String s : list)
			{
				writer.write(s + "\r\n");
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			if(writer != null)
			{
				try
				{
					writer.close();
				}
				catch(Exception e)
				{
					System.out.println("Unable to Close OutputStream this is bad");
				}
			}
		}
	}

}
