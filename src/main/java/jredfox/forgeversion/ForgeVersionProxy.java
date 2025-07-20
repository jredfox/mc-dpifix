package jredfox.forgeversion;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Safely Get the Forge Version 1.3.2 - 1.12.2 without loading the ModContainer class.
 * Class Is Portable, Free to Use, Copy, Re-Distribute and Modify for your own project.
 * If you modify this and it's not a bug fix please refactor for your own mods to prevent class collisions
 * @report bugs to github.com/jredfox/mc-dpifix/issues
 * @compiling needs SideOnly Side classes from both 1.4.5 and 1.5.2 to compile. Do not include inside SideOnly or Side.CLIENT inside your mod's jar. Detection is automatic and won't trigger incorrect classloading
 * @author jredfox
 */
public class ForgeVersionProxy {
	
    //This number is incremented every time we remove deprecated code/major API changes, never reset
    public static int majorVersion;
    //This number is incremented every minecraft release, never reset
    public static int minorVersion;
    //This number is incremented every time a interface changes or new major feature is added, and reset every Minecraft version
    public static int revisionVersion;
    //This number is incremented every time Jenkins builds Forge, and never reset. Should always be 0 in the repo code.
    public static int buildVersion;
    // This is the minecraft version we're building for - used in various places in Forge/FML code
    public static String mcVersion;
    /**
     * If Forge build is < 11.14.3.1503 (MC 1.8) mcpVersion will be null
     */
    public static String mcpVersion;
    /**
     * When false forge build numbers will be 0 {@link #majorVersion}, {@link #minorVersion}, {@link #revisionVersion}, {@link #buildVersion}
     * {@link #mcVersion} will be "1.2.5" and the Extension booleans will be the best guess
     * such as {@link #notchNames} will be true {@link #isObf} will be true and {@link #isClient} will be determined based on the Existence of the Main class
     */
    public static boolean hasForge;
    /**
     * Use this for ASM to determine if your transformer should use notch names vs SRG names
     */
    public static boolean notchNames;
    /**
     * Are we running on a obfuscated environment
     */
    public static boolean isObf;
    /**
     * Are we running on the client or the server?
     */
    public static boolean isClient;
    /**
     * The ForgeVersionProxy Version
     */
    public static final String PROXY_VERSION = "1.0.0";
	
	static
	{
		init();
	}
	
    public static int getMajorVersion()
    {
        return majorVersion;
    }

    public static int getMinorVersion()
    {
        return minorVersion;
    }

    public static int getRevisionVersion()
    {
        return revisionVersion;
    }

    public static int getBuildVersion()
    {
        return buildVersion;
    }

    public static String getVersion()
    {
        return String.format("%d.%d.%d.%d", majorVersion, minorVersion, revisionVersion, buildVersion);
    }
    
    //____START PROXY ADDITIONAL GETTERS____\\
    
    public static String getMcVersion()
    {
    	return mcVersion;
    }
    
    public static String getMcpVersion()
    {
    	return mcpVersion;
    }
    
    public static boolean getNotchNames()
    {
    	return notchNames;
    }
    
    public static boolean getIsClient()
    {
    	return isClient;
    }
    
    public static boolean getIsObf()
    {
    	return isObf;
    }
    
    public static String getProxyVersion()
    {
    	return PROXY_VERSION;
    }
    
  //____END PROXY ADDITIONAL GETTERS____\\

