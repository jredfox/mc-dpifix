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

import jredfox.clfix.LaunchClassLoaderFix;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraftforge.common.ForgeVersion;

public class DpiFixAnnotation implements IClassTransformer, cpw.mods.fml.relauncher.IClassTransformer {
	
	public DpiFixAnnotation()
	{
		LaunchClassLoaderFix.stopMemoryOverflow(null);
	}
	
	@Override
	public byte[] transform(String name, String transformedName, byte[] basicClass) 
	{
		if(transformedName.equals("jredfox.dpimod.DpiFixModLegacy"))
		{
			try
			{
				ClassNode classNode = CoreUtils.getClassNode(basicClass);
	            DpiFixAnnotation.patchAtMod(classNode);
	            ClassWriter cw = CoreUtils.getClassWriter(classNode, ClassWriter.COMPUTE_MAXS);
	            return CoreUtils.toByteArray(cw, transformedName);
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
			AnnotationNode atmod = CoreUtils.getAnnotation(classNode, "Lcpw/mods/fml/common/Mod;");
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
		
		//Add @cpw.mods.fml.common.Mod.PreInit to preinit(FMLPreInitializationEvent e)
		if(ForgeVersion.getMajorVersion() <= 7)
		{
			System.out.println("Replacing Annotation @Mod.EventHandler with @Mod.PreInit from DpiFixModLegacy#preinit");
			MethodNode m = CoreUtils.getMethodNode(classNode, "preinit", "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V");
			m.visibleAnnotations.remove(CoreUtils.getAnnotation(m, "Lcpw/mods/fml/common/Mod$EventHandler;"));
			AnnotationNode preinit = new AnnotationNode("Lcpw/mods/fml/common/Mod$PreInit;");
			m.visibleAnnotations.add(preinit);
		}
	}

}
