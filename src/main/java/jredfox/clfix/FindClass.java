package jredfox.clfix;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.Opcodes;
import org.ow2.asm.tree.AbstractInsnNode;
import org.ow2.asm.tree.ClassNode;
import org.ow2.asm.tree.MethodInsnNode;
import org.ow2.asm.tree.MethodNode;

import jredfox.CoreUtils;
import jredfox.forgeversion.ForgeVersionProxy;
import net.minecraft.launchwrapper.Launch;

/**
 * Print all findClass instance calls probable and absolute
 * @author jredfox
 */
public class FindClass {
	
	public static void main(String[] args)
	{
		File mcDir = new File(args[0].trim()).getAbsoluteFile();
		boolean possible = args.length > 1 ? Boolean.parseBoolean(args[1].trim()) : true;
		if(!mcDir.exists() || !mcDir.isDirectory())
		{
			System.err.println(".minecraft Instance Folder Does not Exist or is not a Directory!");
			return;
		}
		
		Set<File> fileList = new HashSet<File>(500);
		String[] exts =  new String[]{".jar", ".zip"};
		File modDir = new File(mcDir, "mods");
		File coremodDir = new File(mcDir, "coremods");
		File jarModDir = new File(mcDir, "jarmods");
		getDirFiles(modDir, fileList, exts);
		getDirFiles(coremodDir, fileList, exts);
		getDirFiles(jarModDir, fileList, exts);
		for(File fileJar : fileList)
		{
			ZipFile jar = null;
			try
			{
				jar = new ZipFile(fileJar);
	            for (ZipEntry ze : Collections.list(jar.entries()))
	            {
	            	if(ze.getName().endsWith(".class"))
	            	{
	            		InputStream stream = null;
	            		try
	            		{
	            			stream = jar.getInputStream(ze);
	            			ClassNode c = CoreUtils.getClassNode(ForgeVersionProxy.toByteArray(stream));
	            			for(MethodNode m : c.methods)
	            			{
	            				for(AbstractInsnNode a : m.instructions.toArray())
	            				{
	            					if(a instanceof MethodInsnNode && a.getOpcode() != Opcodes.INVOKESTATIC)
	            					{
	            						MethodInsnNode insn = (MethodInsnNode) a;
	            						if(insn.name.equals("findClass") && insn.desc.equals("(Ljava/lang/String;)Ljava/lang/Class;"))
	            						{
	            							if(insn.owner.equals("cpw/mods/fml/relauncher/RelaunchClassLoader") || insn.owner.equals("net/minecraft/launchwrapper/LaunchClassLoader"))
	            							{
	            								System.out.println("Error Found findClass Mod:" + fileJar + " Class:"  + c.name + " method:" + m.name + m.desc);
	            							}
	            							else if(possible)
	            							{
	            								System.out.println("Possible findClass Mod:" + fileJar + " Class:"  + c.name + " method:" + m.name + m.desc);
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
	            		finally
	            		{
	            			if(stream != null)
	            			{
	            				close(stream);
	            			}
	            		}
	            	}
	            }
			} 
			catch (IOException e) 
			{
				e.printStackTrace();
			}
			finally
			{
				close(jar);
			}
		}
	}

	private static void close(Closeable in)
	{
		try 
		{
			if(in != null)
				in.close();
		} 
		catch (Throwable e) 
		{
			e.printStackTrace();
		}
	}

	public static void getDirFiles(File dir, Set<File> files, String[] exts) 
	{
		boolean hasStar = exts[0].equals("*");
	    for (File file : dir.listFiles()) 
	    {
	    	if(file.isDirectory())
	    		getDirFiles(file, files, exts);
	    	
	    	if(hasStar)
	    	{
	    		files.add(file);
	    	}
	    	else
	    	{
	    		String fname = file.getName();
		    	for(String ext : exts)
		    	{
		    		if(fname.endsWith(ext))
		    		{
		    			files.add(file);
		    			break;
		    		}
		    	}
	    	}
	    }
	}
	
	
	
}
