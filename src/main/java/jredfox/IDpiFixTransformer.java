package jredfox;

import org.objectweb.asm.tree.ClassNode;

public interface IDpiFixTransformer {
	
	public void transform(String notch_mc, int index, ClassNode classNode);

}
