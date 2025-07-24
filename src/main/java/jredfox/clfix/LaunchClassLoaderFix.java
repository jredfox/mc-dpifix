package jredfox.clfix;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fix LaunchClassLoader Memory Leaks Supports launchwrapper 1.3 - 1.12
 * V2.0.0 Is alot more robust then V1.0.0 In EvilNotchLib as it handles all possible ClassLoaders & Shadow Variables
 * @author jredfox
 */
public class LaunchClassLoaderFix {
	
	/**
	 * ChangeLog 2.0.1:
	 * - Fixed {@link System#identityHashCode(Object)} collisions resulted in not setting class loader. object hashcode no longer represents the address and is no longer guaranteed since java 8 to be even unique per object instance
	 * - Fixed Technic's resources and pngMap memory leak in LaunchWrapperTransformer only. For some reason RelaunchClassLoader#parent returns the class loader that's not technic's so we can't use reflection to fix it
	 * - Fixed Verify not working for non instances of LaunchClassLoader
	 * - Added Support for more Library ClassLoaders to stop the while loop from
	 * - NOTE: RelaunchClassLoader & technic's MinecraftClassLoader cannot be fixed for 1.5x or below because findClass was public and the API basically said to use get cached classes quickly or load if needed which mods did in fact do. 
	 * However the RAM Leak should be less then 40-80MB in a large modpack(1000+ mods) for both leaks. Unlike 1.6x+ where the ram leak was 150MB for 100 mods
	 */
	public static final String VERSION = "2.0.1";
	
	private static String[] libLoaders = new String[]
	{
		"java.",
		"sun.",
		"com.sun.",
		"jdk.",
		"javax."
	};
	
	public static boolean isLibClassLoader(String[] libs, String name) 
	{
		for(String lib : libs)
			if(name.startsWith(lib))
				return true;
		return false;
	}
	
	/**
	 * can be called at any time
	 */
	public static void stopMemoryOverflow(ClassLoader clforge)
	{
		try
		{
			Class launch = forName("net.minecraft.launchwrapper.Launch");
			if(launch == null)
			{
				System.err.println("LaunchWrapper is Missing!");
				return;
			}
			
			String clazzLoaderName = "net.minecraft.launchwrapper.LaunchClassLoader";
			Class clazzLoaderClazz = forName(clazzLoaderName);
			Set<ClassLoader> loaders = getClassLoaders(launch, clforge);
			for(ClassLoader cl : loaders)
			{
				if(cl == null)
					continue;
				System.out.println("Fixing RAM Leak of:" + cl);
				//Support Shadow Variables for Dumb Mods Replacing Launch#classLoader
				Class actualClassLoader = cl.getClass();
				boolean flag = instanceOf(clazzLoaderClazz, actualClassLoader);
				do
				{
					setDummyMap(cl, actualClassLoader, "cachedClasses");
					setDummyMap(cl, actualClassLoader, "resourceCache");
					setDummyMap(cl, actualClassLoader, "packageManifests");
					setDummySet(cl, actualClassLoader, "negativeResourceCache");
					if(flag && actualClassLoader.getName().equals(clazzLoaderName))
						break;//Regardless of what LaunchClassLoader extends break after as we are done
					actualClassLoader = actualClassLoader.getSuperclass();
				}
				while(flag ? true : !isLibClassLoader(libLoaders, actualClassLoader.getName()) );
			}
		}
		catch(Throwable t)
		{
			System.err.println("FATAL ERROR HAS OCCURED PATCHING THE LaunchClassLoader Memory Leaks!");
			t.printStackTrace();
		}
	}

