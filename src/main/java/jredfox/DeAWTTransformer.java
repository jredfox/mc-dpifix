package jredfox;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

import org.ow2.asm.ClassWriter;
import org.ow2.asm.Opcodes;
import org.ow2.asm.tree.AbstractInsnNode;
import org.ow2.asm.tree.ClassNode;
import org.ow2.asm.tree.FieldInsnNode;
import org.ow2.asm.tree.FieldNode;
import org.ow2.asm.tree.FrameNode;
import org.ow2.asm.tree.InsnList;
import org.ow2.asm.tree.InsnNode;
import org.ow2.asm.tree.IntInsnNode;
import org.ow2.asm.tree.JumpInsnNode;
import org.ow2.asm.tree.LabelNode;
import org.ow2.asm.tree.MethodInsnNode;
import org.ow2.asm.tree.MethodNode;
import org.ow2.asm.tree.TypeInsnNode;
import org.ow2.asm.tree.VarInsnNode;

import jml.gamemodelib.GameModeLib;
import jml.gamemodelib.GameModeLibAgent;

/**
 * Temporarily Disables all Frame#setVisible, Canvas#setVisible, Applet#setVisible To Prevent Flashy frames Until Minecraft#startGame gets called
 * @author jredfox
 * @credit MoreStack for suggesting editing {@link java.awt.Component#setVisible(boolean)} directly instead of whitelisting 50+ launcher classes
 */
public class DeAWTTransformer implements ClassFileTransformer {
	
	public static File component = new File(System.getProperty("user.dir"), "asm/cache/dpi-fix/java/awt/Component.class").getAbsoluteFile();
	public static File technicFile = new File(System.getProperty("user.dir"), "asm/cache/dpi-fix/technic/Frame.class").getAbsoluteFile();
	public static boolean technic = Boolean.parseBoolean(System.getProperty("gamemodelib.technic", "false"));
	
	public static void init(Instrumentation inst)
	{
		if(GameModeLib.hasForge && net.minecraftforge.common.ForgeVersion.getMajorVersion() < 8 && isInCoreMods() && isDeAWT())
		{
			System.setProperty("gamemodelib.deawt", "true");
			component.delete();//delete previous caches
			technicFile.delete();//delete previous caches
			System.out.println("Registering Agent DeAWTTransformer");
			inst.addTransformer(new DeAWTTransformer());
			GameModeLib.forName("java.awt.Component");//Force Load the java.awt.Frame Class
			if(technic)
				GameModeLib.forName("net.technicpack.legacywrapper.Frame");//Force Load Technic's Frame Class so we can edit it
		}
	}
	
