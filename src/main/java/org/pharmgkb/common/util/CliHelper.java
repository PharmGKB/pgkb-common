package org.pharmgkb.common.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.help.HelpFormatter;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;


/**
 * This is a helper class for command line utilities to deal with command line arguments.
 *
 * @author Mark Woon
 */
public class CliHelper {
  private static final String sf_helpFlag = "help";
  private static final String sf_verboseFlag = "verbose";
  private static final String sf_versionFlag = "version";
  private final String m_name;
  private @Nullable String m_version;
  /**
   * Shadow collection of options with nothing required so that we can check if help was requested
   * without hitting a parsing exception.
   */
  private final Options m_helpOptions = new Options();
  private final Options m_options = new Options();
  private @Nullable CommandLine m_commandLine;
  private @Nullable String m_error;


  /**
   * Standard constructor.
   *
   * @param cls the class with the main() method
   */
  public CliHelper(Class cls) {

    m_name = cls.getSimpleName();

    Option opt = new Option("h", sf_helpFlag, false, "print this message");
    m_helpOptions.addOption(opt);
    m_options.addOption(opt);

    opt = new Option("v", sf_verboseFlag, false, "enable verbose output");
    m_helpOptions.addOption(opt);
    m_options.addOption(opt);
  }


  /**
   * Adds the version option that prints specified {@code version}.
   */
  public CliHelper addVersion(String version) {
    Preconditions.checkArgument(version != null);
    if (m_version != null) {
      // distinct from the "another option" collision below - addVersion() itself already registered
      // "version", so blaming "another option" would misattribute the collision to something that doesn't
      // exist
      throw new IllegalArgumentException("addVersion() has already been called");
    }
    if (m_options.getOption(sf_versionFlag) != null) {
      throw new IllegalArgumentException("Cannot add version option: '-version'/'--version' is already in use " +
          "by another option");
    }
    m_version = version;
    // bypasses the reserved-argument check since this is the sole legitimate registrar of "version"
    return addOptionInternal(new Option(sf_versionFlag, sf_versionFlag, false, "print version and exit"), false);
  }


  public CliHelper addOption(Option option) {
    return addOptionInternal(option, true);
  }

  private CliHelper addOptionInternal(Option option, boolean checkReserved) {

    if (checkReserved && isReserved(option.getOpt(), option.getLongOpt())) {
      throw new IllegalArgumentException(reservedArgsMessage());
    }
    checkNotAlreadyRegistered(option.getOpt(), option.getLongOpt());
    // the other addOption(...) overload's negative-numArgs guard doesn't cover this one, since this one
    // takes an ALREADY-BUILT Option - Commons CLI itself accepts Option.Builder.numberOfArgs(-3), silently
    // degrading hasArg() to false (same "silent degradation to a flag" failure mode that guard exists to
    // prevent). Option.UNINITIALIZED (-1, "no args") and Option.UNLIMITED_VALUES (-2) are Commons CLI's own
    // legitimate negative values and must stay accepted - only anything more negative is a real bug.
    Preconditions.checkArgument(option.getArgs() >= Option.UNLIMITED_VALUES,
        "numArgs must be >= %s (Option.UNLIMITED_VALUES)", Option.UNLIMITED_VALUES);
    // the help-options clone must never require an argument (regardless of the real option's requirements),
    // so -h/-version can always be detected even if another, incomplete option is present. But if the real
    // option accepts an argument, the clone must accept the same shape of argument too (as optional), or
    // Commons CLI's lenient first pass rejects "--opt=value"/"-o=value"/"-Dkey=value" syntax as an
    // unrecognized option entirely (numberOfArgs is what's load-bearing here, not just hasArg - mirroring
    // valueSeparator too is harmless but not actually required, since this clone's parsed values are never
    // read, only whether parsing succeeds).
    Option.Builder helpOptBuilder = Option.builder(option.getOpt())
        .longOpt(option.getLongOpt())
        .desc(option.getDescription());
    if (option.hasArg()) {
      helpOptBuilder.numberOfArgs(option.getArgs()).optionalArg(true);
      if (option.getValueSeparator() != 0) {
        helpOptBuilder.valueSeparator(option.getValueSeparator());
      }
    }
    m_helpOptions.addOption(helpOptBuilder.get());
    m_options.addOption(option);
    return this;
  }

