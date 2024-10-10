package jml.gamemodelib;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.ProtectionDomain;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class GameModeLibAgent {
	
	public static void premain(String agentArgs, Instrumentation inst)
	{
		System.setProperty("gamemodelib.agent", "true");
		boolean fixDPI = Boolean.parseBoolean(System.getProperty("gamemodelib.dpi", "false"));
		boolean highPriority = Boolean.parseBoolean(System.getProperty("gamemodelib.high", "false"));
		GameModeLib.load();
		try
		{
			if(fixDPI)
				GameModeLib.fixDPI();
			if(highPriority)
				GameModeLib.setHighPriority();
		}
		catch(Throwable t)
		{
			t.printStackTrace();//Handle Natives not found for the OS
		}
		
		try
		{
			//Remove agent from classpath so forge doesn't load our "@Mod" when not in coremods or mods folder
			if(Boolean.parseBoolean(System.getProperty("gamemodelib.removeAgent", "false")) || Boolean.parseBoolean(System.getProperty("gamemodelib.removeModAgent", "true")) && forName("net.minecraftforge.common.ForgeVersion", GameModeLibAgent.class.getClassLoader()) != null)
			{
				File jarFile = getFileFromClass(GameModeLibAgent.class);
				ClassLoader sy = ClassLoader.getSystemClassLoader();
				ClassLoader parent = getParentCL(ClassLoader.getSystemClassLoader());
				ClassLoader context = Thread.currentThread().getContextClassLoader();
				removeAgentClassPath(jarFile, sy, parent, (sy == context ? null : context));
			}
		}
		catch(Throwable t)
		{
			t.printStackTrace();
		}
	}

	public static void agentmain(String agentArgs, Instrumentation inst) 
	{
		
	}
	
    public static void removeAgentClassPath(File jarPath, ClassLoader... classLoaders) throws Exception 
    {
    	URL jarURL = jarPath.toURI().toURL();
    	for(ClassLoader classLoader : classLoaders)
    	{
    		if(!(classLoader instanceof URLClassLoader))
    			continue;//skip null or non URLClassLoader
    		
    		try
    		{
	    		System.out.println("Removing GameModeLib (Dpi-Fix) Agent From CP:" + classLoader);
	    		Class urlClassLoaderClazz = forName("java.net.URLClassLoader", classLoader);
		        Field ucpField = getField(urlClassLoaderClazz, "ucp");
		        Object ucp = ucpField.get(classLoader);
		        Class urlCPClazz = ucpField.getType();//Gets the actual Field Class and not the Object's Instance class
		        if(urlCPClazz == null)
		        	urlCPClazz = forName("sun.misc.URLClassPath", classLoader);//On Error Default to sun.misc.URLClassPath
		        
		        //Remove jarURL from urls
		        Field u = getField(urlCPClazz, "urls", "url");
		        Object urls = u.get(ucp);
		        remove(urls, jarURL);
		        
		        //Remove jarURL from path
		        Field u2 = getField(urlCPClazz, "path", "paths");
		        Object path = u2.get(ucp);
		        remove(path, jarURL);
		        
		        //Remove open jarURL from path
	            Field lmapField = getField(urlCPClazz, "lmap");
	            if(lmapField != null)
	            {
		            lmapField.setAccessible(true);
		            Object lmap = lmapField.get(ucp);
		            remove(lmap, jarURL);
	            }
    		}
    		catch(Throwable t)
    		{
    			t.printStackTrace();
    		}
    	}
    }
    
    /**
     * Removes an Object from either a Collection(ArrayList) or Map (HashMap)
     */
    private static void remove(Object l, Object o) 
    {
		if(l instanceof Collection)
			((Collection)l).remove(o);
		else
			((Map)l).remove(o);
	}

	public static <T> Class<T> forName(String className, ClassLoader cl)
    {
    	try
    	{
    		return (Class<T>) Class.forName(className, true, cl);
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
    
	public static Field getField(Class c, String... fields) 
	{
		return getField(c, true, fields);
	}
    
	/**
	 * Trys to get exact Field First and if not found returns the first Field that is found with ignoring case if the search ignores case
	 */
	public static Field getField(Class c, boolean ignoreCase, String... fields) 
	{
		try
		{
			Field matchField = null;
			for(Field f : c.getDeclaredFields())
			{
				for(String fname : fields)
				{
					if(ignoreCase && matchField == null && f.getName().equalsIgnoreCase(fname))
						matchField = f;
					if(f.getName().equals(fname))
					{
						f.setAccessible(true);
						return f;
					}
				}
			}
			
			if(matchField != null)
				matchField.setAccessible(true);
			
			return matchField;
		}
		catch(Throwable t) 
		{
			t.printStackTrace(); 
		}
		return null;
	}

	private static ClassLoader getParentCL(ClassLoader cl) {
		try
		{
			return cl.getParent();
		}
		catch(Throwable t)
		{
			t.printStackTrace();
		}
		return null;
	}
	
	/**
	 * get a file from a class Does not support Eclipse's Jar In Jar Loader but does support javaw java and URLClassLoaders
	 */
	public static File getFileFromClass(Class clazz)
	{
		URL jarURL = clazz.getProtectionDomain().getCodeSource().getLocation();//get the path of the currently running jar
		return getFileFromURL(jarURL);
	}
	
	private static File getFileFromURL(URL jarURL) 
	{
		String j = jarURL.toExternalForm().replace("jar:/", "").replace("jar:", "");
		if(j.contains("!"))
			j = j.substring(0, j.indexOf('!'));
		return getFileFromURL(j);
	}

	public static File getFileFromURL(String url)
	{
		try 
		{
			return new File(new URL(url).toURI());
		}
		catch (Exception e) 
		{
			e.printStackTrace();
		}
		return null;
	}

}