	/**
	 * Verify the jar is inside the coremods jar before registering DeAWTTransformer
	 */
	public static boolean isInCoreMods()
	{
		return GameModeLib.jarFile.getParentFile().equals(new File("coremods").getAbsoluteFile());
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
		
		className = className.replace(".", "/");
		if(className.equals("java/awt/Component"))
		{
			try
			{
				System.out.println("Transforming " + className.replace("/", ".") + " to prevent flashes");
				
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
		else if(technic && className.equals("net/technicpack/legacywrapper/Frame"))
		{
			try
			{
				System.out.println("Transforming " + className.replace("/", ".") + " to be compatible with De-AWT");
				
				//Return the cached file if it exists
				if(technicFile.exists())
					return toByteArray(technicFile);
				
				ClassNode classNode = CoreUtils.getClassNode(classBytes);
				
				//Dynamically Find runGame based on Launcher#start call
				MethodNode runGame = null;
				FieldInsnNode mcFieldInsn = null;
				if(runGame == null)
				{
					MethodInsnNode startInsn = new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/Launcher", "start", "()V");
					for(MethodNode m : classNode.methods)
					{
						AbstractInsnNode ab = m.instructions.getFirst();
						while(ab != null)
						{
							if(ab instanceof MethodInsnNode && CoreUtils.equals(startInsn, (MethodInsnNode) ab))
							{
								runGame = m;
								mcFieldInsn = CoreUtils.previousFieldInsnNode(ab);
								break;
							}
							ab = ab.getNext();
						}
						if(runGame != null)
							break;
					}
					if(runGame == null)
					{
						System.err.println("Could Not Find net/technicpack/legacywrapper/Frame#runGame Technic Support is not possible :(");
						return classBytes;
					}
				}

				//Replace all calls of this.setDefaultCloseOperation(JFrame#EXIT_ON_CLOSE); with this.setDefaultCloseOperation(JFrame#HIDE_ON_CLOSE);
				//Correct Default Resolution 854x480
				//Remove  Additional this.pack(); calls which can cause invisible JFrame on linux
				//Removes Additional this.minecraft.setSize() && this.minecraft.setPreferredSize() calls due to race condition bug which causes issues if left in
				MethodInsnNode closeInsn = new MethodInsnNode(Opcodes.INVOKEVIRTUAL, className, "setDefaultCloseOperation", "(I)V");
				MethodInsnNode packInsn =  new MethodInsnNode(Opcodes.INVOKEVIRTUAL, className, "pack", "()V", false);
				MethodInsnNode setSizeInsn = new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/Launcher", "setSize", "(II)V", false);
				MethodInsnNode setPrefSizeInsn = new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/Launcher", "setPreferredSize", "(Ljava/awt/Dimension;)V", false);
				for(MethodNode m : classNode.methods)
				{
					AbstractInsnNode ab = m.instructions.getFirst();
					boolean flag = m.name.equals("<init>");
					boolean flag_rg = m.name.equals(runGame.name);
					int countRes = 0;
					while(ab != null)
					{
						//Patch Default resolution to match Minecraft's Default Resolution 854x480
						if(flag)
						{
							if(ab instanceof IntInsnNode && ab.getOpcode() == Opcodes.SIPUSH && ab.getNext() != null && ab.getNext().getOpcode() == Opcodes.PUTFIELD)
							{
								IntInsnNode insnInt = (IntInsnNode) ab;
								String name = ((FieldInsnNode) ab.getNext()).name.toLowerCase();
								boolean width = name.contains("len") || name.contains("wi");
								if(insnInt.operand > 400 && (width || name.contains("height")))
								{
									insnInt.operand = width ? 854 : 480;
									countRes++;
									if(countRes >= 2)
										flag = false;//Only patch first two PUTFIELD instructions that are likely resolution
								}
							}
						}
						if(flag || flag_rg)
						{
							if(ab instanceof MethodInsnNode && CoreUtils.equals(packInsn, (MethodInsnNode) ab))
							{
								AbstractInsnNode prev = ab.getPrevious();
								InsnList l2 = new InsnList();
								l2.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, className, "toString", "()Ljava/lang/String;"));
								l2.add(new InsnNode(Opcodes.POP));
								m.instructions.insert(ab, l2);
								m.instructions.remove(ab);
								ab = prev;
							}
							if(flag_rg)
							{
								if(ab instanceof MethodInsnNode)
								{
									MethodInsnNode mInsn = (MethodInsnNode) ab;
									if(CoreUtils.equals(setPrefSizeInsn, (MethodInsnNode)ab) || CoreUtils.equals(setSizeInsn, (MethodInsnNode)ab))
									{
										ab = CoreUtils.deleteLine(m, ab);
									}
								}
							}
						}
						
						//this.setDefaultCloseOperation(JFrame#HIDE_ON_CLOSE);
						if(ab instanceof MethodInsnNode && CoreUtils.equals(closeInsn, (MethodInsnNode) ab))
						{
							m.instructions.remove(ab.getPrevious());
							m.instructions.insertBefore(ab, new InsnNode(Opcodes.ICONST_1));
						}
						
						ab = ab.getNext();
					}
				}
				
				//this.minecraft.setSize(this.getWidth(), this.getHeight());
				//this.minecraft.setPreferredSize(new Dimension(this.getWidth(), this.getHeight()));
				//this.pack();
				InsnList l = new InsnList();
				
				l.add(new VarInsnNode(Opcodes.ALOAD, 0));
				l.add(CoreUtils.copy(mcFieldInsn));
				l.add(new VarInsnNode(Opcodes.ALOAD, 0));
				l.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, className, "getWidth", "()I", false));
				l.add(new VarInsnNode(Opcodes.ALOAD, 0));
				l.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, className, "getHeight", "()I", false));
				l.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/Launcher", "setSize", "(II)V", false));
				l.add(new LabelNode());
				
				l.add(new VarInsnNode(Opcodes.ALOAD, 0));
				l.add(CoreUtils.copy(mcFieldInsn));
				l.add(new TypeInsnNode(Opcodes.NEW, "java/awt/Dimension"));
				l.add(new InsnNode(Opcodes.DUP));
				l.add(new VarInsnNode(Opcodes.ALOAD, 0));
				l.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, className, "getWidth", "()I", false));
				l.add(new VarInsnNode(Opcodes.ALOAD, 0));
				l.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, className, "getHeight", "()I", false));
				l.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/awt/Dimension", "<init>", "(II)V", false));
				l.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/Launcher", "setPreferredSize", "(Ljava/awt/Dimension;)V", false));
				l.add(new LabelNode());
				
				l.add(new VarInsnNode(Opcodes.ALOAD, 0));
				l.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, className, "pack", "()V", false));
				l.add(new LabelNode());
				runGame.instructions.insert(CoreUtils.prevLabelNode(CoreUtils.getMethodInsnNode(runGame, Opcodes.INVOKEVIRTUAL, "net/minecraft/Launcher", "init", "()V", false)), l);
				
				byte[] clazzBytes = CoreUtils.toByteArray(CoreUtils.getClassWriter(classNode, ClassWriter.COMPUTE_MAXS), className);
				CoreUtils.toFile(clazzBytes, technicFile);
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
