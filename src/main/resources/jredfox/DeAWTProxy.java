package jredfox;

import java.awt.Component;

public class DeAWTProxy {
   private static boolean canSetVisible;
   public static boolean hasField = Boolean.parseBoolean(System.getProperty("gamemodelib.deawt", "false"));

   public static boolean getVisible() {
      return !hasField ? true : Component.canSetVisible;
   }

   public static void setVisible(boolean enable) {
      if (hasField) {
         Component.canSetVisible = enable;
      }

   }
}
