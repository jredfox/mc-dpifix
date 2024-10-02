package jredfox.clfix;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Set;

/**
 * Fix LaunchClassLoader Memory Leaks Supports Launch Class Loader 1.3 - 1.12
 * @author jredfox
 */
public class LaunchClassLoaderFix {
	
	public static final String VERSION = "2.0.0";
	
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
			Object classLoader = getPrivate(launch, "classLoader");
			Class clazzLoaderClazz = forName("net.minecraft.launchwrapper.LaunchClassLoader");
			setDummyMap(classLoader, clazzLoaderClazz, "cachedClasses");
			setDummyMap(classLoader, clazzLoaderClazz, "resourceCache");
			setDummyMap(classLoader, clazzLoaderClazz, "packageManifests");
			setDummySet(classLoader, clazzLoaderClazz, "negativeResourceCache");
			
			//Support Shadow Variables for Dumb Mods Replacing Launch#classLoader
			Class actualClassLoader = classLoader.getClass();
			if(!clazzLoaderClazz.getName().equals(actualClassLoader.getName()))
			{
				System.out.println("Fixing RAM Leak Shadow Variables:" + actualClassLoader.getName());
				setDummyMap(classLoader, actualClassLoader, "cachedClasses");
				setDummyMap(classLoader, actualClassLoader, "resourceCache");
				setDummyMap(classLoader, actualClassLoader, "packageManifests");
				setDummySet(classLoader, actualClassLoader, "negativeResourceCache");
			}
		}
		catch(Throwable t)
		{
			System.err.println("FATAL ERROR HAS OCCURED PATCHING THE LaunchClassLoader Memory Leaks!");
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
	
	private static Object getPrivate(Object instance, Class<?> clazz, String name)
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

}