  /**
   * Checks if {@code shortName} or {@code longName} collides with a reserved argument (-h/--help, -v/--verbose, or
   * -version/--version if {@link #addVersion(String)} has been called).
   */
  private boolean isReserved(@Nullable String shortName, @Nullable String longName) {
    // Commons CLI doesn't require short opts to be 1 character (or long opts to be more than 1), so each
    // reserved form ("h"/"v" and "help"/"verbose") must be checked against BOTH shortName and longName -
    // otherwise a user's own option collides with hasOption()/isHelpRequested()/isVerbose() (which match by
    // either short or long opt) without ever being rejected here
    if ("h".equals(shortName) || "h".equals(longName) || "v".equals(shortName) || "v".equals(longName) ||
        sf_helpFlag.equals(shortName) || sf_helpFlag.equals(longName) ||
        sf_verboseFlag.equals(shortName) || sf_verboseFlag.equals(longName)) {
      return true;
    }
    return m_version != null && (sf_versionFlag.equals(shortName) || sf_versionFlag.equals(longName));
  }

  /**
   * Builds an error message describing the arguments that are currently reserved.
   */
  private String reservedArgsMessage() {
    return m_version == null
        ? "-h, -v, --help and --verbose are reserved arguments"
        : "-h, -v, --help, --verbose, -version and --version are reserved arguments";
  }

  /**
   * Checks that {@code shortName} (or, for a long-only option, {@code longName} itself) isn't already in use
   * as another option's effective key, and that {@code longName} isn't already in use as another option's
   * long name.
   * <p>
   * Commons CLI's {@code Options} keys its internal "short opts" map by {@code Option.getKey()} - the short
   * name if one is set, otherwise the long name - for EVERY option, not just ones that actually have a short
   * name. So a long-only option (e.g. {@code longName="d"}, no short) occupies the exact same map slot as a
   * short-only option with {@code shortName="d"}; whichever is added second silently overwrites the first
   * one's entry there, and a later generic lookup by {@code "d"} (e.g. {@code getOptionValue("d")}) then
   * resolves to the wrong option - even though the value was correctly parsed against the right one at parse
   * time. This is why the check below uses {@code shortName != null ? shortName : longName} (mirroring
   * {@code getKey()}) against {@link Options#hasShortOption}, not just {@code shortName} - checking only
   * {@code shortName} would miss exactly this long-only-vs-short-only collision. A short name that also
   * happens to equal a DIFFERENT option's long name is intentionally NOT rejected here, since existing
   * chosen behavior depends on it ({@code "version"} usable as one option's short name while a different
   * option separately uses it as a long name, when {@link #addVersion} was never called to reserve it). But
   * this case is NOT actually safe to parse against, only safe to register: {@code Options.getOption()} - and
   * so the real parser, via {@code DefaultParser}'s long-option handling - checks the short-opts map before
   * the long-opts map, so {@code --<thatLongName>} on the command line always resolves to the option whose
   * SHORT name matches, never to the option whose LONG name matches. The second option's own long form is
   * then unreachable, and if that option is required, parsing fails with "Missing required option" even
   * though the user supplied it. Left unfixed because zero real callers register colliding names like this
   * (verified against all real PharmGKB/PharmCAT {@code CliHelper} usage), but do not rely on this being
   * a safe pattern to introduce - it isn't.
   */
  private void checkNotAlreadyRegistered(@Nullable String shortName, @Nullable String longName) {
    String key = shortName != null ? shortName : longName;
    if (key != null && m_options.hasShortOption(key)) {
      throw new IllegalArgumentException("Cannot add option: '" +
          (shortName != null ? "-" + shortName : "--" + longName) + "' is already in use by another option");
    }
    if (longName != null && m_options.hasLongOption(longName)) {
      throw new IllegalArgumentException("Cannot add option: '--" + longName +
          "' is already in use by another option");
    }
  }

  /**
   * Add a boolean option (aka a flag).
   */
  public CliHelper addOption(String shortName, String longName, String description) {

    if (isReserved(shortName, longName)) {
      throw new IllegalArgumentException(reservedArgsMessage());
    }
    checkNotAlreadyRegistered(shortName, longName);
    Option opt = Option.builder(shortName)
        .longOpt(longName)
        .desc(description)
        .hasArg(false)
        .get();
    m_helpOptions.addOption(opt);
    m_options.addOption(opt);
    return this;
  }

  /**
   * Adds an option that takes a required argument.
   */
  public CliHelper addOption(String shortName, String longName, String description,
      boolean isOptionRequired, String argName) {
    return addOption(shortName, longName, description, isOptionRequired, argName, 1, true);
  }

