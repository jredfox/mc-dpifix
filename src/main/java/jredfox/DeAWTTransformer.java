package jredfox;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

import org.apache.commons.io.FileUtils;
import org.ow2.asm.ClassWriter;
import org.ow2.asm.Opcodes;
import org.ow2.asm.tree.ClassNode;
import org.ow2.asm.tree.FieldInsnNode;
import org.ow2.asm.tree.FieldNode;
import org.ow2.asm.tree.FrameNode;
import org.ow2.asm.tree.InsnList;
import org.ow2.asm.tree.InsnNode;
import org.ow2.asm.tree.JumpInsnNode;
import org.ow2.asm.tree.LabelNode;
import org.ow2.asm.tree.MethodNode;
import org.ow2.asm.tree.TypeInsnNode;
import org.ow2.asm.tree.VarInsnNode;

import jml.gamemodelib.GameModeLibAgent;

/**
 * Temporarily Disables all java.awt.* To Prevent Flashy frames Until Minecraft#startGame gets called
 * @author jredfox
 * @credit MoreStack for suggesting editing {@link java.awt.Component#setVisible(boolean)} directly instead of whitelisting 50 classes
 */
public class DeAWTTransformer implements ClassFileTransformer {
	
	public static File component = new File(System.getProperty("user.dir"), "asm/cache/dpi-fix/java/awt/Component.class").getAbsoluteFile();
	public static boolean technic = Boolean.parseBoolean(System.getProperty("gamemodelib.technic", "false"));
	
	public static void init(Instrumentation inst)
	{
		if(GameModeLibAgent.hasForge && net.minecraftforge.common.ForgeVersion.getMajorVersion() < 8 && isInCoreMods() && isDeAWT())
		{
			System.out.println("Registering Agent DeAWTTransformer");
			component.delete();//delete previous caches
			inst.addTransformer(new DeAWTTransformer());
			GameModeLibAgent.forName("java.awt.Component");//Force Load the java.awt.Frame Class
		}
	}
	
	/**
	 * Verify the jar is inside the coremods jar before registering DeAWTTransformer
	 */
	public static boolean isInCoreMods()
	{
		return GameModeLibAgent.jarFile.getParentFile().equals(new File("coremods").getAbsoluteFile());
	}
	
	public static boolean isDeAWT()
	{
		PropertyConfig cfg = new PropertyConfig(new File("config", "DpiFix.cfg"));
		cfg.load();
		technic = cfg.get("Coremod.OneFive.DeAWT.Compat.Technic");
		System.setProperty("gamemodelib.technic", String.valueOf(technic) );
		String os = DpiFix.isWindows ? "Windows" : DpiFix.isMacOs ? "Mac" : "Linux";
		return cfg.get("CoreMod.Enabled") && cfg.get("Coremod.OneFive.DeAWT." + os, false);
	}
	
	@Override
	public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classBytes) 
	{
		if(className == null)
			return classBytes;
		
		if(className.equals("java/awt/Component"))
		{
			try
			{
				System.out.println("Transforming java.awt.Component to prevent flashes");
				
				//Return the cached file if it exists
				if(component.exists())
					return toByteArray(component);
				
				ClassNode classNode = CoreUtils.getClassNode(classBytes);
				//add the field canSetVisible
				classNode.fields.add(new FieldNode(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "canSetVisible", "Z", null, null));
				
				//transform setVisible
				MethodNode m = CoreUtils.getMethodNode(classNode, "setVisible", "(Z)V");
				deawt(m, false);
				
				//transform show
				MethodNode show = CoreUtils.getMethodNode(classNode, "show", "()V");
				if(show != null)
					deawt(show, true);
				
				byte[] clazzBytes = CoreUtils.toByteArray(CoreUtils.getClassWriter(classNode, ClassWriter.COMPUTE_MAXS), className);
				CoreUtils.toFile(clazzBytes, component);
				return clazzBytes;
			}
			catch(Throwable t)
			{
				t.printStackTrace();
			}
		}
		
		return classBytes;
	}

	private void deawt(MethodNode m, boolean isShow) 
	{
		InsnList l = new InsnList();
		//Component#setVisible if(b && !canSetVisible && (this instanceof java.awt.Frame || this instanceof Canvas || this instanceof Applet)) return;
		//Component#show       if(!canSetVisible && (this instanceof java.awt.Frame || this instanceof Canvas || this instanceof Applet)) return;
		LabelNode l0 = new LabelNode();
		l.add(l0);
		LabelNode l1 = new LabelNode();
		//prepends b &&
		if(!isShow)
		{
			l.add(new VarInsnNode(Opcodes.ILOAD, 1));
			l.add(new JumpInsnNode(Opcodes.IFEQ, l1));
		}
		l.add(new FieldInsnNode(Opcodes.GETSTATIC, "java/awt/Component", "canSetVisible", "Z"));
		l.add(new JumpInsnNode(Opcodes.IFNE, l1));
		l.add(new VarInsnNode(Opcodes.ALOAD, 0));
		l.add(new TypeInsnNode(Opcodes.INSTANCEOF, "java/awt/Frame"));
		LabelNode l2 = new LabelNode();
		l.add(new JumpInsnNode(Opcodes.IFNE, l2));
		l.add(new VarInsnNode(Opcodes.ALOAD, 0));
		l.add(new TypeInsnNode(Opcodes.INSTANCEOF, "java/awt/Canvas"));
		l.add(new JumpInsnNode(Opcodes.IFNE, l2));
		l.add(new VarInsnNode(Opcodes.ALOAD, 0));
		l.add(new TypeInsnNode(Opcodes.INSTANCEOF, "java/applet/Applet"));
		l.add(new JumpInsnNode(Opcodes.IFEQ, l1));
		l.add(l2);
		l.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
		l.add(new InsnNode(Opcodes.RETURN));
		l.add(l1);
		l.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
		m.instructions.insert(l);
	}
	
    public static byte[] toByteArray(File file)
    {
    	InputStream input = null;
        ByteArrayOutputStream output = null;
        try
        {
        	input = new FileInputStream(file);
        	output = new ByteArrayOutputStream();
        	copy(input, output);
        }
        catch(Throwable e)
        {
        	e.printStackTrace();
        }
        finally
        {
            closeQuietly(input);
            closeQuietly(output);
        }
        return output.toByteArray();
    }
    
	public static void copy(InputStream in, OutputStream out) throws IOException
	{
		byte[] buffer = new byte[1048576/2];
		int length;
   	 	while ((length = in.read(buffer)) >= 0)
		{
			out.write(buffer, 0, length);
		}
	}
	
	public static void closeQuietly(Closeable clos)
	{
		try 
		{
			if(clos != null)
				clos.close();
		}
		catch (IOException e)
		{
			
		}
	}

}