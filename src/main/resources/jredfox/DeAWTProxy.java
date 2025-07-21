package jredfox;

import jredfox.forgeversion.*;
import java.awt.*;

public class DeAWTProxy
{
    private static boolean canSetVisible;
    
    public DeAWTProxy() {
        super();
    }
    
    public static boolean getVisible() {
        if (!ForgeVersionProxy.onefive || !DpiFix.agentmode) {
            System.err.println("canSetVisible not Found onefive:" + ForgeVersionProxy.onefive + " agent:" + DpiFix.agentmode);
            return true;
        }
        return Component.canSetVisible;
    }
    
    public static void setVisible(final boolean enable) {
        if (!ForgeVersionProxy.onefive || !DpiFix.agentmode) {
            System.err.println("canSetVisible not Found onefive:" + ForgeVersionProxy.onefive + " agent:" + DpiFix.agentmode);
            return;
        }
        Component.canSetVisible = enable;
    }
    
    public static void load() {
    }
}
