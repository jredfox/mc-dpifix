package jredfox.forgeversion;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.ow2.asm.ClassReader;
import org.ow2.asm.Opcodes;
import org.ow2.asm.tree.AbstractInsnNode;
import org.ow2.asm.tree.ClassNode;
import org.ow2.asm.tree.FieldInsnNode;
import org.ow2.asm.tree.FieldNode;
import org.ow2.asm.tree.InsnNode;
import org.ow2.asm.tree.IntInsnNode;
import org.ow2.asm.tree.LdcInsnNode;
import org.ow2.asm.tree.MethodNode;

/**
 * Safely Get the Forge Version 1.1 - 1.12.2 without loading the ModContainer class.
 * Class Is Portable, Free to Use, Copy, Re-Distribute and Modify for your own project.
 * If you modify this and it's not a bug fix please refactor for your own mods to prevent class collisions
 * @report 
 * 	bugs to github.com/jredfox/mc-dpifix/issues
 * @compiling 
 * Needs These classes below as a proxy to compile. Do not include them into your compiled jar! 
 * Doing so may cause crashes between versions due to class collisions! 
 * Detection is automatic and won't trigger loading any classes for the wrong mc version
 * -	net.minecraftforge.fml.relauncher.FMLLaunchHandler (1.12.2)
 * -	cpw.mods.fml.relauncher.FMLLaunchHandler (1.7.10)
 * -	cpw.mods.fml.relauncher.FMLRelauncher (1.5.2)
 * -	cpw.mods.fml.relauncher.Side (1.7.10)
 * -	net.minecraftforge.fml.relauncher.Side (1.12.2)
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
     * {@link #mcVersion} will be "1.0.0" and the Extension booleans will be the best guess
     * such as {@link #notchNames} will be true {@link #isObf} will be true and {@link #isClient} will be determined based on the Existence of the Main class and LWJGL
     */
    public static boolean hasForge;
    /**
     * True when {@link #hasForge} and forge has ASM CoreMod Capabilities. Use this instead of {@link #hasForge} if you compiled with {@link #OLD_LEGACY_SUPP} with true
     */
    public static boolean hasForgeASM;
    /**
     * Use this for ASM to determine if your transformer should use notch names vs SRG names
     */
    public static boolean notchNames;
    /**
     * Are we running on a obfuscated environment
     */
    public static boolean isObf;
    /**
     * Are we most likely running on the client (Main Client Class & LWJGL library exists)
     * Use {@link #getIsClient()} for Your ASM Plugin this is for the java agent and isn't guaranteed to be correct
     */
    public static boolean isClientAgent;
    /**
     * Are we running on the client or the server? DO NOT USE IN YOUR JAVA AGENT it will be false and cause java.awt.Component to load!
     */
    private static Boolean isClient;
    /**
     * Are We running on 1.5x or below
     */
    public static boolean onefive;
	/**
	 * Are We running on 1.6x or below
	 */
	public static boolean onesix;
	/**
	 * Are We running on 1.7x or above
	 */
	public static boolean onesevenPlus;
    /**
     * When Compiled with true ForgeVersionProxy Supports Forge for MC 1.1 - 1.2.5!
     * Set this to false when compiling if you don't want or need that support
     */
    public static final boolean OLD_LEGACY_SUPP = false;
    /**
     * The ForgeVersionProxy Version
     * ChangeLog 1.0.1
     * - Fixed   isClient returning true for servers when java agent was present and the main(String[]) args had already started
     * - Added   isClientAgent for use during a java agent as {@link #getIsClient()} will return false and print a RuntimeException during a java agent notifying the coder of their error
     * - Added   hasForgeASM to determine if Forge has CoreMod Capabilities reliable even in java agent
     * - Added   Support for Forge MC 1.1 - 1.2.5 when compiled with {@link #OLD_LEGACY_SUPP} is true
     * - Added   {@link #onesix}
     * - Added   {@link #onesevenPlus}
     * - Changed {@link #mcVersion} to "1.0.0" when {@link #hasForge} is false
     */
    public static final String PROXY_VERSION = "1.0.1";
	
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
    
    public static boolean getHasForge()
    {
    	return hasForge;
    }
    
    public static boolean getHasForgeASM()
    {
    	return hasForgeASM;
    }
    
    public static boolean getNotchNames()
    {
    	return notchNames;
    }
    
    public static boolean getIsObf()
    {
    	return isObf;
    }
    
    /**
     * @return True if the presence of the Client's Main Class exists and LWJGL is present.
     * If you need a guaranteed boolean after the main(String[] args) method has started then use {@link #getIsClient()} instead
     */
    public static boolean getIsClientAgent()
    {
    	return isClientAgent;
    }
    
	/**
	 * @return True if and only if fired after the main(String[] args) method has started and we are a client. 
	 * Use {@link #isClientAgent} for usage during a javaagent which isn't guaranteed 100% of the time if the presence of LWJGL exists on the server side.
	 * This Method Will work inside your CoreMod Plugin's Constructor but not during javaagent's methods. This method will cause java.awt.Component to load and then it cannot be transformed
	 * @note MC 1.1 - MC 1.2.5 Will simply return {@link #isClientAgent}
	 */
    public static boolean getIsClient()
    {
    	if(isClient == null)
    		isClient = sideCheck();
    	return isClient != null ? isClient : false;
    }
    
    public static boolean getOldLegacySupp()
    {
    	return OLD_LEGACY_SUPP;
    }
    
    public static String getProxyVersion()
    {
    	return PROXY_VERSION;
    }
    
  //____END PROXY ADDITIONAL GETTERS____\\

	public static void init() 
	{
		ClassLoader cl = ForgeVersionProxy.class.getClassLoader();
		try 
		{
			ClassNode c = getClassNode(cl.getResourceAsStream("net/minecraftforge/common/ForgeVersion.class"));
			
			//Re-Direct ClassNode to ForgeHooks which had the forge versions for MC 1.1 - 1.2.5
			if(c == null && getOldLegacySupp())
			{
				InputStream legacyIn = cl.getResourceAsStream("forge/ForgeHooks.class");
				c = getClassNode(legacyIn != null ? legacyIn : cl.getResourceAsStream("net/minecraft/src/forge/ForgeHooks.class"));
			}
			
			if(c == null)
			{
				System.err.println("Unable to Parse ForgeVersion.class via ClassNode! Either Forge isn't installed or your MC Version is isn't supported!");
				hasForge = false;
				mcVersion = "1.0.0";
				notchNames = true;
				isObf = true;
				ClassLoader sysCL = cl.getSystemClassLoader();
				boolean client = (sysCL.getResource("net/minecraft/client/Minecraft.class") != null || sysCL.getResource("net/minecraft/client/main/Main.class") != null)
						&& (cl.getResource("org/lwjgl/LWJGLException.class") != null || cl.getResource("org/lwjgl/Version.class") != null);
				isClientAgent = client;
				isClient = client;
				return;
			}
			
			hasForge = true;
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
			
			//Handle ForgeVersion when the class has been AT (Access Transformed) or modified and are no longer final fields
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
		int major = majorVersion;
		int build = buildVersion;
		hasForgeASM = major > 3;
		notchNames = major < 9 || major == 9 && minorVersion <= 11 && build < 937;
		isObf = (major < 7 && build < 448) ? (cl.getResource("net/minecraft/src/World.class") == null && cl.getResource("net/minecraft/world/World.class") == null) : (cl.getResource("net/minecraft/world/World.class") == null);
		onefive = 		  major < 8;
		onesix = 		  major < 10;
		onesevenPlus =    major > 9;
		isClientAgent = (onefive ? (cl.getSystemClassLoader().getResource("net/minecraft/client/Minecraft.class") != null) : (cl.getSystemClassLoader().getResource("net/minecraft/client/main/Main.class") != null))
				&& (cl.getResource("org/lwjgl/LWJGLException.class") != null || cl.getResource("org/lwjgl/Version.class") != null);
	}
	
	/**
	 * Accurately Check 1.3.2 - 1.12.2 For the real boolean of isClient when {@link #hasForge} is true
	 * @return null when Side is null due to firing inside a java agent.
	 * Returns {@value #isClientAgent} for MC 1.1 - 1.2.5
	 */
	private static Boolean sideCheck()
	{
		try
		{
			//Reduce GETFIELD calls
			int major = majorVersion;
			
			//1.8 - 1.12.2
			if(major > 10)
				return SideCheckModern.checkClient();
			//1.6.1 - 1.7.10
			else if(!onefive)
				return SideCheckOld.checkClient();
			//1.3.2 - 1.5.2
			else if(major > 3)
				return SideCheckLegacy.checkClient();
			//1.1 - 1.2.5
			else
				return isClientAgent;
		}
		catch(Throwable t)
		{
			t.printStackTrace();
			return null;
		}
	}
	
	public static class SideCheckModern
	{
		public static Boolean checkClient()
		{
			net.minecraftforge.fml.relauncher.Side side = net.minecraftforge.fml.relauncher.FMLLaunchHandler.side();
			checkSide(side);
			return side == net.minecraftforge.fml.relauncher.Side.CLIENT;
		}
		
		public static Boolean checkServer()
		{
			net.minecraftforge.fml.relauncher.Side side = net.minecraftforge.fml.relauncher.FMLLaunchHandler.side();
			checkSide(side);
			return side != net.minecraftforge.fml.relauncher.Side.CLIENT;
		}
	}

	public static class SideCheckOld
	{
		public static Boolean checkClient()
		{
			cpw.mods.fml.relauncher.Side side = cpw.mods.fml.relauncher.FMLLaunchHandler.side();
			checkSide(side);
			return side == cpw.mods.fml.relauncher.Side.CLIENT;
		}
		
		public static Boolean checkServer()
		{
			cpw.mods.fml.relauncher.Side side = cpw.mods.fml.relauncher.FMLLaunchHandler.side();
			checkSide(side);
			return side != cpw.mods.fml.relauncher.Side.CLIENT;
		}
	}
	
	public static class SideCheckLegacy
	{
		public static Boolean checkClient()
		{
			Object side = cpw.mods.fml.relauncher.FMLRelauncher.side();
			checkSide(side);
			return side.toString().equalsIgnoreCase("CLIENT");
		}
		
		public static Boolean checkServer()
		{
			Object side = cpw.mods.fml.relauncher.FMLRelauncher.side();
			checkSide(side);
			return !side.toString().equalsIgnoreCase("CLIENT");
		}
	}
	
	private static void checkSide(Object side) 
	{
		if(side == null)
			throw new RuntimeException("ForgeVersionProxy#isClient can only be determined after the main(String[] args) method has started!\n"
					+ "Use ForgeVersionProxy#isClientAgent Instead!\n"
					+ "Not only is this to prevent isClient from being false for your Java Agent but also to prevent pre-mature class loading of java.awt.Component");
	}

	public static void initMcVersion()
	{
		if(mcVersion != null)
			return;
		
		int build = buildVersion;
		switch(majorVersion)
		{
			case 1:
				if(build < 30)
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
				if(build <= 329)
					mcVersion = "1.4.1";
				else if(build <= 355)
					mcVersion = "1.4.2";
				else if(build <= 358)
					mcVersion = "1.4.3";
				else if(build <= 378)
					mcVersion = "1.4.4";
				else if(build <= 448)
					mcVersion = "1.4.5";
				else if(build <= 489)
					mcVersion = "1.4.6";
				else
					mcVersion = "1.4.7";
			break;
			
			case 7:
				if(build <= 598)
					mcVersion = "1.5";
				else if(build <= 682)
					mcVersion = "1.5.1";
				else
					mcVersion = "1.5.2";
			break;
			
			case 8:
				mcVersion = "1.6.1";
			break;
			
			case 9:
				if(build <= 871)
					mcVersion = "1.6.2";
				else if(minorVersion <= 11 && build <= 878)
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
		byte[] buffer = new byte[1048576/4];
		int length;
   	 	while ((length = in.read(buffer)) >= 0)
		{
			out.write(buffer, 0, length);
		}
	}

}
