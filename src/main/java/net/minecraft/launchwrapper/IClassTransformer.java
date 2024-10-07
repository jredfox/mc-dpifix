package net.minecraft.launchwrapper;

public interface IClassTransformer
{
    public byte[] transform(String name, String transformedName, byte[] bytes);
}
