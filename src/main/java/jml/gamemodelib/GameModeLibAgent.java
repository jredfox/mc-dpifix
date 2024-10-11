package jml.gamemodelib;

import java.io.Closeable;
import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

public class GameModeLibAgent {
	
	public static void premain(String agentArgs, Instrumentation inst)
	{
		try
		{
			System.setProperty("gamemodelib.agent", "true");
			GameModeLib.load();
			GameModeLib.fixDPI();
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
				removeAgentClassPath(jarFile, true, sy, parent, (sy == context ? null : context));
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
	
    public static void removeAgentClassPath(File jarPath, boolean removeCP, ClassLoader... classLoaders) throws Exception 
    {
    	if(removeCP)
    		removeCP(jarPath);
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
	            
	            //Remove it from List<Loader> loaders
	            Class loaderClazz = forName(urlCPClazz.getName() + "$Loader", classLoader);
	            if(loaderClazz == null)
	            {
	            	System.err.println("Unable to find " + urlCPClazz.getName() + "$Loader Please Report to https://github.com/jredfox/mc-dpifix/issues");
	            	System.err.println(System.getProperty("java.version") + " " + System.getProperty("java.vendor"));
	            	continue;
	            }
	            
	            Field loaderField = getField(urlCPClazz, "loaders");
	            if(loaderField != null)
	            {
	            	Collection loaders = getCollection(loaderField.get(ucp));
	                Iterator it = loaders.iterator();
	                Field getBase = getField(loaderClazz, "base", "url", "csu", "baseurl", "jarurl", "base_url");//guess other java distro's field names
	                while(it.hasNext())
	                {
	                	Object loader = it.next();
	                	URL lurl = (URL) getBase.get(loader);
	                	if(jarURL.equals( getFileFromURL(lurl).toURI().toURL() ) )
	                	{
	                		it.remove();
	                		if(loader instanceof Closeable)
	                		{
	                			try
	                			{
	                				((Closeable)loader).close();
	                			}
	                			catch(Exception e)
	                			{
	                				e.printStackTrace();
	                			}
	                		}
	                	}
	                }
	            }
    		}
    		catch(Throwable t)
    		{
    			t.printStackTrace();
    		}
    	}
    }

    /**
     * Removes the jar from the java.class.path property to prevent future reloading of the jar
     * This method doesn't remove it from the class loaders only the java property
     */
    public static void removeCP(File jar)
    {
    	jar = jar.getAbsoluteFile();
		String[] cp = System.getProperty("java.class.path").replace(";", File.pathSeparator).split(File.pathSeparator);
		StringBuilder b = new StringBuilder();
		for(String c : cp)
		{
			if(!jar.equals(new File(c).getAbsoluteFile()))
			{
				if(b.length() > 0)
					b.append(File.pathSeparator);
				b.append(c);
			}
		}
		String built = b.toString();
		System.setProperty("java.class.path", built);
	}

	/**
     * Safely gets a collection whether it be a Map or Collection
     */
    public static Collection getCollection(Object o) {
		return o instanceof Map ? ((Map)o).values() : (Collection) o;
	}

	/**
     * Removes an Object from either a Collection(ArrayList) or Map (HashMap)
     */
    public static void remove(Object l, Object o) 
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