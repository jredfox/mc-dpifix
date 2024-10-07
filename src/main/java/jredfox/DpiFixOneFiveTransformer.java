package jredfox;

import org.lwjgl.opengl.Display;
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
import org.objectweb.asm.tree.LineNumberNode;
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
				patchMC(notch_mc, classNode);
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

	public void patchMC(String notch_mc, ClassNode classNode)
	{
		String mcApplet = CoreUtils.getObfString("mcApplet", "A");
		String leftClickCounter = CoreUtils.getObfString("leftClickCounter", "Y");
		String fullScreen = CoreUtils.getObfString("fullscreen", "S");
		String toggleFullScreen = CoreUtils.getObfString("toggleFullscreen", "k");
		
		//Manual Access Transformer (AT) for 1.5x
		for(FieldNode f : classNode.fields)
		{
			if(f.name.equals(mcApplet) || f.name.equals(leftClickCounter) || f.name.equals(fullScreen))
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
		lctr.add(new VarInsnNode(Opcodes.ALOAD, 0));
		lctr.add(new VarInsnNode(Opcodes.ILOAD, 3));
		lctr.add(new FieldInsnNode(Opcodes.PUTFIELD, "net/minecraft/client/Minecraft", CoreUtils.getObfString("tempDisplayWidth", "Z"), "I"));//TODO: Obfnames
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
		
		MethodNode startGame = CoreUtils.getMethodNode(classNode, CoreUtils.getObfString("startGame", "a"), "()V");
//    	Display.setParent(null);
//    	DpiFixDeAWT.fixIcons(this);
//    	DpiFixDeAWT.hide(this);
		InsnList startList = new InsnList();
		startList.add(new InsnNode(Opcodes.ACONST_NULL));
		startList.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "setParent", "(Ljava/awt/Canvas;)V", false));
		startList.add(new VarInsnNode(Opcodes.ALOAD, 0));
		startList.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixDeAWT", "fixIcons", "(Lnet/minecraft/client/Minecraft;)V", false));
		startList.add(new VarInsnNode(Opcodes.ALOAD, 0));
		startList.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixDeAWT", "hide", "(Lnet/minecraft/client/Minecraft;)V", false));
		startList.add(new LabelNode());
		startGame.instructions.insert(CoreUtils.getFirstInstruction(startGame), startList);
		
		//if(false && this.mcCanvas != null)
		FieldInsnNode id = CoreUtils.getFieldInsnNode(startGame, Opcodes.GETFIELD, "net/minecraft/client/Minecraft", CoreUtils.getObfString("mcCanvas", "m"), "Ljava/awt/Canvas;" );//TODO: Obf classes
		JumpInsnNode jump = CoreUtils.nextJumpInsnNode(id);
		LabelNode startIf = CoreUtils.prevLabel(id);
		InsnList startIfList = new InsnList();
		startIfList.add(new InsnNode(Opcodes.ICONST_0));
		startIfList.add(new JumpInsnNode(Opcodes.IFEQ, jump.label));
		startGame.instructions.insert(startIf, startIfList);
		
		//Display.setResizable(true);
		MethodInsnNode spotDisplay = CoreUtils.getMethodInsnNode(startGame, Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "setDisplayMode", "(Lorg/lwjgl/opengl/DisplayMode;)V", false);
		InsnList spotDisplayList = new InsnList();
		spotDisplayList.add(new InsnNode(Opcodes.ICONST_1));
		spotDisplayList.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "setResizable", "(Z)V", false));
		startGame.instructions.insert(spotDisplay, spotDisplayList);
		
		//DpiFixDeAWT.fixTitle
		MethodInsnNode startTitle = CoreUtils.getMethodInsnNode(startGame, Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "setTitle", "(Ljava/lang/String;)V", false);
		InsnList startTitleList = new InsnList();
		startTitleList.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixDeAWT", "fixTitle", "()V", false));
		startGame.instructions.insert(startTitle, startTitleList);
		
		//DpiFixDeAWT.loadScreen();
		//return;
		InsnList loadList = new InsnList();
		loadList.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixDeAWT", "loadScreen", "()V", false));
		loadList.add(new InsnNode(Opcodes.RETURN));
		loadList.add(new LabelNode());
		MethodNode loadScreen = CoreUtils.getMethodNode(classNode, CoreUtils.getObfString("loadScreen", "J"), "()V");
		loadScreen.instructions.insert(CoreUtils.getFirstInstruction(loadScreen), loadList);
		
		MethodNode runGameLoop = CoreUtils.getMethodNode(classNode, CoreUtils.getObfString("runGameLoop", "K"), "()V");
		//remove canvas check
		AbstractInsnNode closeRequest = CoreUtils.getMethodInsnNode(runGameLoop, Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "isCloseRequested", "()Z", false);
		if(closeRequest != null)
		{
			FieldInsnNode canvasInsn = CoreUtils.previousFieldInsnNode(runGameLoop, closeRequest, Opcodes.GETFIELD, "net/minecraft/client/Minecraft", CoreUtils.getObfString("mcCanvas", "m"), "Ljava/awt/Canvas;");
			if(canvasInsn != null)
			{
				runGameLoop.instructions.remove(canvasInsn.getNext());//Remove JumpInsnNode
				runGameLoop.instructions.remove(canvasInsn.getPrevious());//Remove ALOAD 0
				runGameLoop.instructions.remove(canvasInsn);//Remove check
			}
			else
				System.out.println("Unable to Remove Minecraft#runGameLoop mcCanvas Check. The X Button may not work now :(");
		}
		
		//Disable Display Updates
		this.disableDisplayUpdate(runGameLoop);
		
		//fix MC-160054 tabbing out or showing desktop results in minimized MC < 1.8
		if(DpiFix.fsTabFix)
		{
			if(CoreUtils.getMethodInsnNode(runGameLoop, Opcodes.INVOKEVIRTUAL, CoreUtils.getObfString("net/minecraft/client/Minecraft", notch_mc), toggleFullScreen, "()V", false) != null)
			{
				MethodInsnNode spotTab = CoreUtils.getMethodInsnNode(runGameLoop, Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "isActive", "()Z", false);
				if(spotTab != null)
				{
					JumpInsnNode jump2 = CoreUtils.nextJumpInsnNode(spotTab);
					if(jump2 != null)
					{
						InsnList list = new InsnList();
						list.add(new InsnNode(Opcodes.ICONST_0));
						list.add(new JumpInsnNode(Opcodes.IFEQ, jump2.label));
						runGameLoop.instructions.insert(CoreUtils.prevLabelNode(spotTab), list);
					}
				}
			}
		}
		
		//DpiFixCoreMod#tickDisplay(this);
		LineNumberNode yeild = CoreUtils.prevLabelNode(CoreUtils.getMethodInsnNode(runGameLoop, Opcodes.INVOKESTATIC, "java/lang/Thread", "yield", "()V", false));
		InsnList yeildList = new InsnList();
		yeildList.add(new VarInsnNode(Opcodes.ALOAD, 0));
		yeildList.add(CoreUtils.newMethodInsnNode(Opcodes.INVOKESTATIC, "jredfox/DpiFixCoreMod", "tickDisplay", "(Lnet/minecraft/client/Minecraft;)V", false));
		runGameLoop.instructions.insert(yeild, yeildList);
		
		//Disable AWT resize method
		FieldInsnNode canvasInsn2 = CoreUtils.getFieldInsnNode(runGameLoop, Opcodes.GETFIELD, "net/minecraft/client/Minecraft", CoreUtils.getObfString("mcCanvas", "m"), "Ljava/awt/Canvas;");
		if(canvasInsn2 != null)
		{
			InsnList liResize = new InsnList();
			liResize.add(new InsnNode(Opcodes.ICONST_0));
			liResize.add(new JumpInsnNode(Opcodes.IFEQ, CoreUtils.nextJumpInsnNode(canvasInsn2).label));
			runGameLoop.instructions.insert(CoreUtils.prevLabelNode(canvasInsn2), liResize);
		}
		
		//Disable FMLReEntry from ever appearing
		MethodNode fmlReEntry = CoreUtils.getMethodNode(classNode, "fmlReentry", "(Lcpw/mods/fml/relauncher/ArgsWrapper;)V");
		MethodInsnNode fmlReEntryInsn = CoreUtils.getMethodInsnNode(fmlReEntry, Opcodes.INVOKEVIRTUAL, "java/awt/Frame", "setVisible", "(Z)V", false);
		fmlReEntry.instructions.remove(fmlReEntryInsn.getPrevious());
		fmlReEntry.instructions.insertBefore(fmlReEntryInsn, new InsnNode(Opcodes.ICONST_0));
		
		MethodNode m = CoreUtils.getMethodNode(classNode, toggleFullScreen, "()V");
		m.access = Opcodes.ACC_PUBLIC;
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
