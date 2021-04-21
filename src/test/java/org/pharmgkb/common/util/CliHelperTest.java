package org.pharmgkb.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


/**
 * JUnit test for {@link CliHelper}.
 * 
 * @author Mark Woon
 */
class CliHelperTest {


  @Test
  void testHelp() {

    CliHelper ch = new CliHelper(CliHelperTest.class);
    ch.addOption("d", "directory", "directory", true, "dir");

    assertFalse(ch.parse(new String[]{ "-h" }));
    assertTrue(ch.isHelpRequested());
    assertFalse(ch.hasError());


    // with other args
    ch = new CliHelper(CliHelperTest.class);
    ch.addOption("d", "directory", "directory", true, "dir");

    assertFalse(ch.parse(new String[]{ "-h", "-d", "/some/path" }));
    assertTrue(ch.isHelpRequested());
    assertFalse(ch.hasError());
  }


  @Test
  void testNoArgs() {

    CliHelper ch = new CliHelper(CliHelperTest.class);
    ch.addOption("d", "directory", "directory", true, "dir");

    assertFalse(ch.parse(null));
    assertFalse(ch.isHelpRequested());
    assertTrue(ch.hasError());
    assertEquals("Missing required option: d", ch.getError());
  }


  @Test
  void testUnknownArg() {

    CliHelper ch = new CliHelper(CliHelperTest.class);
    ch.addOption("d", "directory", "directory", true, "dir");

    assertFalse(ch.parse(new String[] { "-h", "-q" }));
    assertFalse(ch.isHelpRequested());
    assertTrue(ch.hasError());
    assertEquals("Unrecognized option: -q", ch.getError());
  }


  @Test
  void testGotDir() {

    CliHelper ch = new CliHelper(CliHelperTest.class);
    ch.addOption("d", "directory", "directory desc", true, "dir");

    assertTrue(ch.parse(new String[]{ "-d", "/some/path" }));
    assertFalse(ch.isHelpRequested());
    assertFalse(ch.hasError());
    assertEquals("/some/path", ch.getValue("d"));
  }


  @Test
  void testRequiredParam() {

    CliHelper ch = new CliHelper(CliHelperTest.class);
    ch.addOption("d", "directory", "directory", true, "dir");

    assertFalse(ch.parse(new String[] { "-d" }));
    assertFalse(ch.isHelpRequested());
    assertTrue(ch.hasError());
    assertEquals("Missing argument for option: d", ch.getError());
  }
}
