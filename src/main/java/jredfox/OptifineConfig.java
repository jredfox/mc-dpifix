package jredfox;

import java.awt.Dimension;

import org.lwjgl.opengl.DisplayMode;

/**
 * A Proxy for Optifine's Config. Since it's in the default Package we call these and then transform our own mod class to bypass java's dumb issue
 * of "cannot import default classes"
 */
public class OptifineConfig {
	
	public static String OF_EDITION;
	public static Dimension getFullscreenDimension() { return null; }
	public static DisplayMode getDisplayMode(Dimension dim) { return null; }
	public static void setDesktopDisplayMode(DisplayMode desktopDisplayMode) { }
	public static DisplayMode getDesktopDisplayMode() { return null; }
	public static int getAntialiasingLevel() { return 0; }
	
}
