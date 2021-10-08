package org.pharmgkb.common.util;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.commons.cli.Option;
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

    CliHelper ch = new CliHelper(CliHelperTest.class)
        .addOption("d", "directory", "directory", true, "dir");

    assertFalse(ch.parse(new String[]{ "-h" }));
    assertTrue(ch.isHelpRequested());
    assertFalse(ch.hasError());


    // with other args
    ch = new CliHelper(CliHelperTest.class)
        .addOption("d", "directory", "directory", true, "dir");

    assertFalse(ch.parse(new String[]{ "-h", "-d", "/some/path" }));
    assertTrue(ch.isHelpRequested());
    assertFalse(ch.hasError());
  }


  @Test
  void testVersion() {

    CliHelper ch = new CliHelper(CliHelperTest.class)
        .addVersion("FooBar 1.0")
        .addOption("d", "directory", "directory", true, "dir");

    assertFalse(ch.parse(new String[]{ "-version" }));
    assertTrue(ch.isVersionRequested());
    assertFalse(ch.hasError());


    // with other args
    ch = new CliHelper(CliHelperTest.class)
        .addVersion("FooBar 1.0")
        .addOption("d", "directory", "directory", true, "dir");

    assertFalse(ch.parse(new String[]{ "--version", "-d", "/some/path" }));
    assertTrue(ch.isVersionRequested());
    assertFalse(ch.hasError());
  }


  @Test
  void testNoArgs() {

    CliHelper ch = new CliHelper(CliHelperTest.class)
        .addOption("d", "directory", "directory", true, "dir");

    assertFalse(ch.parse(null));
    assertFalse(ch.isHelpRequested());
    assertTrue(ch.hasError());
    assertEquals("Missing required option: d", ch.getError());
  }


  @Test
  void testUnknownArg() {

    CliHelper ch = new CliHelper(CliHelperTest.class)
        .addOption("d", "directory", "directory", true, "dir");

    assertFalse(ch.parse(new String[] { "-h", "-q" }));
    assertFalse(ch.isHelpRequested());
    assertTrue(ch.hasError());
    assertEquals("Unrecognized option: -q", ch.getError());
  }

  @Test
  void testReservedArg() {
    CliHelper ch = new CliHelper(CliHelperTest.class);

    assertThrows(IllegalArgumentException.class, () -> ch.addOption("h", "foo", "bar"));
    assertThrows(IllegalArgumentException.class, () -> ch.addOption("v", "foo", "bar"));

    assertThrows(IllegalArgumentException.class, () -> ch.addOption("h", "foo", "bar", false, "h"));
    assertThrows(IllegalArgumentException.class, () -> ch.addOption("v", "foo", "bar", false, "v"));

    assertThrows(IllegalArgumentException.class, () -> ch.addOption("h", "foo", "bar", false, "h", 1, true));
    assertThrows(IllegalArgumentException.class, () -> ch.addOption("v", "foo", "bar", false, "v", 1, true));

    assertThrows(IllegalArgumentException.class, () -> ch.addOption(Option.builder("h")
        .longOpt("foo")
        .desc("bar")
        .hasArg(false)
        .build()));
    assertThrows(IllegalArgumentException.class, () -> ch.addOption(Option.builder("v")
        .longOpt("foo")
        .desc("bar")
        .hasArg(false)
        .build()));
  }


  @Test
  void testGotDirNotExist() {

    CliHelper ch = new CliHelper(CliHelperTest.class);
    ch.addOption("d", "directory", "directory desc", true, "dir");

    assertTrue(ch.parse(new String[]{ "-d", "/some/path" }));
    assertFalse(ch.isHelpRequested());
    assertFalse(ch.hasError());
    assertEquals("/some/path", ch.getValue("d"));
    assertThrows(IllegalArgumentException.class, () -> ch.getValidDirectory("d", false));
  }

  @Test
  void testGotDir() throws Exception {

    CliHelper ch = new CliHelper(CliHelperTest.class);
    ch.addOption("d", "directory", "directory desc", true, "dir");

    Path file = PathUtils.getPathToResource(getClass(), "PathUtilsTest.txt");
    Path dir = file.getParent();

    assertTrue(ch.parse(new String[]{ "-d", dir.toString() }));
    assertFalse(ch.isHelpRequested());
    assertFalse(ch.hasError());
    assertEquals(dir.toString(), ch.getValue("d"));
    assertEquals(dir, ch.getValidDirectory("d", false));
  }


  @Test
  void testGotFileNotExist() {

    CliHelper ch = new CliHelper(CliHelperTest.class);
    ch.addOption("f", "file", "file desc", true, "f");

    assertTrue(ch.parse(new String[]{ "-f", "/some/path" }));
    assertFalse(ch.isHelpRequested());
    assertFalse(ch.hasError());
    assertEquals("/some/path", ch.getValue("f"));
    assertEquals(Paths.get("/some/path"), ch.getValidFile("f", false));
    assertThrows(IllegalArgumentException.class, () -> ch.getValidFile("f", true));
  }

  @Test
  void testGotFile() {

    CliHelper ch = new CliHelper(CliHelperTest.class);
    ch.addOption("f", "file", "file desc", true, "f");

    Path file = PathUtils.getPathToResource(getClass(), "PathUtilsTest.txt");

    assertTrue(ch.parse(new String[]{ "-f", file.toString() }));
    assertFalse(ch.isHelpRequested());
    assertFalse(ch.hasError());
    assertEquals(file.toString(), ch.getValue("f"));
    assertEquals(file, ch.getValidFile("f", true));
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

  @Test
  void testFlag() {
    CliHelper ch = new CliHelper(CliHelperTest.class);
    ch.addOption("b", "beep", "beep boop");

    assertTrue(ch.parse(new String[] { "-b" }));
    assertFalse(ch.isHelpRequested());
    assertFalse(ch.hasError());
    assertFalse(ch.hasOption("d"));
    assertTrue(ch.hasOption("b"));
  }

  @Test
  void testCustomOption() {
    CliHelper ch = new CliHelper(CliHelperTest.class);
    ch.addOption(Option.builder("b")
        .longOpt("beep")
        .desc("beep boop")
        .build());

    assertTrue(ch.parse(new String[] { "-b" }));
    assertFalse(ch.isHelpRequested());
    assertFalse(ch.hasError());
    assertFalse(ch.hasOption("d"));
    assertTrue(ch.hasOption("b"));
  }
}