  /**
   * Adds an option that takes arguments.
   * <p>
   * For {@code numArgs > 1}, Commons CLI only guarantees that at least one value was supplied, not exactly
   * {@code numArgs}, and under-supply behaves differently depending on {@code argsAreRequired}: with
   * {@code argsAreRequired = false}, a caller can supply fewer values than {@code numArgs} and {@link #parse}
   * still succeeds, leaving the option's remaining slots simply absent from {@link #getValues}; with
   * {@code argsAreRequired = true}, under-supply can instead make {@link #parse} fail outright with "Missing
   * argument for option" - and, counter-intuitively, NOT monotonically: verified empirically that supplying
   * {@code numArgs - 1} values fails while supplying fewer still (e.g. just 1) succeeds. Either way, if
   * positional arguments follow, those can be silently consumed as if they were the option's own values
   * instead of being left for {@link #getArguments}. Callers that need to guarantee an exact count should
   * check {@code getValues(opt).size() == numArgs} themselves after a successful {@link #parse}.
   *
   * @param numArgs 0 if argument(s) are optional, otherwise the number of expected arguments
   */
  public CliHelper addOption(String shortName, String longName, String description,
      boolean isOptionRequired, String argName, int numArgs, boolean argsAreRequired) {

    if (isReserved(shortName, longName)) {
      throw new IllegalArgumentException(reservedArgsMessage());
    }
    checkNotAlreadyRegistered(shortName, longName);
    // numArgs is documented as "0 if optional, otherwise the number of expected arguments" - a negative
    // value fits neither case. Left unrejected, Commons CLI either silently resets a small negative value
    // back to 1 (Option.Builder.optionalArg()) or passes a more negative one through as a real negative arg
    // count, which DefaultParser NPEs on for "--opt=value" syntax instead of parse()'s documented
    // false-return contract
    Preconditions.checkArgument(numArgs >= 0, "numArgs must be >= 0");
    // numArgs=0 is documented as meaning "argument(s) are optional" - pairing it with argsAreRequired=true
    // is self-contradictory (0 required args) and would otherwise silently produce a zero-argument option
    Preconditions.checkArgument(numArgs > 0 || !argsAreRequired,
        "numArgs must be > 0 when argsAreRequired is true");

    // the help-options clone must never require an argument (regardless of numArgs/argsAreRequired), so
    // -h/-version can always be detected even if another, incomplete option is present. But it must still
    // accept the same number of optional arguments (this overload is always for an arg-taking option), or
    // Commons CLI's lenient first pass rejects "--opt=value"/"-o=value" syntax - or a numArgs>1 option's
    // multi-value syntax - as an unrecognized option entirely.
    m_helpOptions.addOption(Option.builder(shortName).longOpt(longName).desc(description)
        .numberOfArgs(numArgs == 0 ? 1 : numArgs).optionalArg(true).get());
    m_options.addOption(buildOption(shortName, longName, description, isOptionRequired, argName, numArgs, argsAreRequired));
    return this;
  }


  private Option buildOption(String shortName, String longName, String description,
      boolean isOptionRequired, String argName, int numArgs, boolean argsAreRequired) {

    Option.Builder optBuilder = Option.builder(shortName)
        .longOpt(longName)
        .desc(description)
        .argName(argName);

    if (argsAreRequired) {
      optBuilder.numberOfArgs(numArgs);
    } else {
      // numArgs=0 is documented as "optional"; Commons CLI needs an explicit positive arg count for
      // optionalArg(true) to actually take effect, otherwise the option never captures its value
      optBuilder.numberOfArgs(numArgs == 0 ? 1 : numArgs).optionalArg(true);
    }
    // add non-require variant to help options
    if (isOptionRequired) {
      optBuilder.required();
    }
    return optBuilder.get();
  }


  /**
   * Parses arguments.
   * <p>
   * On failure (a {@code false} return, other than for a help/version request), the command line is left
   * unset - every accessor below that requires a successful parse ({@link #hasOption}, {@link #getValue},
   * {@link #getValues}, {@link #getArguments}, {@link #isVerbose}) then throws {@link IllegalStateException}
   * rather than returning stale or partial data.
   *
   * @return true if parse completed and processing should continue, false if there are missing arguments or
   * help or version was requested
   */
  public boolean parse(String[] args) {

    m_error = null;
    m_commandLine = null;
    try {
      CommandLineParser parser = new DefaultParser();
      // check for -h
      m_commandLine = parser.parse(m_helpOptions, args);
      if (isHelpRequested()) {
        printHelp();
        return false;
      }
      if (isVersionRequested()) {
        System.out.println(m_version);
        return false;
      }
      parser = new DefaultParser();
      m_commandLine = parser.parse(m_options, args);
      return true;

    } catch (org.apache.commons.cli.ParseException ex) {
      // don't leave m_commandLine pointing at an earlier, successful pass's stale/lenient result
      m_commandLine = null;
      m_error = ex.getMessage();
      System.err.println(m_error);
      System.err.println();
      printHelp();
      return false;
    }
  }

