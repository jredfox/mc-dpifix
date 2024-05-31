package jredfox;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

/**
 * TODO: get x86 and arm64 natives on windows
 * TODO: macos intel(x64) and silicon (arm64)
 * TODO: linux ubuntu / mint x86, x64, arm64
 * NOTE: ARM32 looks to not be supported by java so they would be running x86 on windows and who knows what on linux / android
 */
public class DpiFix implements IFMLLoadingPlugin, net.minecraftforge.fml.relauncher.IFMLLoadingPlugin {

	public DpiFix()
	{
		this.load();
	}

	public void load()
	{
		try 
		{
			ARCH arch = getARCH();
			this.loadNatives(arch, 0);
			this.fixDPI();
		} 
		catch (Throwable t)
		{
			t.printStackTrace();
		}
	}

	public void loadNatives(ARCH arch, int pass) throws IOException 
	{
		String strNativeName = "mc-dpifix-" + arch + ".dll";
		File fnative = new File("natives/jredfox", strNativeName).getAbsoluteFile();
		InputStream in = null;
		FileOutputStream out = null;
		if (!fnative.exists()) 
		{
			fnative.getParentFile().mkdirs();
			in = DpiFix.class.getClassLoader().getResourceAsStream("natives/jredfox/" + strNativeName);
			out = new FileOutputStream(fnative);
			copy(in, out);
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
		finally
		{
			closeQuietly(in);
			closeQuietly(out);
		}
	}

	public native void fixDPI();

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

	public static enum ARCH {
		X64, X86, ARM32, ARM64, UNSUPPORTED
	}

	// JUNK CODE We are simply using forge as a high jack hack to load the DPI
	// fix before the game app is visible
	@Override
	public String[] getASMTransformerClass() {
		// TODO Auto-generated method stub
		return null;
	}

	private static boolean rtrue() {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public String getModContainerClass() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getSetupClass() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void injectData(Map<String, Object> data) {
		// TODO Auto-generated method stub

	}

	@Override
	public String getAccessTransformerClass() {
		// TODO Auto-generated method stub
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

}
