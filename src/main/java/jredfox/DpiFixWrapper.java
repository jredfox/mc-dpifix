package jredfox;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Map;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import jml.gamemodelib.GameModeLibAgent;
import jredfox.clfix.LaunchClassLoaderFix;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.fml.relauncher.CoreModManager;

/**
 * DpiFixWrapper is a wrapper for DpiFix that will correctly add it to CoreModManager without annotation or security conflicts
 * caused by Java 7 making forge find the wrong IFMLLoadingPlugin which then later throws a security exception when it tries to load IFMLLoadingPlugin$Name
 * @author jredfox
 */
//TODO addTransformerExclusion 1.6+ or and something for 1.5x
public class DpiFixWrapper implements IFMLLoadingPlugin, net.minecraftforge.fml.relauncher.IFMLLoadingPlugin {
	
	public DpiFixWrapper() throws Exception
	{
		this.wrap();
	}

	/**
	 * Adds a new DpiFix instance to CoreModManager without needing annotations. 
	 * Annotations cause crashes on java 6-7 when it finds the wrong IFMLLoadingPlugin.class and IFMLLoadingPlugin$Name tries to load
	 */
	public void wrap() throws Exception
	{
		//Annotation Equivalent Values
		String name = "mc-dpifix";
		int sortIndex = 1005;
		String exclusions = "jredfox.DpiFix";
		
		//Works for 1.5 - 1.12.2
		boolean onefive = ForgeVersion.getMajorVersion() < 8;
		boolean onesixnotch = ForgeVersion.getMajorVersion() < 9 || ForgeVersion.getMajorVersion() == 9 && ForgeVersion.getMinorVersion() <= 11 && ForgeVersion.getBuildVersion() < 937;
		boolean oneeight = ForgeVersion.getMajorVersion() >= 11;
		Class IFMLLoadingPluginClazz = oneeight ? net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.class : cpw.mods.fml.relauncher.IFMLLoadingPlugin.class;
		Class coreModManagerClazz = oneeight ? LaunchClassLoaderFix.forName("net.minecraftforge.fml.relauncher.CoreModManager") : ( (!onefive) ? LaunchClassLoaderFix.forName("cpw.mods.fml.relauncher.CoreModManager") : LaunchClassLoaderFix.forName("cpw.mods.fml.relauncher.RelaunchLibraryManager"));
		Class FMLPluginWrapper = LaunchClassLoaderFix.forName(coreModManagerClazz.getName() + "$FMLPluginWrapper");
		List plugins = (List) GameModeLibAgent.getField(coreModManagerClazz, "loadPlugins").get(null);//TODO: change to an array of possible fields
		File location = GameModeLibAgent.getFileFromClass(GameModeLibAgent.class);
		if(!onesixnotch)
		{
			Constructor<?> ctr = FMLPluginWrapper.getDeclaredConstructor(
        		String.class, 
        		IFMLLoadingPluginClazz, 
        		File.class, 
        		int.class, 
                String[].class
            );
			ctr.setAccessible(true);
			plugins.add(ctr.newInstance(name, new DpiFix(), location, sortIndex, new String[0] ));
		}
		//1.6.2 - 1.6.4(notch)
		else if(FMLPluginWrapper != null)
		{
			Constructor<?> ctr = FMLPluginWrapper.getDeclaredConstructor(
	        		String.class, 
	        		IFMLLoadingPluginClazz,
	        		File.class,
	                String[].class
	        );
			ctr.setAccessible(true);
			plugins.add(ctr.newInstance(name, new DpiFix(), location, new String[0] ));
		}
		//1.5 - 1.6.1
		else
		{
			plugins.add(new DpiFix());
		}
	}

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
    	DpiFix.isObf = (Boolean) data.get("runtimeDeobfuscationEnabled");
    }

	@Override
	public String getAccessTransformerClass() {
		return null;
	}

	@Override
	public String[] getLibraryRequestClass() {
		return null;
	}

}
