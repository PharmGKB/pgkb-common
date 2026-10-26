package org.pharmgkb.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * This is a JUnit test for {@link AnsiConsole}.
 * <p>
 * Whether ANSI is actually enabled in a given JVM run is decided once, at class-load time
 * ({@code System.console()}/IntelliJ classpath detection), with no injectable seam to override it per-test -
 * so these tests can't force both the "ANSI supported" and "ANSI not supported" branches within a single
 * run. Assertions below either hold regardless of which branch is active, or are conditioned on the
 * observed environment.
 *
 * @author Mark Woon
 */
class AnsiConsoleTest {

  @Test
  void colorizeAlwaysContainsOriginalText() {
    assertTrue(AnsiConsole.colorize("hello", AnsiConsole.ANSI_RED).contains("hello"));
  }

  @Test
  void colorizeProducesOneOfItsTwoDocumentedShapes() {
    String result = AnsiConsole.colorize("hello", AnsiConsole.ANSI_RED);
    boolean plain = result.equals("hello");
    boolean colored = result.equals(AnsiConsole.ANSI_RED + "hello" + AnsiConsole.ANSI_RESET);
    assertTrue(plain || colored, result);
  }

  @Test
  void styleWarningUsesYellow() {
    String result = AnsiConsole.styleWarning("hello");
    if (!result.equals("hello")) {
      // ANSI is active in this environment - verify it specifically used yellow, not some other color
      assertEquals(AnsiConsole.ANSI_YELLOW + "hello" + AnsiConsole.ANSI_RESET, result);
    }
  }

  @Test
  void styleErrorUsesRed() {
    String result = AnsiConsole.styleError("hello");
    if (!result.equals("hello")) {
      assertEquals(AnsiConsole.ANSI_RED + "hello" + AnsiConsole.ANSI_RESET, result);
    }
  }
}