	/**
	 * Disables FoamFix's Flawed Fix trying to Fix LaunchClassLoader RAM Leak
	 */
	public static void stopMemoryOverflowFoamFix(ClassLoader clforge)
	{
		//Disable FoamFix lwWeakenResourceCache & lwRemovePackageManifestMap for 1.8x - 1.12.2
		Class foamShared = forName("pl.asie.foamfix.shared.FoamFixShared");
		Class foamBF = forName("pl.asie.foamfix.bugfixmod.coremod.BugfixModClassTransformer");
		if(foamShared != null)
		{
			try
			{
				System.out.println("Disabling FoamFix's \"Fix\" for LaunchClassLoader!");
				Object finstance = getPrivate(null, foamShared, "config");
				Class foamFixConfig = forName("pl.asie.foamfix.shared.FoamFixConfig");
				setPrivate(finstance, false, foamFixConfig, "lwWeakenResourceCache");
				setPrivate(finstance, false, foamFixConfig, "lwRemovePackageManifestMap");
				
				//Forces foamfix.cfg to be created
				Class foamCfgClazz = forName("pl.asie.foamfix.shared.FoamFixConfig");
				File foamCfgFile = new File("config", "foamfix.cfg");
				Object instance = foamCfgClazz.newInstance();
				Method init = foamCfgClazz.getDeclaredMethod("init", File.class, boolean.class);
				if(init == null)
					init = foamCfgClazz.getDeclaredMethod("init", File.class, Boolean.class);
				init.setAccessible(true);
				init.invoke(instance, foamCfgFile, true);
				
				//Disable their fix
				Object[] lines = getFileLines(foamCfgFile, true).toArray();
				for(int i=0;i<lines.length;i++)
					lines[i] = ((String) lines[i]).replace("removePackageManifestMap=true", "removePackageManifestMap=false").replace("weakenResourceCache=true", "weakenResourceCache=false");
				saveFileLines(lines, foamCfgFile);
			}
			catch(Throwable t)
			{
				t.printStackTrace();
			}
		}
		//Support 1.7.10 FoamFix BS
		else if(foamBF != null)
		{
			System.err.println("FoamFix for MC 1.7.10 has been found. FoamFix is not needed for 1.7.10 and actually causes graphical bugs. Please Use Optifine instead");
			try
			{
				Field fieldInstance = null;
				Field fieldSettings = null;
				for(Field f : foamBF.getDeclaredFields())
				{
					String name = f.getName();
					if(name.equalsIgnoreCase("instance"))
						fieldInstance = f;
					else if(name.equalsIgnoreCase("settings"))
						fieldSettings = f;
				}
				fixFields(fieldInstance, fieldSettings);
				
				Object instance = fieldInstance.get(null);
				Object settingsInstance = fieldSettings.get(instance);
				Class settingsClazz = settingsInstance.getClass();
				Field rc = settingsClazz.getDeclaredField("lwWeakenResourceCache");
				Field pkg = settingsClazz.getDeclaredField("lwRemovePackageManifestMap");
				fixFields(rc, pkg);
				rc.set(settingsInstance, false);
				pkg.set(settingsInstance, false);
			}
			catch(Throwable t)
			{
				t.printStackTrace();
			}
		}
		
		//StopMemoryOverflow just in case
		stopMemoryOverflow(clforge);
	}
	
	/**
	 * Verifies that LaunchClassLoader Map / Set are instances of the Dummy Version. Only Checks LaunchClassLoader.class values
	 */
	public static void verify(ClassLoader clforge)
	{
		try
		{
			Class launch = forName("net.minecraft.launchwrapper.Launch");
			if(launch == null)
				return;
			Set<ClassLoader> cls = getClassLoaders(launch, clforge);
			for(ClassLoader classLoader : cls)
			{
				if(classLoader == null)
					continue;
				System.out.println("Verifying ClassLoader:" + classLoader);
				
				Class actualClazz = classLoader.getClass();
				String actualName = "";
				
				while(actualClazz != null && !isLibClassLoader(libLoaders, actualName))
				{
					actualName = actualClazz.getName();
					Map cachedClasses = (Map) getPrivate(classLoader, actualClazz, "cachedClasses");
					Map resourceCache = (Map) getPrivate(classLoader, actualClazz, "resourceCache");
					Map packageManifests = (Map) getPrivate(classLoader, actualClazz, "packageManifests");
					Set negativeResourceCache = (Set) getPrivate(classLoader, actualClazz, "negativeResourceCache");
					boolean flag = actualName.equals("net.minecraft.launchwrapper.LaunchClassLoader");
					
					if(cachedClasses != null && !(cachedClasses instanceof DummyMap))
						System.err.println((flag ? "LaunchClassLoader" : actualName) + "#cachedClasses is Unoptimized! size:" + cachedClasses.size() + " Class:" + cachedClasses.getClass());
					if(resourceCache != null && !(resourceCache instanceof DummyMap))
						System.err.println((flag ? "LaunchClassLoader" : actualName) + "#resourceCache is Unoptimized! size:" + resourceCache.size() + " Class:" + resourceCache.getClass());
					if(packageManifests != null && !(packageManifests instanceof DummyMap))
						System.err.println((flag ? "LaunchClassLoader" : actualName) + "#packageManifests is Unoptimized! size:" + packageManifests.size() + " Class:" + packageManifests.getClass());
					if(negativeResourceCache != null && !(negativeResourceCache instanceof DummySet))
						System.err.println((flag ? "LaunchClassLoader" : actualName) + "#negativeResourceCache is Unoptimized! size:" + negativeResourceCache.size() + " Class:" + negativeResourceCache.getClass());
					
					if(flag)
						break;
					actualClazz = actualClazz.getSuperclass();
				}
			}
		}
		catch(Throwable t)
		{
			System.err.println("FATAL ERROR HAS OCCURED VERIFYING THE LaunchClassLoader Memory Leaks Was Fixed!");
			t.printStackTrace();
		}
	}