	public static void init() 
	{
		hasForge = true;
		ClassLoader cl = ForgeVersionProxy.class.getClassLoader();
		ClassNode c;
		try 
		{
			c = getClassNode(cl.getResourceAsStream("net/minecraftforge/common/ForgeVersion.class"));
			if(c == null)
			{
				System.err.println("Unable to Parse ForgeVersion.class via ClassNode! Either Forge isn't installed or your MC Version is 1.2.5 or older which isn't supported!");
				hasForge = false;
				mcVersion = "1.2.5";
				notchNames = true;
				isObf = true;
				isClient = cl.getSystemClassLoader().getResource("net/minecraft/client/Minecraft.class") != null || cl.getSystemClassLoader().getResource("net/minecraft/client/main/Main.class") != null;
				return;
			}
			
			boolean modified = false;
			for(Object of : c.fields)
			{
				FieldNode f = (FieldNode) of;
				String n = f.name;
				try
				{
					if(n.equals("majorVersion"))
						majorVersion = ((Number)f.value).intValue();
					else if(n.equals("minorVersion"))
						minorVersion = ((Number)f.value).intValue();
					else if(n.equals("revisionVersion"))
						revisionVersion = ((Number)f.value).intValue();
					else if(n.equals("buildVersion"))
						buildVersion = ((Number)f.value).intValue();
					else if(n.equals("mcVersion"))
						mcVersion = f.value.toString();
					else if(n.equals("mcpVersion"))
						mcpVersion = f.value.toString();
				}
				catch(NullPointerException e)
				{
					System.err.println("Field " + f.name + " is no longer final!");
					modified = true;
				}
				catch(Exception ec)
				{
					System.err.println("Field " + f.name + " is no longer a Number or Primative! Class:" + (f.value != null ? f.value.getClass().getName() : "null"));
					modified = true;
				}
			}
			
			//Handle ForgeVersion whan the class has been AT (Access Transformed) or modified and are no longer final fields
			if(modified)
			{
				MethodNode m = getMethodNode(c, "<clinit>", "()V");
				for(AbstractInsnNode a : m.instructions.toArray())
				{
					if(a.getOpcode() == Opcodes.PUTSTATIC && a instanceof FieldInsnNode)
					{
						FieldInsnNode insn = (FieldInsnNode) a;
						if(insn.owner.equals("net/minecraftforge/common/ForgeVersion"))
						{
							String n = insn.name;
							if(n.equals("majorVersion"))
								majorVersion = getIntFromInsn(insn.getPrevious());
							else if(n.equals("minorVersion"))
								minorVersion = getIntFromInsn(insn.getPrevious());
							else if(n.equals("revisionVersion"))
								revisionVersion = getIntFromInsn(insn.getPrevious());
							else if(n.equals("buildVersion"))
								buildVersion = getIntFromInsn(insn.getPrevious());
							else if(n.equals("mcVersion"))
								mcVersion = getStringFromInsn(insn.getPrevious());
							else if(n.equals("mcpVersion"))
								mcpVersion = getStringFromInsn(insn.getPrevious());
						}
					}
				}
			}
		}
		catch (Throwable e)
		{
			e.printStackTrace();
		}
		
		initMcVersion();
		notchNames = majorVersion < 9 || majorVersion == 9 && minorVersion <= 11 && buildVersion < 937;
		isObf = (majorVersion < 7 && buildVersion < 448) ? (cl.getResource("net/minecraft/src/World.class") == null && cl.getResource("net/minecraft/world/World.class") == null) : (cl.getResource("net/minecraft/world/World.class") == null);
		isClient = majorVersion < 8 ? legacyIsClient() : cl.getSystemClassLoader().getResource("net/minecraft/client/main/Main.class") != null;
	}
	
	private static boolean legacyIsClient()
	{
		try
		{
			//1.4.6 - 1.5.2
			if(majorVersion > 6 || majorVersion == 6 && buildVersion >= 451)
				SideCheckOF.checkClient();
			//1.3.2 - 1.4.5
			else
				SideCheckLegacy.checkClient();
			return true;
		}
		catch(Throwable t)
		{
			return false;
		}
	}

	public static class SideCheckOF
	{
		@cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
		public static void checkClient()
		{
			
		}
		
