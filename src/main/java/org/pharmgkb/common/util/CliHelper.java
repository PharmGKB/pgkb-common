package org.pharmgkb.common.util;

import java.io.File;
import java.io.IOException;
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
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.Nullable;


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
  private String m_version;
  /**
   * Shadow collection of options with nothing required so that we can check if help was requested
   * without hitting a parsing exception.
   */
  private final Options m_helpOptions = new Options();
  private final Options m_options = new Options();
  private CommandLine m_commandLine;
  private String m_error;


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
   * Add version option that prints specified {@code version}.
   */
  public CliHelper addVersion(String version) {
    Preconditions.checkArgument(version != null);
    m_version = version;
    return addOption(new Option(sf_versionFlag, sf_versionFlag, false, "print version and exit"));
  }


  public CliHelper addOption(Option option) {

    if (option.getOpt().equals("h") || option.getOpt().equals("v")) {
      throw new IllegalArgumentException("-h and -v are reserved arguments");
    }
    if (option.isRequired()) {
      m_helpOptions.addOption(new Option(option.getOpt(), option.getLongOpt(), option.hasArg(), option.getDescription()));
    } else {
      m_helpOptions.addOption(option);
    }
    m_options.addOption(option);
    return this;
  }

  /**
   * Add a boolean option (aka a flag).
   */
  public CliHelper addOption(String shortName, String longName, String description) {

    if (shortName.equals("h") || shortName.equals("v") || shortName.equals(sf_verboseFlag)) {
      throw new IllegalArgumentException("-h, -v and -version are reserved arguments");
    }
    Option opt = Option.builder(shortName)
        .longOpt(longName)
        .desc(description)
        .hasArg(false)
        .build();
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
   *
   * @param numArgs 0 if argument(s) are optional, otherwise the number of expected arguments
   */
  public CliHelper addOption(String shortName, String longName, String description,
      boolean isOptionRequired, String argName, int numArgs, boolean argsAreRequired) {

    if (shortName.equals("h") || shortName.equals("v")) {
      throw new IllegalArgumentException("-h and -v are reserved arguments");
    }

    m_helpOptions.addOption(buildOption(shortName, longName, description, false, argName, numArgs, argsAreRequired));
    m_options.addOption(buildOption(shortName, longName, description, isOptionRequired, argName, numArgs, argsAreRequired));
    return this;
  }


  private Option buildOption(String shortName, String longName, String description,
      boolean isOptionRequired, String argName, int numArgs, boolean argsAreRequired) {

    Option.Builder optBuilder = Option.builder(shortName)
        .longOpt(longName)
        .desc(description)
        .argName(argName)
        .numberOfArgs(numArgs);

    if (argsAreRequired) {
      optBuilder.hasArg();
    } else {
      optBuilder.optionalArg(true);
    }
    // add non-require variant to help options
    if (isOptionRequired) {
      optBuilder.required();
    }
    return optBuilder.build();
  }


  /**
   * Parses arguments.
   *
   * @return true if parse completed and processing should continue, false if there are missing arguments or help was
   * requested
   */
  public boolean parse(String[] args) {

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

    if (!parse(args)) {
      if (isHelpRequested()) {
        System.exit(0);
      }
      System.exit(1);
    }
    System.exit(function.apply(this));
  }


  /**
   * Checks whether the specified option exists.
   */
  public boolean hasOption(String opt) {
    return m_commandLine.hasOption(opt);
  }

  /**
   * Gets the first String value, if any, for the given option.
   *
   * @param opt the name of the option
   * @return Value of the argument if option is set, and has an argument, otherwise null.
   */
  public @Nullable String getValue(String opt) {
    return StringUtils.stripToNull(m_commandLine.getOptionValue(opt));
  }

  /**
   * Gets the String values for the given option.
   */
  public List<String> getValues(String opt) {
    String[] vals = m_commandLine.getOptionValues(opt);
    if (vals == null) {
      return Collections.emptyList();
    }
    return Lists.newArrayList(vals);
  }

  /**
   * Gets the int value for the given option.
   */
  public int getIntValue(String opt) {
    return Integer.parseInt(m_commandLine.getOptionValue(opt));
  }


  /**
   * Gets the value for the given option as a {@link File}.
   *
   * @param createIfNotExist if true and directory doesn't exist, create the directory;
   * otherwise, if false and directory doesn't exist, throw IllegalArgumentException
   * @return the directory
   * @throws IllegalArgumentException if option was not specified or directory doesn't exist
   */
  public Path getValidDirectory(String opt, boolean createIfNotExist) throws IOException {

    String val = getValue(opt);
    if (val == null) {
      throw new IllegalArgumentException("Missing option '" + opt + "'");
    }
    Path dir = Paths.get(val);
    if (Files.exists(dir)) {
      if (Files.isDirectory(dir)) {
        return dir;
      }
      throw new IOException("Not a valid directory: " + dir);
    } else if (createIfNotExist) {
      Files.createDirectories(dir);
      return dir;
    }
    throw new IllegalArgumentException("No such directory: " + dir);
  }


  /**
   * Gets the value for the given option as a {@link Path}.
   *
   * @throws IllegalArgumentException if option was not specified
   */
  public Path getPath(String opt) {

    String val = getValue(opt);
    if (val == null) {
      throw new IllegalArgumentException("Missing option '" + opt + "'");
    }
    return Paths.get(val);
  }

  /**
   * Gets the value for the given option as a {@link Path}, that must point to an existing file.
   *
   * @throws IllegalArgumentException if file doesn't exist
   */
  public Path getValidFile(String opt, boolean mustExist) {
    Path p = getPath(opt);
    if (!Files.exists(p)) {
      if (mustExist) {
        throw new IllegalArgumentException("File '" + p.toString() + "' does not exist");
      }
    } else {
      if (!Files.isRegularFile(p)) {
        throw new IllegalArgumentException("Not a file: '" + p.toString());
      }
    }
    return p;
  }


  /**
   * Gets remaining parameters.
   */
  public List getArguments() {
    return m_commandLine.getArgList();
  }


  /**
   * Gets whether to operate in verbose mode.
   */
  public boolean isVerbose() {
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
   */
  public void printHelp() {

    HelpFormatter formatter = new HelpFormatter();
    formatter.printHelp(m_name, m_options);
  }
}
