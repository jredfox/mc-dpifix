package jredfox;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import net.minecraftforge.common.ForgeVersion;

public class DpiFixOneFiveTransformer implements IDpiFixTransformer {
	
	/**
	 * "func_73718_a"  "a"
	 * "func_71480_b"  "b"
	 * "field_71473_z" "A"
	 */

	@Override
	public void transform(String notch_mc, int index, ClassNode classNode)
	{
		switch(index)
		{
			case 0:
            	System.out.println("Patching: Minecraft Fullscreen to fix MC-68754, MC-111419, MC-160054");
				patchFullScreen(notch_mc, classNode);
				patchMaxResFix( notch_mc, classNode);
			break;
			
			case 1:
				patchLoadingScreenRenderer(notch_mc, classNode);
			break;
			
			case 2:
				DpiFixAnnotation.patchAtMod(classNode);
			break;
			
			case 3:
				removeAppletShutdown(classNode);
			break;
		}
	}

	public void patchFullScreen(String notch_mc, ClassNode classNode)
	{
		String mcApplet = CoreUtils.getObfString("mcApplet", "A");
		for(FieldNode f : classNode.fields)
		{
			if(f.name.equals(mcApplet) )
			{
				f.access = Opcodes.ACC_PUBLIC;
			}
		}
		
		MethodNode ctr = CoreUtils.getFirstConstructor(classNode);
		
		//Find Injection point of first-ish line of the constructor
		AbstractInsnNode spotPre = null;
		AbstractInsnNode abctr = ctr.instructions.getFirst();
		while(abctr != null)
		{
			if(abctr instanceof VarInsnNode && abctr.getOpcode() == Opcodes.ALOAD || abctr.getOpcode() == Opcodes.ILOAD)
			{
				VarInsnNode var = (VarInsnNode) abctr;
				if(var.var > 0)
				{
					spotPre = CoreUtils.nextLabelNode(abctr);
					break;
				}
			}
			abctr = abctr.getNext();
		}
		
		//DpiFixDeAWT.hide(par1Canvas, par2MinecraftApplet);
		//this.tempDisplayWidth = par3;
		//this.tempDisplayHeight = par4;
		InsnList lctr = new InsnList();
		lctr.add(new VarInsnNode(Opcodes.ALOAD, 1));
		lctr.add(new VarInsnNode(Opcodes.ALOAD, 2));
		lctr.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixDeAWT", "hide", "(Ljava/awt/Canvas;Lnet/minecraft/client/MinecraftApplet;)V", false));
		lctr.add(new LabelNode());
		lctr.add(new VarInsnNode(Opcodes.ALOAD, 0));
		lctr.add(new VarInsnNode(Opcodes.ILOAD, 3));
		lctr.add(new FieldInsnNode(Opcodes.PUTFIELD, "net/minecraft/client/Minecraft", CoreUtils.getObfString("tempDisplayWidth", "Z"), "I"));//TODO: Obfnames
		lctr.add(new LabelNode());
		lctr.add(new VarInsnNode(Opcodes.ALOAD, 0));
		lctr.add(new VarInsnNode(Opcodes.ILOAD, 4));
		lctr.add(new FieldInsnNode(Opcodes.PUTFIELD, "net/minecraft/client/Minecraft", CoreUtils.getObfString("tempDisplayHeight", "aa"), "I"));//TODO: Obfnames
		lctr.add(new LabelNode());
		ctr.instructions.insert(spotPre, lctr);
		
		//DpiFixDeAWT.hide(this);
		InsnList lctrLast = new InsnList();
		lctrLast.add(new VarInsnNode(Opcodes.ALOAD, 0));
		lctrLast.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixDeAWT", "hide", "(Lnet/minecraft/client/Minecraft;)V", false));
		ctr.instructions.insert(CoreUtils.getLastInstruction(ctr, Opcodes.PUTFIELD), lctrLast);
		
		
	}
	
	private void patchMaxResFix(String notch_mc, ClassNode classNode) 
	{
		
	}
	
	public void patchLoadingScreenRenderer(String name, ClassNode classNode)
	{
		if(!DpiFix.maximizeFix || ForgeVersion.getMajorVersion() >= 10)
			return;
		
		System.out.println("Patching: LoadingScreenRenderer");
		MethodNode m = CoreUtils.getMethodNode(classNode, CoreUtils.getObfString("setLoadingProgress", "a"), "(I)V");
		
		//Disable all Display#update calls
		disableDisplayUpdate(m);
		
		//DpiFixCoreMod.tickDisplay(this.mc);
		InsnList li = new InsnList();
		li.add(new VarInsnNode(Opcodes.ALOAD, 0));
		li.add(new FieldInsnNode(Opcodes.GETFIELD, name, CoreUtils.getObfString("mc", "field_73725_b"), "Lnet/minecraft/client/Minecraft;"));
		li.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixCoreMod", "tickDisplay", "(Lnet/minecraft/client/Minecraft;)V", false));
		m.instructions.insertBefore(CoreUtils.getLastMethodInsn(m, Opcodes.INVOKESTATIC, "java/lang/Thread", "yield", "()V", false), li);
	}
	
	private void disableDisplayUpdate(MethodNode m)
	{
		//Disable all calls of Display#update
		MethodInsnNode updateInsn = CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "update", "()V", false);
		AbstractInsnNode ab = m.instructions.getFirst();
		while(ab != null)
		{
			if(ab instanceof MethodInsnNode && CoreUtils.equals(updateInsn, (MethodInsnNode) ab))
			{
				MethodInsnNode minsn = (MethodInsnNode) ab;
				minsn.owner = "jredfox/CoreUtils";
				minsn.name = "disabled";
			}
			ab = ab.getNext();
		}
	}
	
	public void removeAppletShutdown(ClassNode classNode) 
	{
		if(!DpiFix.deawt)
			return;
		
		System.out.println("Removing Applet Shutdown");
		MethodNode m = CoreUtils.getMethodNode(classNode, CoreUtils.getObfString("shutdown", "b"), "()V");
		InsnList li = new InsnList();
		LabelNode l0 = new LabelNode();
		li.add(l0);
		li.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/CoreUtils", "rtrue", "()Z", false));
		LabelNode l1 = new LabelNode();
		li.add(new JumpInsnNode(Opcodes.IFEQ, l1));
		li.add(new InsnNode(Opcodes.RETURN));
		li.add(l1);
		li.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
		m.instructions.insert(li);
	}

}