		@cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.SERVER)
		public static void checkServer()
		{
			
		}
	}
	
	public static class SideCheckLegacy
	{
		@cpw.mods.fml.common.asm.SideOnly(cpw.mods.fml.common.Side.CLIENT)
		public static void checkClient()
		{
			
		}
		
		@cpw.mods.fml.common.asm.SideOnly(cpw.mods.fml.common.Side.SERVER)
		public static void checkServer()
		{
			
		}
	}

	public static void initMcVersion()
	{
		if(mcVersion != null)
			return;
		
		switch(majorVersion)
		{
			case 1:
				if(buildVersion < 30)
					mcVersion = "1.1";
				else
					mcVersion = "1.2.3";
			break;
			
			case 2:
				mcVersion = "1.2.4";
			break;
			
			case 3:
				mcVersion = "1.2.5";
			break;
			
			case 4:
				mcVersion = "1.3.2";
			break;
			
			case 5:
				mcVersion = "1.4";
			break;
			
			case 6:
				if(buildVersion <= 329)
					mcVersion = "1.4.1";
				else if(buildVersion <= 355)
					mcVersion = "1.4.2";
				else if(buildVersion <= 358)
					mcVersion = "1.4.3";
				else if(buildVersion <= 378)
					mcVersion = "1.4.4";
				else if(buildVersion <= 448)
					mcVersion = "1.4.5";
				else if(buildVersion <= 489)
					mcVersion = "1.4.6";
				else
					mcVersion = "1.4.7";
			break;
			
			case 7:
				if(buildVersion <= 598)
					mcVersion = "1.5";
				else if(buildVersion <= 682)
					mcVersion = "1.5.1";
				else
					mcVersion = "1.5.2";
			break;
			
			case 8:
				mcVersion = "1.6.1";
			break;
			
			case 9:
				if(buildVersion <= 871)
					mcVersion = "1.6.2";
				else if(minorVersion <= 11 && buildVersion <= 878)
					mcVersion = "1.6.3";
				else
					mcVersion = "1.6.4";
			break;
			
			case 10:
				if(minorVersion <= 12)
					mcVersion = "1.7.2";
				else
					mcVersion = "1.7.10";
			break;
			
			case 11:
				mcVersion = "1.8";
			break;
		
			default:
				break;
		}
	}

	public static void load() {}
	
	public static int getIntFromInsn(AbstractInsnNode spot)
	{
		if(spot instanceof InsnNode)
		{
			int opcode = ((InsnNode)spot).getOpcode();
			switch(opcode)
			{
				case Opcodes.ICONST_M1:
					return -1;
				case Opcodes.ICONST_0:
					return 0;		
				case Opcodes.ICONST_1:
					return 1;
				case Opcodes.ICONST_2:
					return 2;
				case Opcodes.ICONST_3:
					return 3;
				case Opcodes.ICONST_4:
					return 4;
				case Opcodes.ICONST_5:
					return 5;
				case Opcodes.LCONST_0:
					return 0;
				case Opcodes.LCONST_1:
					return 1;
				case Opcodes.FCONST_0:
					return 0;
				case Opcodes.FCONST_1:
					return 1;
				case Opcodes.FCONST_2:
					return 2;
				case Opcodes.DCONST_0:
					return 0;
				case Opcodes.DCONST_1:
					return 1;
				default:
					return 0;
			}
		}
		else if(spot instanceof IntInsnNode)
			return ((IntInsnNode)spot).operand;
		else if(spot instanceof LdcInsnNode)
		{
			Object o = ((LdcInsnNode)spot).cst;
			return o instanceof Number ? ( ((Number)o).intValue() ) : ( parseInt(o.toString()) );
		}
		return 0;
	}
	
	/**
	 * Parse a Int Safely
	 */
	public static int parseInt(String value) 
	{
		if(value == null) return 0;
		
		try
		{
			return (int) Long.parseLong(value.trim(), 10);
		}
		catch(NumberFormatException e)
		{
			return 0;
		}
	}

	public static String getStringFromInsn(AbstractInsnNode spot) 
	{
		return spot instanceof LdcInsnNode ? ((LdcInsnNode)spot).cst.toString() : null;
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
	
	public static ClassNode getClassNode(InputStream in) 
	{
		if(in == null)
			return null;
		
		try
		{
			byte[] basicClass = toByteArray(in);
			ClassNode classNode = new ClassNode();
	        ClassReader classReader = new ClassReader(basicClass);
	        classReader.accept(classNode, 0);
	        return classNode;
		}
		catch(Throwable t)
		{
			t.printStackTrace();
		}
		finally
		{
			if(in != null)
			{
				try
				{
					in.close();
				}
				catch(Throwable t)
				{
					t.printStackTrace();
				}
			}
		}
		return null;
	}
	
	/**
	 * Converts the InputStream into byte[] then closes the InputStream
	 * @throws IOException 
	 */
    public static byte[] toByteArray(final InputStream input) throws IOException
    {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        copy(input, output);
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

}
