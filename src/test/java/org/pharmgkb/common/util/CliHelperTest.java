package org.pharmgkb.common.util;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
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
  void testPrintHelpOutputsUsageAndOptions() {
    // locks in printHelp()'s actual printed content (program name, both option forms, description) across
    // the commons-cli HelpFormatter migration - a broken migration could throw, print nothing, or silently
    // drop the option table without failing any other test, since nothing else asserts on this output
    CliHelper ch = new CliHelper(CliHelperTest.class)
        .addOption("d", "directory", "the target directory", true, "dir");

    PrintStream origOut = System.out;
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
    try {
      ch.printHelp();
    } finally {
      System.setOut(origOut);
    }
    String output = captured.toString(StandardCharsets.UTF_8);
    assertTrue(output.contains(CliHelperTest.class.getSimpleName()), output);
    assertTrue(output.contains("-d") && output.contains("--directory"), output);
    assertTrue(output.contains("the target directory"), output);
    // the new HelpFormatter's default builder shows a "Since" column (populated from Option.getSince(),
    // which nothing in this codebase ever sets) - every row would show a meaningless "--" filler value with
    // no way to ever populate it, pure noise on every --help invocation. main's old HelpFormatter never had
    // such a column at all.
    assertFalse(output.contains("Since"), output);
  }

  @Test
  void testHelpWithIncompleteOption() {
    // -h must be detected even if another option is present without its required argument
    CliHelper ch = new CliHelper(CliHelperTest.class)
        .addOption("d", "directory", "directory", true, "dir");

    assertFalse(ch.parse(new String[]{ "-h", "-d" }));
    assertTrue(ch.isHelpRequested());
    assertFalse(ch.hasError());

    // same, but for an option added via addOption(Option) with .required()
    CliHelper ch2 = new CliHelper(CliHelperTest.class)
        .addOption(Option.builder("f").longOpt("file").required().hasArg().build());

    assertFalse(ch2.parse(new String[]{ "-h", "-f" }));
    assertTrue(ch2.isHelpRequested());
    assertFalse(ch2.hasError());
  }

  @Test
  void testAttachedValueSyntax() {
    // GNU-style "--opt=value"/"-o=value" attached-value syntax must not be rejected as an unrecognized
    // option by the lenient first pass (used only to detect -h/--version)
    CliHelper ch = new CliHelper(CliHelperTest.class)
        .addOption("d", "directory", "directory", true, "dir");

    assertTrue(ch.parse(new String[]{ "--directory=/some/path" }));
    assertFalse(ch.hasError());
    assertEquals("/some/path", ch.getValue("d"));

    ch = new CliHelper(CliHelperTest.class)
        .addOption("d", "directory", "directory", true, "dir");
    assertTrue(ch.parse(new String[]{ "-d=/some/path" }));
    assertFalse(ch.hasError());
    assertEquals("/some/path", ch.getValue("d"));

    // same, but for an option added via addOption(Option)
    CliHelper ch2 = new CliHelper(CliHelperTest.class)
        .addOption(Option.builder("f").longOpt("file").required().hasArg().build());
    assertTrue(ch2.parse(new String[]{ "--file=/some/path" }));
    assertFalse(ch2.hasError());
    assertEquals("/some/path", ch2.getValue("f"));
  }

  @Test
  void testValueSeparatorSyntax() {
    // property-style "-Dkey=value" (multi-arg option with a value separator) must not be rejected as an
    // unrecognized option by the lenient first pass, and -h must still be detected alongside it
    CliHelper ch = new CliHelper(CliHelperTest.class)
        .addOption(Option.builder("D").longOpt("define").hasArgs().valueSeparator('=').desc("property").build());

    assertTrue(ch.parse(new String[]{ "-Dkey=value" }));
    assertFalse(ch.hasError());
    assertEquals(List.of("key", "value"), ch.getValues("D"));

    CliHelper ch2 = new CliHelper(CliHelperTest.class)
        .addOption(Option.builder("D").longOpt("define").hasArgs().valueSeparator('=').desc("property").build());
    assertFalse(ch2.parse(new String[]{ "-Dkey=value", "-h" }));
    assertTrue(ch2.isHelpRequested());
    assertFalse(ch2.hasError());
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
  void testParseDoesNotLeakStateAcrossCalls() {
    CliHelper ch = new CliHelper(CliHelperTest.class)
        .addOption("d", "directory", "directory", false, "dir");

    // a failed parse sets an error and a stale command line
    assertFalse(ch.parse(new String[]{ "-q" }));
    assertTrue(ch.hasError());

    // a subsequent successful parse must not still report the earlier error
    assertTrue(ch.parse(new String[]{ "-d", "/some/path" }));
    assertFalse(ch.hasError());
    assertEquals("/some/path", ch.getValue("d"));

    // a subsequent failed parse (fails during the help-options pre-parse) must not leave the
    // previous successful parse's command line queryable
    assertFalse(ch.parse(new String[]{ "-q" }));
    assertTrue(ch.hasError());
    assertThrows(IllegalStateException.class, () -> ch.getValue("d"));
  }

  @Test
  void testFailedSecondStageParseDoesNotLeaveStaleCommandLine() {
    CliHelper ch = new CliHelper(CliHelperTest.class);
    ch.addOption(Option.builder("f").longOpt("file").required().numberOfArgs(2).build());
    ch.addOption(Option.builder("x").longOpt("extra").required().build());

    // "-f a b" satisfies the lenient help-options pre-parse (which strips the "required" flag but keeps
    // this option's own arg count), but the real parse fails because the separately-required "-x" is missing
    assertFalse(ch.parse(new String[]{ "-f", "a", "b" }));
    assertTrue(ch.hasError());
    // querying after a failed parse must not silently return the earlier lenient pass's data
    assertThrows(IllegalStateException.class, () -> ch.getValues("f"));
  }

  @Test
  void testReservedArg() {
    CliHelper ch = new CliHelper(CliHelperTest.class);

    // before addVersion() is called, --version isn't reserved and the error message must not claim it is
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> ch.addOption("h", "foo", "bar"));
    assertFalse(ex.getMessage().contains("version"), ex.getMessage());
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

    // long-name collisions with reserved flags, even when the short name doesn't collide
    assertThrows(IllegalArgumentException.class, () -> ch.addOption("x", "help", "bar"));
    assertThrows(IllegalArgumentException.class, () -> ch.addOption("x", "verbose", "bar"));
    assertThrows(IllegalArgumentException.class, () -> ch.addOption("x", "verbose", "bar", false, "x"));
    assertThrows(IllegalArgumentException.class, () -> ch.addOption("x", "verbose", "bar", false, "x", 1, true));
    assertThrows(IllegalArgumentException.class, () -> ch.addOption(Option.builder("x")
        .longOpt("verbose")
        .desc("bar")
        .hasArg(false)
        .build()));

    // "version" is not reserved unless addVersion() was called
    ch.addOption("version", "notVersion", "bar");
    ch.addOption("y", "version", "bar");
  }

  @Test
  void testReservedArgLongNameMatchingShortForm() {
    // a LONG name equal to "h"/"v" must be rejected too, not just a short name equal to those - Commons CLI
    // resolves "--h"/"--v" against the built-in -h/--help and -v/--verbose options regardless of what the
    // user's own option's short name is, hijacking isHelpRequested()/isVerbose() and swallowing the user's
    // intended value instead of just failing to register a colliding option
    CliHelper ch = new CliHelper(CliHelperTest.class);
    assertThrows(IllegalArgumentException.class, () -> ch.addOption("x", "h", "bar"));
    assertThrows(IllegalArgumentException.class, () -> ch.addOption("x", "v", "bar"));
  }

  @Test
  void testReservedArgShortNameMatchingLongForm() {
    // a SHORT name equal to "verbose"/"help" (Commons CLI doesn't require short opts to be 1 character) must
    // be rejected too, not just a long name equal to those - otherwise a user's own "-verbose"/"-help"
    // option gets misidentified by hasOption(sf_verboseFlag)/isHelpRequested() (which match by either short
    // or long opt), hijacking isVerbose()/help-printing even though the user never asked for either
    CliHelper ch = new CliHelper(CliHelperTest.class);
    assertThrows(IllegalArgumentException.class, () -> ch.addOption("verbose", "vb", "user's own flag"));
    assertThrows(IllegalArgumentException.class, () -> ch.addOption("help", "hp", "user's own flag"));
  }

  @Test
  void testReservedArgAfterAddVersion() {
    CliHelper ch = new CliHelper(CliHelperTest.class).addVersion("1.0");

    // once addVersion() is called, both the short and long "version" forms become reserved, and the error
    // message reflects that
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
        ch.addOption("version", "notVersion", "bar"));
    assertTrue(ex.getMessage().contains("version"), ex.getMessage());
    assertThrows(IllegalArgumentException.class, () -> ch.addOption("y", "version", "bar"));
  }

  @Test
  void testAddOptionRejectsLongOnlyCollisionWithExistingShortOption() {
    // a long-only option (no short name) whose long name equals an EXISTING option's short name must be
    // rejected - Commons CLI keys its internal "short opts" map by Option.getKey() (short name if present,
    // else long name) for every option, so a long-only "d" occupies the same map slot as a short-only "d";
    // whichever is added second silently overwrites the first one there, and a later generic lookup by "d"
    // (e.g. getOptionValue("d")) then resolves to the wrong option even though "-d" was parsed correctly
    // against the original one at parse time
    CliHelper ch = new CliHelper(CliHelperTest.class)
        .addOption("d", null, "short-only", false, "value");
    assertThrows(IllegalArgumentException.class,
        () -> ch.addOption(null, "d", "long-only, collides with -d", false, "value"));

    // the original short option must still work after the rejected collision
    assertTrue(ch.parse(new String[]{ "-d", "value" }));
    assertEquals("value", ch.getValue("d"));
  }

  @Test
  void testAddVersionAfterConflictingOption() {
    // addVersion() must not silently clobber a "version" option registered before it, regardless of call order
    CliHelper chShortName = new CliHelper(CliHelperTest.class).addOption("version", "notVersion", "bar");
    assertThrows(IllegalArgumentException.class, () -> chShortName.addVersion("1.0"));

    CliHelper chLongName = new CliHelper(CliHelperTest.class).addOption("y", "version", "bar");
    assertThrows(IllegalArgumentException.class, () -> chLongName.addVersion("1.0"));
  }

  @Test
  void testAddVersionCalledTwiceHasAccurateMessage() {
    // calling addVersion() a second time collides with itself (its own first call already registered
    // "version"), not "another option" - the error message must say so accurately, not blame a nonexistent
    // other option
    CliHelper ch = new CliHelper(CliHelperTest.class).addVersion("1.0");
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> ch.addVersion("2.0"));
    assertFalse(ex.getMessage().contains("another option"), ex.getMessage());
  }


  @Test
  void testAddOptionRejectsDuplicateShortName() {
    // a later addOption() call reusing an earlier option's short name must be rejected outright - silently
    // registering it left the earlier option's requiredOpts entry orphaned, producing a CLI that rejects a
    // supplied value as "missing"
    CliHelper ch = new CliHelper(CliHelperTest.class)
        .addOption("d", "directory", "directory", true, "dir");
    assertThrows(IllegalArgumentException.class, () -> ch.addOption("d", "other", "desc"));
  }

  @Test
  void testAddOptionRejectsDuplicateLongName() {
    CliHelper ch = new CliHelper(CliHelperTest.class)
        .addOption("d", "directory", "directory", true, "dir");
    assertThrows(IllegalArgumentException.class, () -> ch.addOption("x", "directory", "desc"));
  }

  @Test
  void testAddOptionMultiArgOverloadRejectsDuplicateName() {
    CliHelper ch = new CliHelper(CliHelperTest.class)
        .addOption("d", "directory", "directory", true, "dir");
    assertThrows(IllegalArgumentException.class,
        () -> ch.addOption("x", "directory", "desc", false, "x", 1, true));
  }

  @Test
  void testAddOptionOptionOverloadRejectsDuplicateName() {
    CliHelper ch = new CliHelper(CliHelperTest.class)
        .addOption("d", "directory", "directory", true, "dir");
    assertThrows(IllegalArgumentException.class,
        () -> ch.addOption(Option.builder("x").longOpt("directory").desc("desc").build()));
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


  @Test
  void testGetIntValue() {
    CliHelper ch = new CliHelper(CliHelperTest.class)
        .addOption("n", "number", "a number", false, "n");

    assertTrue(ch.parse(new String[]{ "-n", "42" }));
    assertEquals(42, ch.getIntValue("n"));

    CliHelper chMissing = new CliHelper(CliHelperTest.class)
        .addOption("n", "number", "a number", false, "n");
    assertTrue(chMissing.parse(new String[0]));
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> chMissing.getIntValue("n"));
    assertEquals("Missing option 'n'", ex.getMessage());
  }

  @Test
  void testGetRequiredValue() {
    CliHelper ch = new CliHelper(CliHelperTest.class)
        .addOption("d", "directory", "directory", true, "dir");

    assertTrue(ch.parse(new String[]{ "-d", "/some/path" }));
    assertEquals("/some/path", ch.getRequiredValue("d"));

    CliHelper chMissing = new CliHelper(CliHelperTest.class)
        .addOption("d", "directory", "directory", false, "dir");
    assertTrue(chMissing.parse(new String[0]));
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> chMissing.getRequiredValue("d"));
    assertEquals("Missing option 'd'", ex.getMessage());
  }

  @Test
  void testGetRequiredValueWithWhitespaceOnlyValueGivesAccurateMessage() {
    // getValue() strips a whitespace-only value to null, same as an absent one - but hasOption() is still
    // true here (the option WAS supplied, just with a blank value), so "Missing option" would be a wrong
    // diagnosis; a caller debugging "-d '   '" needs to be told the value is blank, not that they forgot -d
    CliHelper ch = new CliHelper(CliHelperTest.class)
        .addOption("d", "directory", "directory", false, "dir");
    assertTrue(ch.parse(new String[]{ "-d", "   " }));
    assertTrue(ch.hasOption("d"));
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> ch.getRequiredValue("d"));
    assertEquals("Option 'd' has a blank value", ex.getMessage());
  }

  @Test
  void testGetRequiredValueWithNoValueAtAllGivesAccurateMessage() {
    // an optional-arg option supplied bare (no value following it at all) is a DIFFERENT situation from a
    // whitespace-only value above - getOptionValue() returns null directly here, not a string that strips
    // to null, so "has a blank value" would misdescribe it just as much as "Missing option" would
    CliHelper ch = new CliHelper(CliHelperTest.class)
        .addOption("d", "directory", "directory", false, "dir", 0, false);
    assertTrue(ch.parse(new String[]{ "-d" }));
    assertTrue(ch.hasOption("d"));
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> ch.getRequiredValue("d"));
    assertEquals("Option 'd' was supplied with no value", ex.getMessage());
  }

  @Test
  void testOptionalArg() {
    // numArgs=0 is documented as meaning the option's argument is optional
    CliHelper ch = new CliHelper(CliHelperTest.class)
        .addOption("n", "number", "a number", false, "n", 0, false);

    assertTrue(ch.parse(new String[]{ "-n", "42" }));
    assertEquals("42", ch.getValue("n"));
  }

  @Test
  void testRequiredMultiArg() {
    CliHelper ch = new CliHelper(CliHelperTest.class)
        .addOption("n", "numbers", "numbers", false, "n", 3, true);

    assertTrue(ch.parse(new String[]{ "-n", "1", "2", "3" }));
    assertEquals(List.of("1", "2", "3"), ch.getValues("n"));
  }

  @Test
  void testGetArgumentsIsUnmodifiable() {
    CliHelper ch = new CliHelper(CliHelperTest.class);

    assertTrue(ch.parse(new String[]{ "foo", "bar" }));
    List args = ch.getArguments();
    assertEquals(List.of("foo", "bar"), args);
    assertThrows(UnsupportedOperationException.class, () -> args.add("baz"));
  }

  @Test
  void testExecuteWithNullReturnDoesNotThrowAndExitsZero() {
    CliHelper ch = new CliHelper(CliHelperTest.class);

    assertTrue(ch.parse(new String[0]));
    // a function returning null must not NPE when auto-unboxed into the exit code
    int exitCode = ch.computeExitCode(new String[0], helper -> null);
    assertEquals(0, exitCode);
  }

  @Test
  void testAddOptionRejectsZeroNumArgsWithArgsRequired() {
    // numArgs=0 is documented as meaning "argument(s) are optional" - pairing it with argsAreRequired=true
    // is self-contradictory (declaring 0 required args) and must fail loudly, not silently produce a
    // zero-argument option that swallows the value the caller clearly intended to require
    CliHelper ch = new CliHelper(CliHelperTest.class);
    assertThrows(IllegalArgumentException.class,
        () -> ch.addOption("o", "opt", "desc", false, "arg", 0, true));
  }

  @Test
  void testAddOptionRejectsNegativeNumArgs() {
    // numArgs is documented as "0 if optional, otherwise the number of expected arguments" - a negative
    // value fits neither case. Left unrejected, Commons CLI's Option.Builder.optionalArg() resets a small
    // negative value back to 1 (masking the bad input), but a more negative one (numArgs <= -3) is passed
    // through as a real negative arg count, which DefaultParser.handleLongOptionWithEqual() then NPEs on for
    // "--opt=value" syntax - an uncaught exception escaping parse() instead of the documented false-return
    // contract
    CliHelper ch = new CliHelper(CliHelperTest.class);
    assertThrows(IllegalArgumentException.class,
        () -> ch.addOption("o", "opt", "desc", false, "arg", -1, false));
    assertThrows(IllegalArgumentException.class,
        () -> ch.addOption("o", "opt", "desc", false, "arg", -3, false));
  }

  @Test
  void testAddOptionWithOptionObjectRejectsInvalidNegativeArgCount() {
    // the negative-numArgs guard above only covers the 7-arg addOption(...) overload, which builds its own
    // Option internally - addOption(Option) takes an ALREADY-BUILT Option, bypassing that guard entirely.
    // Option.Builder.numberOfArgs(-3) is accepted by Commons CLI itself (silently degrading hasArg() to
    // false, the same "silent degradation to a flag" failure mode the other overload's guard exists to
    // prevent) - only -1 (Option.UNINITIALIZED, "no args") and -2 (Option.UNLIMITED_VALUES) are legitimate
    // negative values from Commons CLI's own perspective, so anything more negative must still be rejected
    CliHelper ch = new CliHelper(CliHelperTest.class);
    Option badOption = Option.builder("o").longOpt("opt").desc("desc").numberOfArgs(-3).get();
    assertThrows(IllegalArgumentException.class, () -> ch.addOption(badOption));

    // sanity check: Commons CLI's own legitimate negative sentinels must still be accepted
    Option unlimited = Option.builder("u").longOpt("unl").desc("desc").hasArgs().get();
    ch.addOption(unlimited);
    Option flag = Option.builder("f").longOpt("flag").desc("desc").get();
    ch.addOption(flag);
  }
}