  /**
   * Parse arguments and execute the function.
   * This helps enforce proper exit codes.
   */
  public void execute(String[] args, Function<CliHelper, Integer> function) {
    System.exit(computeExitCode(args, function));
  }

  /**
   * Computes the exit code for {@link #execute(String[], Function)} without actually exiting the JVM, so that
   * this logic can be tested.
   *
   * @param function the function to run; if it returns null, this is treated as a successful exit code of 0
   */
  int computeExitCode(String[] args, Function<CliHelper, Integer> function) {

    if (!parse(args)) {
      if (isHelpRequested() || isVersionRequested()) {
        return 0;
      }
      return 1;
    }
    Integer exitCode = function.apply(this);
    return exitCode == null ? 0 : exitCode;
  }


  /**
   * Checks whether the specified option exists.
   *
   * @throws IllegalStateException if called before {@link #parse} or after a failed parse
   */
  public boolean hasOption(String opt) {
    Preconditions.checkState(m_commandLine != null, "Command line has not been parsed");
    return m_commandLine.hasOption(opt);
  }

  /**
   * Gets the first String value, if any, for the given option.
   *
   * @param opt the name of the option
   * @return Value of the argument if the option is set and has an argument, otherwise null.
   * @throws IllegalStateException if called before {@link #parse} or after a failed parse
   */
  public @Nullable String getValue(String opt) {
    Preconditions.checkState(m_commandLine != null, "Command line has not been parsed");
    return StringUtils.stripToNull(m_commandLine.getOptionValue(opt));
  }

  /**
   * Gets the String values for the given option.
   * @return returns a List of the specified values, empty list if no options specified
   * @throws IllegalStateException if called before {@link #parse} or after a failed parse
   */
  public List<String> getValues(String opt) {
    Preconditions.checkState(m_commandLine != null, "Command line has not been parsed");
    String[] vals = m_commandLine.getOptionValues(opt);
    if (vals == null) {
      return Collections.emptyList();
    }
    return Lists.newArrayList(vals);
  }

  /**
   * Builds the exception for a required value that {@link #getValue} returned null for - distinguishing 3
   * different situations that all collapse to that same null: "option not supplied at all", "option supplied
   * with no value at all" (e.g. an optional-arg option given bare, like {@code -d} alone), and "option
   * supplied, but its value stripped to blank" ({@link #getValue} strips a whitespace-only value to null the
   * same as either of the above) - only the first is accurately described as "missing."
   */
  private IllegalArgumentException missingValueException(String opt) {
    if (hasOption(opt)) {
      if (m_commandLine.getOptionValue(opt) == null) {
        return new IllegalArgumentException("Option '" + opt + "' was supplied with no value");
      }
      return new IllegalArgumentException("Option '" + opt + "' has a blank value");
    }
    return new IllegalArgumentException("Missing option '" + opt + "'");
  }

  /**
   * Gets the first String value for the given option, which must be present.
   *
   * @throws IllegalArgumentException if the option wasn't supplied, was supplied with no value at all, or its
   * value stripped to blank (see {@link #getValue})
   */
  public String getRequiredValue(String opt) {
    String val = getValue(opt);
    if (val == null) {
      throw missingValueException(opt);
    }
    return val;
  }


  /**
   * Gets the int value for the given option.
   *
   * @throws IllegalArgumentException if the option wasn't supplied, was supplied with no value at all, or its
   * value stripped to blank (see {@link #getValue})
   * @throws NumberFormatException if the value is not a parseable int
   */
  public int getIntValue(String opt) {
    String val = getValue(opt);
    if (val == null) {
      throw missingValueException(opt);
    }
    return Integer.parseInt(val);
  }


