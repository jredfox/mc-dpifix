package jredfox;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraftforge.common.ForgeVersion;

public class DpiFixAnnotation implements IClassTransformer {
	
	@Override
	public byte[] transform(String name, String transformedName, byte[] basicClass) 
	{
		if(transformedName.equals("jredfox.dpimod.DpiFixModLegacy"))
		{
			try
			{
				ClassNode classNode = new ClassNode();
	            ClassReader classReader = new ClassReader(basicClass);
	            classReader.accept(classNode, 0);
	            
	            DpiFixAnnotation.patchAtMod(classNode);
	            
	            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
	            classNode.accept(classWriter);
	            
	            byte[] bytes = classWriter.toByteArray();
	            if(Boolean.parseBoolean(System.getProperty("asm.dump", "false")))
	            	dumpFile(transformedName, bytes);
	         
	            
	            return bytes;
			}
			catch(Throwable t)
			{
				t.printStackTrace();
			}
		}
		return basicClass;
	}
	
	/**
	 * Patches the @Mod annotation for versions < 1.7.2
	 */
	public static void patchAtMod(ClassNode classNode) 
	{
		if(ForgeVersion.getMajorVersion() < 10)
		{
			//Remove acceptableRemoteVersions = "*"
			AnnotationNode atmod = getAnnotation(classNode, "Lcpw/mods/fml/common/Mod;");
			for(int i=0;i<atmod.values.size();i++)
			{
				Object o = atmod.values.get(i);
				if(o instanceof String && ((String) o).equals("acceptableRemoteVersions"))
				{
					atmod.values.remove(i + 1);
					atmod.values.remove(i);
				}
			}
			
			//Append @cpw.mods.fml.common.network.NetworkMod(clientSideRequired = false, serverSideRequired = false)
			System.out.println("Adding Annotation @NetworkMod(clientSideRequired = false, serverSideRequired = false) to DpiFixModLegacy");
			AnnotationNode atnet = new AnnotationNode("Lcpw/mods/fml/common/network/NetworkMod;");
			atnet.values = new ArrayList(5);
			atnet.values.add("clientSideRequired");
			atnet.values.add(false);
			atnet.values.add("serverSideRequired");
			atnet.values.add(false);
			classNode.visibleAnnotations.add(atnet);
		}
		
		//Add @cpw.mods.fml.common.Mod.PreInit to preinit(FMLPreInitializationEvent)
		if(ForgeVersion.getMajorVersion() <= 7)
		{
			System.out.println("Replacing Annotation @Mod.EventHandler with @Mod.PreInit from DpiFixModLegacy#preinit");
			MethodNode m = getMethodNode(classNode, "preinit", "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V");
			m.visibleAnnotations.remove(getAnnotation(m, "Lcpw/mods/fml/common/Mod$EventHandler;"));
			AnnotationNode preinit = new AnnotationNode("Lcpw/mods/fml/common/Mod$PreInit;");
			m.visibleAnnotations.add(preinit);
		}
	}
	
	public static MethodNode getMethodNode(ClassNode classNode, String method_name, String method_desc) 
	{
		for (Object method_ : classNode.methods)
		{
			MethodNode method = (MethodNode) method_;
			if (method.name.equals(method_name) && method.desc.equals(method_desc))
			{
				return method;
			}
		}
		return null;
	}
	
	public static AnnotationNode getAnnotation(ClassNode classNode, String... descs)
	{
		return getAnnotation(classNode.visibleAnnotations, descs);
	}
	
	public static AnnotationNode getAnnotation(MethodNode methodNode, String... descs)
	{
		return getAnnotation(methodNode.visibleAnnotations, descs);
	}
	
	private static AnnotationNode getAnnotation(List<AnnotationNode> visibleAnnotations, String... descs) 
	{
		for(AnnotationNode a : visibleAnnotations)
			for(String desc : descs)
				if(a.desc.equals(desc))
					return a;
		return null;
	}

	/**
	 * dumps a file from memory
	 * @throws IOException 
	 */
	public static void dumpFile(String name, byte[] bytes) throws IOException  
	{
    	name = name.replace('.', '/');
    	File f = new File(System.getProperty("user.dir") + "/asm/dumps/dpi-fix/" + name + ".class");
    	f.getParentFile().mkdirs();
    	InputStream in = null;
    	OutputStream out = null;
    	try
    	{
    		in = new ByteArrayInputStream(bytes);
    		out = new FileOutputStream(f);
    		DpiFix.copy(in, out);
    	}
    	catch(Throwable e)
    	{
    		e.printStackTrace();
    	}
    	finally
    	{
    		DpiFix.closeQuietly(in);
    		DpiFix.closeQuietly(out);
    	}
	}

}
