package jredfox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

public class PropertyConfig
{
	public Properties properties = new Properties();
	public File property_file;
	private boolean dirty;
	
	public PropertyConfig(File f)
	{
		property_file = f.getAbsoluteFile();
	}
	
	public void load()
	{
		if(!this.property_file.exists())
		{
			this.dirty = true;
			return;
		}
		this.dirty = false;
		
		BufferedReader input = null;
		try
		{
			input = new BufferedReader(new InputStreamReader(new FileInputStream(this.property_file), Charset.forName("UTF-8") ) );
			properties.load(input);
		}
		catch(Throwable t)
		{
			t.printStackTrace();
		}
		finally
		{
			DpiFix.closeQuietly(input);
		}
	}

	/**
	 * Fast save method for Properties unlike {@link Properties#store(java.io.OutputStream, String)} which can take 20MS of delay
	 */
	public void save()
	{
		BufferedWriter writer = null;
		StringBuilder sb = new StringBuilder();
		try
		{
			this.createFile();
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(this.property_file), Charset.forName("UTF-8") ) );
			for(Map.Entry<Object, Object> entry : new TreeMap<Object, Object>(this.properties).entrySet())
			{
				writer.write(sb.append((String)entry.getKey()).append("=").append(entry.getValue()).append("\r\n").toString());
				sb.setLength(0);
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			DpiFix.closeQuietly(writer);
		}
	}
	
	protected void createFile() throws IOException
	{
		if(this.dirty || !this.property_file.exists())
		{
			this.dirty = false;
			this.property_file.getParentFile().mkdirs();
			this.property_file.createNewFile();
		}
	}
	
	public boolean get(String key)
	{
		return this.get(key, true);
	}
	
	public boolean get(String key, boolean def)
	{
		return Boolean.parseBoolean(this.getKey(key, def ? "true" : "false").toLowerCase());
	}
	
	public int getInt(String key, int def) 
	{
		return Integer.parseInt(this.getKey(key, String.valueOf(def)));
	}
	
	public String getStr(String key)
	{
		return this.getKey(key, "");
	}
	
	public String getKey(String key, String def) 
	{
		String prop = this.properties.getProperty(key);
		if(prop == null) 
		{
			this.properties.put(key, def);
			prop = def;
		}
		return prop;
	}

}