  /**
   * Gets the value for the given option as a {@link Path}.
   *
   * @param createIfNotExist if true and the directory doesn't exist, create the directory;
   * otherwise, if false and the directory doesn't exist, throw InvalidCliPathException
   * @return the directory
   * @throws IllegalArgumentException if the option wasn't supplied, was supplied with no value at all, or its
   * value stripped to blank (see {@link #getValue})
   * @throws InvalidCliPathException if the specified path is not a directory or {@code createIfNotExist} is false and
   * directory doesn't exist
   */
  public Path getValidDirectory(String opt, boolean createIfNotExist) throws IOException {

    String val = getValue(opt);
    if (val == null) {
      throw missingValueException(opt);
    }
    Path dir = Paths.get(val);
    if (Files.exists(dir)) {
      if (Files.isDirectory(dir)) {
        return dir;
      }
      throw new InvalidCliPathException("Not a valid directory: " + dir);
    } else if (createIfNotExist) {
      Files.createDirectories(dir);
      return dir;
    }
    throw new InvalidCliPathException("No such directory: " + dir);
  }


  /**
   * Gets the value for the given option as a {@link Path}.
   *
   * @throws IllegalArgumentException if the option wasn't supplied, was supplied with no value at all, or its
   * value stripped to blank (see {@link #getValue})
   */
  public Path getPath(String opt) {

    String val = getValue(opt);
    if (val == null) {
      throw missingValueException(opt);
    }
    return Paths.get(val);
  }

  /**
   * Gets the value for the given option as a {@link Path}, that must point to an existing file.
   *
   * @throws InvalidCliPathException if {@code mustExist} is true and the file doesn't exist, or if the path exists but
   * is not a regular file
   */
  public Path getValidFile(String opt, boolean mustExist) throws InvalidCliPathException {
    Path p = getPath(opt);
    if (!Files.exists(p)) {
      if (mustExist) {
        throw new InvalidCliPathException("File '" + p + "' does not exist");
      }
    } else {
      if (!Files.isRegularFile(p)) {
        throw new InvalidCliPathException("Not a file: '" + p + "'");
      }
    }
    // parent can be null if the path has no dir info (e.g. "foo.txt" vs. "./foo.txt")
    if (p.getParent() == null) {
      p = p.toAbsolutePath();
    }
    return p;
  }


  /**
   * Gets remaining parameters. The returned list is unmodifiable.
   *
   * @throws IllegalStateException if called before {@link #parse} or after a failed parse
   */
  public List getArguments() {
    Preconditions.checkState(m_commandLine != null, "Command line has not been parsed");
    return Collections.unmodifiableList(m_commandLine.getArgList());
  }


  /**
   * Gets whether to operate in verbose mode.
   *
   * @throws IllegalStateException if called before {@link #parse} or after a failed parse
   */
  public boolean isVerbose() {
    Preconditions.checkState(m_commandLine != null, "Command line has not been parsed");
    return m_commandLine.hasOption(sf_verboseFlag);
  }


  /**
   * Checks whether the arguments were parsed successfully.
   */
  public boolean hasError() {
    return m_error != null;
  }

  /**
   * Gets the error that occurred while parsing arguments.
   */
  public @Nullable String getError() {
    return m_error;
  }


  /**
   * Gets whether help on command line arguments has been requested.
   */
  public boolean isHelpRequested() {
    return m_commandLine != null && m_commandLine.hasOption(sf_helpFlag);
  }

  public boolean isVersionRequested() {
    return m_version != null && m_commandLine != null && m_commandLine.hasOption(sf_versionFlag);
  }


  /**
   * Prints the help message.
   *
   * @throws UncheckedIOException if writing the help output fails
   */
  public void printHelp() {

    // showSince defaults to true, adding a "Since" column populated from Option.getSince() - nothing in
    // this codebase ever sets that, so every row would show a meaningless "--" filler with no way to ever
    // populate it. main's old HelpFormatter never had such a column at all.
    HelpFormatter formatter = HelpFormatter.builder().setShowSince(false).get();
    try {
      formatter.printHelp(m_name, null, m_options, null, true);
    } catch (IOException ex) {
      // the new commons-cli help API models output as a checked-exception-producing HelpAppendable, but
      // this formatter's default HelpAppendable just writes to System.out - not expected to fail in practice
      throw new UncheckedIOException(ex);
    }
  }


  /**
   * Deliberately not named {@code InvalidPathException} - that name collides with
   * {@link java.nio.file.InvalidPathException}, and since both are unchecked, a caller that already has
   * {@code java.nio.file.InvalidPathException} imported can write {@code catch (InvalidPathException ex)}
   * around a call to {@link #getValidFile}/{@link #getValidDirectory} with no compiler warning, silently
   * catching the wrong type.
   */
  public static class InvalidCliPathException extends IllegalArgumentException {
    InvalidCliPathException(String msg) {
      super(msg);
    }
  }
}
