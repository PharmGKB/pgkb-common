package org.pharmgkb.common.util;

import java.util.Locale;


/**
 * Utility class to colorize text on command line.  This is meant to be used with {@link System#out} and
 * {@link System#err}.
 * <p>
 * Tries to detect when program might be printing to a non-ANSI-capable terminal or is being redirected.
 * In this case, ANSI codes will not be added.
 * <p>
 * Special case for running in IntelliJ - will always colorize.
 *
 * @author Mark Woon
 */
public class AnsiConsole {
  public static final String ANSI_RESET = "\u001B[0m";
  public static final String ANSI_RED = "\u001B[31m";
  public static final String ANSI_GREEN = "\u001B[32m";
  public static final String ANSI_YELLOW = "\u001B[33m";
  public static final String ANSI_BLUE = "\u001B[34m";
  public static final String ANSI_PURPLE = "\u001B[35m";
  public static final String ANSI_CYAN = "\u001B[36m";

  // based on https://github.com/fusesource/jansi/blob/master/src/main/java/org/fusesource/jansi/AnsiConsole.java
  private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("win");
  private static final boolean IS_CYGWIN = IS_WINDOWS
      && System.getenv("PWD") != null
      && System.getenv("PWD").startsWith("/");
  private static final boolean IS_MSYSTEM = IS_WINDOWS
      && System.getenv("MSYSTEM") != null
      && (System.getenv("MSYSTEM").startsWith("MINGW")
      || System.getenv("MSYSTEM").equals("MSYS"));
  private static final boolean IS_CONEMU = IS_WINDOWS
      && System.getenv("ConEmuPID") != null;
  private static final boolean sf_supportsAnsi = isAsciiConsole() || isIntelliJ();


  public static String colorize(String text, String color) {
    if (sf_supportsAnsi) {
      return color + text + ANSI_RESET;
    }
    return text;
  }

  public static String styleWarning(String text) {
    return colorize(text, ANSI_YELLOW);
  }

  public static String styleError(String text) {
    return colorize(text, ANSI_RED);
  }


  private static boolean isIntelliJ() {
    String classPath = System.getProperty("java.class.path");
    if (classPath.contains("idea_rt.jar")) {
      return true;
    }
    try {
      // from https://intellij-support.jetbrains.com/hc/en-us/community/posts/360000015340-Detecting-Intellij-from-within-main-methods
      return AnsiConsole.class.getClassLoader().loadClass("com.intellij.rt.execution.application.AppMainV2")
          != null;
    } catch (Exception ex) {
      return false;
    }
  }

  private static boolean isAsciiConsole() {
    // System.console check is not 100% reliable, but is close enough (https://stackoverflow.com/a/23419451/1063501)
    return (IS_CONEMU || IS_CYGWIN || IS_MSYSTEM || !IS_WINDOWS) && System.console() != null;
  }

  public static void main(String[] args) {
    System.out.println(styleWarning("hello"));
  }
}
