package jredfox;

public class DpiFixAnnOF extends DpiFixAnn implements cpw.mods.fml.relauncher.IClassTransformer {

	@Override
	public byte[] transform(String name, byte[] bytes) {
		return this.transform(name, name, bytes);
	}

}
