package jredfox;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.ow2.asm.ClassWriter;
import org.ow2.asm.tree.AnnotationNode;
import org.ow2.asm.tree.ClassNode;
import org.ow2.asm.tree.MethodNode;

import jredfox.clfix.LaunchClassLoaderFix;
import jredfox.forgeversion.ForgeVersionProxy;

public class DpiFixAnn implements net.minecraft.launchwrapper.IClassTransformer {
	
	public DpiFixAnn()
	{
		LaunchClassLoaderFix.stopMemoryOverflow(this.getClass().getClassLoader());
		this.failsafe();
	}
	
	/**
	 * Re-Enables JFrame GUI if we are not on the client and are 1.5.2 or below
	 */
	public void failsafe() 
	{
		if(ForgeVersionProxy.onefive && !DeAWTProxy.getVisible())
		{
			System.out.println("Re-Enabling JFrame GUIs SERVER SIDE");
			DeAWTProxy.setVisible(true);
		}
	}

	@Override
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
		if(ForgeVersionProxy.onesix)
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
		//Add @cpw.mods.fml.common.Mod.ServerAboutToStart to modloadcomplete(FMLServerAboutToStartEvent e)
		if(ForgeVersionProxy.onefive)
		{
			//Removes @Mod$EventHandler
			for(MethodNode m : classNode.methods)
			{
				List<AnnotationNode> arr = m.visibleAnnotations;
				if(arr == null || arr.isEmpty())
					continue;
				Iterator<AnnotationNode> it = arr.iterator();
				while(it.hasNext())
					if(it.next().desc.equals("Lcpw/mods/fml/common/Mod$EventHandler;"))
						it.remove();
			}
			
			System.out.println("Adding Annotation @Mod.PreInit for DpiFixModLegacy#preinit");
			MethodNode m1 = CoreUtils.getMethodNode(classNode, "preinit", "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V");
			m1.visibleAnnotations.add(new AnnotationNode("Lcpw/mods/fml/common/Mod$PreInit;"));
			
			System.out.println("Adding Annotation @Mod.ServerAboutToStart for DpiFixModLegacy#modloadcomplete");
			MethodNode m2 = CoreUtils.getMethodNode(classNode, "modloadcomplete", "(Lcpw/mods/fml/common/event/FMLServerAboutToStartEvent;)V");
			m2.visibleAnnotations.add(new AnnotationNode("Lcpw/mods/fml/common/Mod$ServerAboutToStart;"));
		}
	}
	
//	private void makeDeAWTProxy(String name, byte[] basicClass)
//	{
//		if(name.equals("jredfox.DeAWTProxy"))
//		{
//			ClassNode classNode = CoreUtils.getClassNode(basicClass);
//			for(MethodNode m : classNode.methods)
//			{
//				for(AbstractInsnNode a : m.instructions.toArray())
//				{
//					if(a instanceof FieldInsnNode)
//					{
//						FieldInsnNode f = (FieldInsnNode)a;
//						if(f.owner.equals("jredfox/DeAWTProxy") && !f.name.equals("hasField"))
//						{
//							f.owner = "java/awt/Component";
//						}
//					}
//				}
//			}
//			ClassWriter cw = CoreUtils.getClassWriter(classNode, ClassWriter.COMPUTE_MAXS);
//			try {
//				CoreUtils.dumpFile(name + "_make", cw.toByteArray());
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
//		}
//	}

}
