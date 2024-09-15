package jredfox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;

public class PropertyConfig
{
	public Properties properties = new Properties();
	public File property_file;
	
	public PropertyConfig(File f)
	{
		property_file = f.getAbsoluteFile();
	}
	
	public void load()
	{
		BufferedReader input = null;
		try
		{
			this.createFile();
			input = new BufferedReader(new InputStreamReader(new FileInputStream(this.property_file), StandardCharsets.UTF_8) );
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
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(this.property_file),StandardCharsets.UTF_8 ) );
			for(Map.Entry<Object, Object> entry : this.properties.entrySet())
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
		if(!this.property_file.exists())
		{
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
		return Boolean.parseBoolean(this.getKey(key, def ? "true" : "false"));
	}
	
	public String getStr(String key)
	{
		return this.getKey(key, "");
	}
	
	protected String getKey(String key, String def) 
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
