package jredfox.clfix;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Fix LaunchClassLoader Memory Leaks Supports launchwrapper 1.3 - 1.12
 * V2.0.0 Is alot more robust then V1.0.0 In EvilNotchLib as it handles all possible ClassLoaders & Shadow Variables
 * Does not Override FoamFix please Use EvilNotchLib for 1.12.2 if you plan to install FoamFix
 * @author jredfox
 */
public class LaunchClassLoaderFix {
	
	public static final String VERSION = "2.0.0";
	public static ClassLoader legacyClassLoader = null;
	
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
				while(!actualClassLoader.getName().startsWith("java."))
				{
					setDummyMap(cl, actualClassLoader, "cachedClasses");
					setDummyMap(cl, actualClassLoader, "resourceCache");
					setDummyMap(cl, actualClassLoader, "packageManifests");
					setDummySet(cl, actualClassLoader, "negativeResourceCache");
					actualClassLoader = actualClassLoader.getSuperclass();
				}
			}
		}
		catch(Throwable t)
		{
			System.err.println("FATAL ERROR HAS OCCURED PATCHING THE LaunchClassLoader Memory Leaks!");
			t.printStackTrace();
		}
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

	private static Object getPrivate(Class clazz, String field) {
		return getPrivate(null, clazz, field);
	}
	
	private static Object getPrivate(Object instance, Class<?> clazz, String name) {
		return getPrivate(instance, clazz, name, true);
	}
	
	private static Object getPrivate(Object instance, Class<?> clazz, String name, boolean print)
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
    
    public static boolean instanceOf(Class base, Object obj)
    {
    	return base.isInstance(obj);
    }

}
