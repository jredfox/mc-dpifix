package jredfox;

import java.util.ArrayList;

import org.ow2.asm.ClassWriter;
import org.ow2.asm.tree.AnnotationNode;
import org.ow2.asm.tree.ClassNode;
import org.ow2.asm.tree.MethodNode;

import jredfox.clfix.LaunchClassLoaderFix;
import net.minecraftforge.common.ForgeVersion;

public class DpiFixAnn implements net.minecraft.launchwrapper.IClassTransformer {
	
	public DpiFixAnn()
	{
		LaunchClassLoaderFix.stopMemoryOverflow(null);
	}
	
	public byte[] transform(String name, String transformedName, byte[] basicClass) 
	{
		if(transformedName.equals("jredfox.dpimod.DpiFixModLegacy") && basicClass != null)
		{
			try
			{
				ClassNode classNode = CoreUtils.getClassNode(basicClass);
				DpiFixAnn.patchAtMod(classNode);
	            ClassWriter cw = CoreUtils.getClassWriter(classNode, ClassWriter.COMPUTE_MAXS);
	            return CoreUtils.toByteArray(cw, transformedName);
			}
			catch(Throwable t)
			{
				CoreUtils.dumpFileErr(transformedName, basicClass);
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
