package jredfox;

public class DpiFixCoreModOF extends DpiFixCoreMod implements cpw.mods.fml.relauncher.IClassTransformer {
	
	@Override
	public byte[] transform(String name, byte[] bytes) {
		return this.transform(name, name, bytes);
	}

}