	private static void setDummyMap(Object classLoader, Class clazzLoaderClazz, String mapName)
	{
		Map init = (Map) getPrivate(classLoader, clazzLoaderClazz, mapName);
		if(init == null) 
		{
			System.err.println(clazzLoaderClazz.getName() + "#" + mapName + " is missing!");
			return;
		}
		init.clear();
		setPrivate(classLoader, new DummyMap(), clazzLoaderClazz, mapName);
	}

	private static void setDummySet(Object classLoader, Class clazzLoaderClazz, String setName)
	{
		Set init = (Set) getPrivate(classLoader, clazzLoaderClazz, setName);
		if(init == null)
		{
			System.err.println(clazzLoaderClazz.getName() + "#" + setName + " is missing!");
			return;
		}
		init.clear();
		setPrivate(classLoader, new DummySet(), clazzLoaderClazz, setName);
	}
	
	public static Set<ClassLoader> getClassLoaders(Class launch, ClassLoader clforge) 
	{
		Set<ClassLoader> loaders = Collections.newSetFromMap(new IdentityHashMap(5));
		ClassLoader classLoader = (ClassLoader) getPrivate(null, launch, "classLoader", false);
		ClassLoader currentLoader = LaunchClassLoaderFix.class.getClassLoader();
		ClassLoader contextLoader = getContextClassLoader();
		
		loaders.add(classLoader);
		loaders.add(clforge);
		loaders.add(currentLoader);
		loaders.add(contextLoader);
		
		return loaders;
	}

	public static ClassLoader getContextClassLoader() 
	{
		try
		{
			return Thread.currentThread().getContextClassLoader();
		}
		catch(Throwable t)
		{
			t.printStackTrace();
		}
		return null;
	}
	
	public static Field modifiersField;
	static
	{
		try
		{
			modifiersField = Field.class.getDeclaredField("modifiers");
			modifiersField.setAccessible(true);
		}
		catch(Throwable t)
		{
			t.printStackTrace();
		}
	}
	
	private static void fixFields(Field... fields) throws IllegalArgumentException, IllegalAccessException
	{
		for(Field f : fields) 
		{
			f.setAccessible(true);
			modifiersField.setInt(f, f.getModifiers() & ~Modifier.FINAL);
		}
	}

	public static Object getPrivate(Class clazz, String field) {
		return getPrivate(null, clazz, field);
	}
	
	public static Object getPrivate(Object instance, Class<?> clazz, String name) {
		return getPrivate(instance, clazz, name, true);
	}
	
	public static Object getPrivate(Object instance, Class<?> clazz, String name, boolean print)
	{
		try
		{
	    	Field f = clazz.getDeclaredField(name);
			f.setAccessible(true);
			modifiersField.setInt(f, f.getModifiers() & ~Modifier.FINAL);
			return f.get(instance);
		}
		catch(NoSuchFieldException e)
		{
			
		}
		catch(Throwable t)
		{
			if(print)
				t.printStackTrace();
		}
        return null;
	}
	
	public static void setPrivate(Object instance, Object toset, Class clazz, String name)
	{
		try
		{
	    	Field f = clazz.getDeclaredField(name);
			f.setAccessible(true);
			modifiersField.setInt(f, f.getModifiers() & ~Modifier.FINAL);
			f.set(instance, toset);
		}
		catch(NoSuchFieldException e)
		{
			
		}
		catch(Throwable t)
		{
			t.printStackTrace();
		}
	}
	
    public static <T> Class<T> forName(String className)
    {
    	try
    	{
    		return (Class<T>) Class.forName(className);
    	}
    	catch(ClassNotFoundException e)
    	{
    		
    	}
    	catch(Throwable t)
    	{
    		t.printStackTrace();
    	}
    	return null;
    }
    
    public static boolean instanceOf(Class base, Class compare)
    {
    	return base.isAssignableFrom(compare);
    }
    
    public static boolean instanceOf(Class base, Object obj)
    {
    	return base.isInstance(obj);
    }
    
	public static void saveFileLines(Object[] list,File f)
	{
		BufferedWriter writer = null;
		try
		{
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), Charset.forName("UTF-8") ) );
			for(Object s : list)
				writer.write(s + lineSeparator());
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			try 
			{
				if(writer != null)
					writer.close();
			}
			catch (IOException e)
			{
				
			}
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
				reader = new BufferedReader(new InputStreamReader(new FileInputStream(f), Charset.forName("UTF-8") ) );
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
					System.err.println("Unable to Close InputStream this is bad");
				}
			}
		}
		
		return list;
	}
	
	public static String lineSeparator()
	{
		return System.getProperty("java.version").replace("'", "").replace("\"", "").trim().startsWith("1.6.") ? System.getProperty("line.separator") : System.lineSeparator();
	}

}
