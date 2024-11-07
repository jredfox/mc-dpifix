package jml.gamemodelib;

import java.io.Closeable;
import java.io.File;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import jredfox.DpiFix;

public class GameModeLib {
	
	public static boolean debug = Boolean.parseBoolean(System.getProperty("gamemodelib.debug", "false"));
	public static File jarFile = GameModeLib.getFileFromClass(GameModeLibAgent.class);
	public static boolean hasForge = GameModeLib.forName("net.minecraftforge.common.ForgeVersion", GameModeLibAgent.class.getClassLoader()) != null;
	
	public static void init()
	{
		System.setProperty("gamemodelib.agent", "true");
		if(System.getProperty("gamemodelib.cfg") == null)
			System.setProperty("gamemodelib.cfg", String.valueOf(isInCoreMods()) );
	}

	public static void load()
	{
		//Use Mod Config useful for when both the javaagent and mod are used at the same time which is actually required in 1.5x version of the mod
		if( Boolean.parseBoolean(System.getProperty("gamemodelib.cfg", "false")) )
			DpiFix.loadConfig();
		
		DpiFix.load();
	}

	public static void fixDPI()
	{
		if(DpiFix.dpifix)
			DpiFix.fixProcessDPI();
	}

	public static void setHighPriority() 
	{
		if(DpiFix.highPriority)
			DpiFix.setHighProcessPriority();
	}
	
	/**
	 * @return true if forge is installed and the coremod should load 1.5 - 1.12.2 supported
	 */
	public static boolean isInCoreMods()
	{
		return hasForge && (net.minecraftforge.common.ForgeVersion.getMajorVersion() < 8 ? GameModeLib.jarFile.getParentFile().equals(new File("coremods").getAbsoluteFile()) : GameModeLib.jarFile.getParent().startsWith(new File("mods").getAbsolutePath()));
	}
	
	public static boolean isInMods()
	{
		return hasForge && GameModeLib.jarFile.getParent().startsWith(new File("mods").getAbsolutePath());
	}
	
	public static void removeAgent() 
	{
		try
		{
			//Remove agent from classpath so forge doesn't load our "@Mod" when not in coremods or mods folder
			if(Boolean.parseBoolean(System.getProperty("gamemodelib.removeAgent", "false")) || Boolean.parseBoolean(System.getProperty("gamemodelib.removeModAgent", "true")) && hasForge)
			{
				ClassLoader sy = ClassLoader.getSystemClassLoader();
				ClassLoader parent = GameModeLib.getParentCL(sy);
				ClassLoader context = Thread.currentThread().getContextClassLoader();
				GameModeLib.removeAgentClassPath(jarFile, true, sy, parent, (sy == context ? null : context));
			}
		}
		catch(Throwable t)
		{
			t.printStackTrace();
		}
	}
	
	
	/**
	 * Does not work in instances where the class loader is replaced and the parent is set to null or a different parent
	 * Fixing it would be a nightmare and significantly slow down the process for removing them.
	 * - Because just checking instanceof would check both all superclasses (slow) and interfaces (slow)
	 * - Then Instead of just calling normal methods they would all be gotten using reflection (slow)
	 * - Since this works on all major java distros and shouldn't be an issue with Minecraft or Forge I refuse to make it slower
	 * It works as long as another java agent before this doesn't replace the class loader with a different parent before this gets called
	 * @author jredfox
	 */
    public static void removeAgentClassPath(File jarFile, boolean removeCP, ClassLoader... classLoaders) throws Exception 
    {
    	if(removeCP)
    		removeCP(jarFile);
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
		        remove(urls, jarFile);
		        
		        //Remove jarURL from path
		        Field u2 = getField(urlCPClazz, "path", "paths");
		        Object path = u2.get(ucp);
		        remove(path, jarFile);
		        
		        //Remove open jarURL from path
	            Field lmapField = getField(urlCPClazz, "lmap");
	            if(lmapField != null)
	            {
		            lmapField.setAccessible(true);
		            Object lmap = lmapField.get(ucp);
		            remove(lmap, jarFile);
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
	                	URL lurl = new URL(getBase.get(loader).toString());
	                	if(jarFile.equals(getFileFromURL(lurl)) )
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
	                
	                if(debug) 
	                {
		                for(Object loader : loaders)
		                	System.out.println("loaders:" + getBase.get(loader).toString());
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
		String[] cp = System.getProperty("java.class.path").replace(";", File.pathSeparator).split("\\" + File.pathSeparator, -1);
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
    	if(debug)
    		System.out.println(System.getProperty("java.class.path") + "\n");
	}

	/**
     * Safely gets a collection whether it be a Map or Collection
     */
    public static Collection getCollection(Object o) {
		return o instanceof Map ? ((Map)o).values() : (Collection) o;
	}

	/**
     * Removes an Object from either a Collection(ArrayList) or Map (HashMap)
	 * @throws MalformedURLException 
     */
    public static void remove(Object l, File f) throws MalformedURLException 
    {
    	Collection col = l instanceof Collection ? (Collection) l : ((Map)l).keySet();
		Iterator it = col.iterator();
		while(it.hasNext())
		{
			URL url = new URL(it.next().toString());//support string only mode
			if(f.equals(getFileFromURL(url)))
				it.remove();
		}
		if(debug)
		{
	        System.out.println("list#size:" + col.size());
	        String list = l instanceof Collection ? "list:" : "lmap:";
			for(Object o : col)
				System.out.println(list + l.hashCode() + " " + o);
		}
	}
    
	public static <T> Class<T> forName(String className)
    {
    	return forName(className, GameModeLibAgent.class.getClassLoader());
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

	public static ClassLoader getParentCL(ClassLoader cl) {
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
	 * This assumes the file is contained in an archive file such as a zip or a jar. If it's a folder derp jar then it will return the physical path of the .class file
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
			return new File(new URL(url).toURI()).getAbsoluteFile();
		}
		catch (Exception e) 
		{
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * @return The Root Directory of the Jar when the Jar is a Folder
	 */
	public static File getDerpJar(File clazzFile, Class clazz)
	{
		String pjar = clazzFile.getPath().replace("\\", "/");
		return new File(pjar.substring(0, pjar.lastIndexOf(clazz.getName().replace(".", "/") + ".class")));
	}

}
