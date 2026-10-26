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
   * Checks that neither {@code shortName} nor {@code longName} is already in use by another option, as either a
   * short or a long name.
   * <p>
   * Both roles are checked because Commons CLI keys options by {@code Option.getKey()} (the short name if set,
   * otherwise the long name), and resolves {@code --name} against short names before long ones: a name used as one
   * option's short name and another's long name would make one of them unreachable.
   */
  private void checkNotAlreadyRegistered(@Nullable String shortName, @Nullable String longName) {
    if (shortName != null && (m_options.hasShortOption(shortName) || m_options.hasLongOption(shortName))) {
      throw new IllegalArgumentException("Cannot add option: '-" + shortName + "' is already in use by another option");
    }
    if (longName != null && (m_options.hasLongOption(longName) || m_options.hasShortOption(longName))) {
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
   * {@code numArgs}: depending on {@code argsAreRequired}, supplying fewer values either succeeds or fails
   * {@link #parse} (and not monotonically), and positional arguments that follow can be consumed as the option's
   * values. Callers that need an exact count should check {@code getValues(opt).size()} after a successful
   * {@link #parse}.
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
   * Gets the first String value, if any, for the given option. A whitespace-only value is stripped to
   * {@code null}, same as a truly missing one.
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
   * @throws IllegalArgumentException if the option wasn't supplied, was supplied with no value at all, or its
   * value stripped to blank (propagated from {@link #getPath})
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
   * Thrown for an invalid path option. Not named {@code InvalidPathException}, so it can't be confused with
   * {@link java.nio.file.InvalidPathException} in a {@code catch}.
   */
  public static class InvalidCliPathException extends IllegalArgumentException {
    InvalidCliPathException(String msg) {
      super(msg);
    }
  }
}
