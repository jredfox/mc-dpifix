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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fix LaunchClassLoader Memory Leaks Supports launchwrapper 1.3 - 1.12
 * V2.0.0 Is alot more robust then V1.0.0 In EvilNotchLib as it handles all possible ClassLoaders & Shadow Variables
 * Does not Override FoamFix please Use EvilNotchLib for MC 1.12.2 if you plan to install FoamFix
 * @author jredfox
 */
public class LaunchClassLoaderFix {
	
	public static final String VERSION = "2.0.0";
	public static ClassLoader legacyClassLoader = null;
	
	/**
	 * can be called at any time
	 */
	public static void stopMemoryOverflow()
	{
		try
		{
			Class launch = forName("net.minecraft.launchwrapper.Launch");
			if(launch == null)
			{
				System.err.println("LaunchWrapper is Missing!");
				return;
			}
			
			System.out.println("Fixing RAM Leak of LaunchClassLoader");
			String clazzLoaderName = "net.minecraft.launchwrapper.LaunchClassLoader";
			Class clazzLoaderClazz = forName(clazzLoaderName);
			ClassLoader classLoader = (ClassLoader) getPrivate(null, launch, "classLoader", false);
			ClassLoader currentLoader = LaunchClassLoaderFix.class.getClassLoader();
			ClassLoader contextLoader = getContextClassLoader();
			if(legacyClassLoader == null)
				legacyClassLoader = currentLoader;
			
			Map<String, ClassLoader> loaders = new HashMap(5);
			loaders.put(toNString(classLoader), classLoader);
			loaders.put(toNString(legacyClassLoader), legacyClassLoader);
			loaders.put(toNString(currentLoader), currentLoader);
			loaders.put(toNString(contextLoader), contextLoader);
			for(ClassLoader cl : loaders.values())
			{
				if(cl == null)
					continue;
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
				while(flag ? true : !actualClassLoader.getName().startsWith("java.") );
			}
		}
		catch(Throwable t)
		{
			System.err.println("FATAL ERROR HAS OCCURED PATCHING THE LaunchClassLoader Memory Leaks!");
			t.printStackTrace();
		}
	}
	
	/**
	 * Must be called during preinit after foamfix has done their preinit or later after preinit
	 */
	public static void stopMemoryOverflowFoamFix()
	{
		Class foamCfgClazz = forName("pl.asie.foamfix.shared.FoamFixConfig");
		File foamCfgFile = new File("config", "foamfix.cfg");
		if(foamCfgClazz != null)
		{
			try
			{
				System.out.println("Disabling FoamFix's \"Fix\" for LaunchClassLoader!");
				
				//Forces foamfix.cfg to be created
				Object instance = foamCfgClazz.newInstance();
				Method init = foamCfgClazz.getDeclaredMethod("init", File.class, boolean.class);
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
		
		//StopMemoryOverflow just in case
		stopMemoryOverflow();
	}

	private static ClassLoader getContextClassLoader() 
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
	
    public static String toNString(Object o) {
        return o == null ? "0" : (o.getClass().getName() + "@" + System.identityHashCode(o));
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
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f),StandardCharsets.UTF_8 ) );
			for(Object s : list)
				writer.write(s + System.lineSeparator());
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

}
