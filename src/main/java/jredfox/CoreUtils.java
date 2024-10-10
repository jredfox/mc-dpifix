package jredfox;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class CoreUtils {
	
	public static void disabled() {}
	public static boolean rfalse() { return false; }
	public static boolean rtrue() { return true; }
	
	/**
	 * Gets the Last LabelNode either before the return of the method or last label
	 */
	public static LabelNode getLastLabelNode(MethodNode method, boolean afterReturn)
	{
		AbstractInsnNode[] arr = method.instructions.toArray();
		boolean found = afterReturn;
		for(int i=arr.length-1;i>=0;i--)
		{
			AbstractInsnNode ab = arr[i];
			if(!found && isReturnOpcode(ab.getOpcode()))
				found = true;
			
			if(found && ab instanceof LabelNode)
			{
				return (LabelNode) ab;
			}
		}
		return null;
	}
	
	public static boolean isReturnOpcode(int opcode)
	{
		return opcode == Opcodes.RETURN || opcode == Opcodes.ARETURN || opcode == Opcodes.DRETURN || opcode == Opcodes.FRETURN || opcode == Opcodes.IRETURN || opcode == Opcodes.LRETURN;
	}
	
	public static MethodInsnNode nextMethodInsnNode(AbstractInsnNode pretarg, int opcode, String owner, String name, String desc, boolean itf) 
	{
		MethodInsnNode look = newMethodInsnNode(opcode, owner, name, desc, itf);
		AbstractInsnNode ab = pretarg;
		while(ab != null)
		{
			ab = ab.getNext();
			if(ab instanceof MethodInsnNode && equals(look, (MethodInsnNode) ab))
				return (MethodInsnNode) ab;
		}
		return null;
	}
	
	public static FieldInsnNode getFieldInsnNode(MethodNode node, int opcode, String owner, String name, String desc)
	{
		AbstractInsnNode[] arr = node.instructions.toArray();
		FieldInsnNode compare = new FieldInsnNode(opcode, owner, name, desc);
		for(AbstractInsnNode ab : arr)
		{
			if(ab instanceof FieldInsnNode && equals(compare, (FieldInsnNode)ab))
			{
				return (FieldInsnNode)ab;
			}
		}
		return null;
	}
	
	public static boolean equals(FieldInsnNode obj1, FieldInsnNode obj2)
	{
		return obj1.getOpcode() == obj2.getOpcode() && obj1.name.equals(obj2.name) && obj1.desc.equals(obj2.desc) && obj1.owner.equals(obj2.owner);
	}
	
	/**
	 * optimized way of getting a last instruction
	 */
	public static AbstractInsnNode getLastInstruction(MethodNode method, int opCode) 
	{
		AbstractInsnNode[] arr = method.instructions.toArray();
		for(int i=arr.length-1;i>=0;i--)
		{
			AbstractInsnNode node = arr[i];
			if(node.getOpcode() == opCode)
				return node;
		}
		return null;
	}
	
	/**
	 * optimized way of getting a last instruction
	 */
	public static AbstractInsnNode getLastInstruction(MethodNode method) 
	{
		AbstractInsnNode[] arr = method.instructions.toArray();
		for(int i=arr.length-1;i>=0;i--)
		{
			AbstractInsnNode node = arr[i];
			if(node instanceof LineNumberNode)
				return node;
		}
		return null;
	}
	
	/**
	 * getting the first instanceof of this will usually tell you where the initial injection point should be after
	 */
	public static LineNumberNode getFirstInstruction(MethodNode method) 
	{
		for(AbstractInsnNode obj : method.instructions.toArray())
			if(obj instanceof LineNumberNode)
				return (LineNumberNode) obj;
		return null;
	}
	
	public static MethodInsnNode getLastMethodInsn(MethodNode node, int opcode, String owner, String name, String desc, boolean isInterface) 
	{
		MethodInsnNode compare = newMethodInsnNode(opcode,owner,name,desc,isInterface);
		AbstractInsnNode[] list = node.instructions.toArray();
		for(int i=list.length-1;i>=0;i--)
		{
			AbstractInsnNode ab = list[i];
			if(ab.getOpcode() == opcode && ab instanceof MethodInsnNode && equals(compare, (MethodInsnNode)ab) )
			{
				return (MethodInsnNode)ab;
			}
		}
		return null;
	}
	
	public static LabelNode prevLabel(AbstractInsnNode spot) 
	{
		AbstractInsnNode n = spot;
		while(n != null)
		{
			n = n.getPrevious();
			if(n instanceof LabelNode)
				return (LabelNode) n;
		}
		return null;
	}

	public static LineNumberNode prevLabelNode(AbstractInsnNode spot) 
	{
		AbstractInsnNode n = spot;
		while(n != null)
		{
			n = n.getPrevious();
			if(n instanceof LineNumberNode)
				return (LineNumberNode) n;
		}
		return null;
	}
	
	public static LineNumberNode nextLabelNode(AbstractInsnNode spot) 
	{
		AbstractInsnNode n = spot;
		while(n != null)
		{
			n = n.getNext();
			if(n instanceof LineNumberNode)
				return (LineNumberNode) n;
		}
		return null;
	}

	public static JumpInsnNode nextJumpInsnNode(AbstractInsnNode spot)
	{
		AbstractInsnNode n = spot;
		while(n != null)
		{
			n = n.getNext();
			if(n instanceof JumpInsnNode)
				return (JumpInsnNode) n;
		}
		return null;
	}

	public static MethodInsnNode newMethodInsnNode(int opcode, String owner, String name, String desc, boolean itf) 
	{
		MethodInsnNode insn = new MethodInsnNode(opcode, owner, name, desc);
		return insn;
	}

	public static String getObfString(String deob, String ob)
	{
		return DpiFix.isObf ? ob : deob;
	}
	
	public static MethodInsnNode getMethodInsnNode(MethodNode node, int opcode, String owner, String name, String desc, boolean itf)
	{
		AbstractInsnNode[] arr = node.instructions.toArray();
		MethodInsnNode compare = newMethodInsnNode(opcode, owner, name, desc, itf);
		for(AbstractInsnNode ab : arr)
		{
			if(ab instanceof MethodInsnNode)
			{
				if(equals(compare, (MethodInsnNode)ab))
				{
					return (MethodInsnNode)ab;
				}
			}
		}
		return null;
	}
	
	public static boolean equals(MethodInsnNode obj1, MethodInsnNode obj2)
	{
		return obj1.getOpcode() == obj2.getOpcode() && obj1.name.equals(obj2.name) && obj1.desc.equals(obj2.desc) && obj1.owner.equals(obj2.owner);
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
	
	/**
	 * dumps a file from memory
	 * @throws IOException 
	 */
	public static void dumpFile(String name, byte[] bytes) throws IOException  
	{
    	name = name.replace('.', '/');
    	File f = new File(System.getProperty("user.dir"), "asm/dumps/dpi-fix/" + name + ".class");
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

	public static ClassNode getClassNode(byte[] basicClass)
	{
		ClassNode classNode = new ClassNode();
        ClassReader classReader = new ClassReader(basicClass);
        classReader.accept(classNode, 0);
        return classNode;
	}

	public static ClassWriter getClassWriter(ClassNode classNode, int flags) 
	{
        ClassWriter classWriter = new ClassWriter(flags);
        classNode.accept(classWriter);
        return classWriter;
	}

	public static byte[] toByteArray(ClassWriter classWriter, String transformedName) throws IOException 
	{
        byte[] bytes = classWriter.toByteArray();
        if(Boolean.parseBoolean(System.getProperty("asm.dump", "false")))
        	dumpFile(transformedName, bytes);
        
        return bytes;
	}
	
	public static MethodNode getFirstConstructor(ClassNode classNode) 
	{
		for (MethodNode method : classNode.methods)
		{
			if (method.name.equals("<init>"))
			{
				return method;
			}
		}
		return null;
	}
	
	public static FieldInsnNode previousFieldInsnNode(AbstractInsnNode spot, int opcode, String owner, String name, String desc) 
	{
		FieldInsnNode compare = new FieldInsnNode(opcode, owner, name, desc);
		AbstractInsnNode ab = spot;
		while(ab != null)
		{
			if(ab instanceof FieldInsnNode && equals(compare, (FieldInsnNode)ab))
			{
				return (FieldInsnNode)ab;
			}
			ab = ab.getPrevious();
		}
		return null;
	}

	public static FieldInsnNode nextFieldInsnNode(AbstractInsnNode pretarg, int opcode, String owner, String name, String desc) 
	{
		FieldInsnNode look = new FieldInsnNode(opcode, owner, name, desc);
		AbstractInsnNode ab = pretarg;
		while(ab != null)
		{
			ab = ab.getNext();
			if(ab instanceof FieldInsnNode && equals(look, (FieldInsnNode) ab))
				return (FieldInsnNode) ab;
		}
		return null;
	}

}
