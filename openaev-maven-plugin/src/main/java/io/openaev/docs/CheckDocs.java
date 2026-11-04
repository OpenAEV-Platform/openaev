package io.openaev.docs;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Runs {@code docs/run.py check} with the system Python. The script manages its own virtual
 * environment, builds the docs site when it changed and checks that every link of {@code
 * docs/links.json} points to an existing page and anchor. Running it on every local build makes a
 * broken docs link fail on the developer's computer, not only in CI.
 *
 * <p>Exit codes of {@code run.py}: 0 when the docs are fine (or unchanged since the last successful
 * check), 1 when the check found problems in the docs, 2 on bad arguments, 3 when the toolchain
 * crashed or was stopped by a signal, 4 when its Python environment could not be set up.
 *
 * <p>Python is found with the rule shared by every caller of {@code run.py} (Maven, the front
 * scripts, the docs serve scripts): an explicit choice ({@code -Dopenaev.docs.python}, then the
 * {@code PYTHON} environment variable) must work, otherwise the build fails naming it; without one,
 * the first of {@code py -3} (Windows), {@code python3}, {@code python}, {@code python3.20} down to
 * {@code python3.<minimum>} that answers {@code --version} with at least the minimum version of
 * {@code docs/.python-version} is taken.
 */
@Mojo(name = "docs-check", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true)
public class CheckDocs extends AbstractMojo {

  static final String SKIP_PROPERTY = "openaev.docs.check.skip";
  static final String PYTHON_PROPERTY = "openaev.docs.python";
  static final String PYTHON_VARIABLE = "PYTHON";
  static final String RUN_SCRIPT = "run.py";
  static final String PYTHON_VERSION_FILE = ".python-version";
  static final int EXIT_DOCS_PROBLEMS = 1;
  static final int EXIT_SETUP_FAILED = 4;

  /** Highest {@code python3.N} name tried on the PATH. */
  static final int HIGHEST_MINOR_TRIED = 20;

  private static final long VERSION_PROBE_TIMEOUT_SECONDS = 10;
  private static final Pattern VERSION_OUTPUT = Pattern.compile("Python (\\d+)\\.(\\d+)(\\S*)");
  private static final Pattern VERSION_FILE = Pattern.compile("^(\\d+)\\.(\\d+)");

  /** Variables that would make the child Python load modules from elsewhere than its venv. */
  private static final List<String> REMOVED_VARIABLES = List.of("PYTHONPATH", "PYTHONHOME");

  static final String SKIP_HINT = " Or skip the check with -D" + SKIP_PROPERTY + "=true";
  static final String MESSAGE_SKIPPED = "Docs check skipped (" + SKIP_PROPERTY + "=true)";
  static final String MESSAGE_SKIPPED_CI =
      "Docs check skipped: the "
          + Ci.VARIABLE
          + " environment variable is set, and the Docs Check job checks the docs in CI";
  static final String MESSAGE_ALREADY_CHECKED = "Docs already checked in this build";
  static final String MESSAGE_SCRIPT_NOT_FOUND = "Docs check script not found: ";
  static final String MESSAGE_PYTHON_NOT_FOUND =
      "Python %s or later is needed to check the docs links: install it, set -D"
          + PYTHON_PROPERTY
          + "=<path>, or skip with -D"
          + SKIP_PROPERTY
          + "=true";
  static final String MESSAGE_EXPLICIT_NOT_FOUND = "%s: no such Python executable.";
  static final String MESSAGE_EXPLICIT_NO_VERSION = "%s: `%s --version` failed.";
  static final String MESSAGE_EXPLICIT_TOO_OLD =
      "%s is Python %s, below the %s or later the docs need (docs/" + PYTHON_VERSION_FILE + ").";
  static final String MESSAGE_USING_PYTHON = "Docs check uses Python %s (%s)";
  static final String MESSAGE_DOCS_PROBLEMS =
      "Docs check found problems: fix the docs or docs/links.json as reported above.";
  static final String MESSAGE_SETUP_FAILED =
      "Docs check could not set up its Python environment: see the message above (Python %s or"
          + " later is needed, with python3-venv on Debian/Ubuntu).";
  static final String MESSAGE_TOOLCHAIN_FAILED =
      "Docs check could not run (exit code %d): the docs toolchain failed, or run.py crashed or"
          + " got bad arguments; see the output above.";
  static final String MESSAGE_OFFLINE =
      " Maven runs offline (-o), so the docs packages could not be downloaded: run the build once"
          + " online.";

  /**
   * Builds already checked by this plugin class loader, so that a lifecycle forked by another goal
   * (e.g. {@code spring-boot:run} forks up to {@code test-compile}) does not run the check twice.
   */
  static final Set<String> CHECKED_BUILDS = ConcurrentHashMap.newKeySet();

  @Parameter(property = SKIP_PROPERTY, defaultValue = "false")
  private boolean skip;

  @Parameter(
      property = "openaev.docs.directory",
      required = true,
      defaultValue = "${project.basedir}/../docs")
  private File docsDirectory;

  /** Python to run the check with, instead of the one found on the PATH. */
  @Parameter(property = PYTHON_PROPERTY)
  private String pythonExecutable;

  @Parameter(defaultValue = "${session.offline}", readonly = true)
  private boolean offline;

  @Parameter(defaultValue = "${session.request.startTime}", readonly = true)
  private Date buildStartTime;

  private final Map<String, String> env;
  private final boolean windows;

  public CheckDocs() {
    this(null, false, System.getenv(), isWindows());
  }

  public CheckDocs(File docsDirectory, boolean offline, Map<String, String> env, boolean windows) {
    this.docsDirectory = docsDirectory;
    this.offline = offline;
    this.env = env;
    this.windows = windows;
  }

  void setSkip(boolean skip) {
    this.skip = skip;
  }

  void setPythonExecutable(String pythonExecutable) {
    this.pythonExecutable = pythonExecutable;
  }

  void setBuildStartTime(Date buildStartTime) {
    this.buildStartTime = buildStartTime;
  }

  /** A Python interpreter: the command that starts it and the version it reported. */
  record Python(List<String> command, int major, int minor, String version) {
    boolean atLeast(int[] minimum) {
      return major > minimum[0] || (major == minimum[0] && minor >= minimum[1]);
    }
  }

  public void execute() throws MojoExecutionException, MojoFailureException {
    if (skip) {
      getLog().info(MESSAGE_SKIPPED);
      return;
    }
    if (Ci.isCi(env)) {
      getLog().info(MESSAGE_SKIPPED_CI);
      return;
    }
    Path docs = docsDirectory.toPath().toAbsolutePath().normalize();
    if (buildStartTime != null && !CHECKED_BUILDS.add(buildStartTime.getTime() + "|" + docs)) {
      getLog().info(MESSAGE_ALREADY_CHECKED);
      return;
    }
    Path script = docs.resolve(RUN_SCRIPT);
    if (!Files.isRegularFile(script)) {
      throw new MojoExecutionException(MESSAGE_SCRIPT_NOT_FOUND + script + "." + SKIP_HINT);
    }
    int[] minimum = minimumVersion(docs);
    String minimumText = minimum[0] + "." + minimum[1];
    Python python =
        findPython(env, pythonExecutable, windows, minimum)
            .orElseThrow(
                () -> new MojoExecutionException(MESSAGE_PYTHON_NOT_FOUND.formatted(minimumText)));
    getLog()
        .info(MESSAGE_USING_PYTHON.formatted(python.version(), String.join(" ", python.command())));

    List<String> command = command(python.command(), script, offline);
    getLog().info("Checking docs links: " + String.join(" ", command));
    int exitCode = run(command, docs);
    if (exitCode == EXIT_DOCS_PROBLEMS) {
      throw new MojoFailureException(MESSAGE_DOCS_PROBLEMS + SKIP_HINT);
    }
    if (exitCode == EXIT_SETUP_FAILED) {
      throw new MojoExecutionException(
          MESSAGE_SETUP_FAILED.formatted(minimumText) + offlineHint(offline) + SKIP_HINT);
    }
    if (exitCode != 0) {
      throw new MojoExecutionException(
          MESSAGE_TOOLCHAIN_FAILED.formatted(exitCode) + offlineHint(offline) + SKIP_HINT);
    }
  }

  /** The command of the docs check contract; {@code --offline} keeps pip off the network. */
  static List<String> command(List<String> python, Path script, boolean offline) {
    List<String> command = new ArrayList<>(python);
    command.add(script.toString());
    if (offline) {
      command.add("--offline");
    }
    command.add("check");
    return command;
  }

  /** The minimum Python version, from {@code docs/.python-version} (e.g. {@code 3.11}). */
  static int[] minimumVersion(Path docs) throws MojoExecutionException {
    Path file = docs.resolve(PYTHON_VERSION_FILE);
    try {
      Matcher matcher =
          VERSION_FILE.matcher(Files.readString(file, StandardCharsets.UTF_8).strip());
      if (matcher.find()) {
        return new int[] {Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))};
      }
    } catch (IOException e) {
      throw new MojoExecutionException("Cannot read " + file + "." + SKIP_HINT, e);
    }
    throw new MojoExecutionException(
        file + " must start with the minimum Python version, e.g. 3.11." + SKIP_HINT);
  }

  /**
   * Finds the Python to run the check with (see the class comment for the rule).
   *
   * @throws MojoExecutionException when the explicit choice is not a working Python of the minimum
   *     version: it is never replaced silently by another one
   */
  static Optional<Python> findPython(
      Map<String, String> env, String pythonProperty, boolean windows, int[] minimum)
      throws MojoExecutionException {
    String path = getenv(env, "PATH", windows);
    String minimumText = minimum[0] + "." + minimum[1];
    String variable = getenv(env, PYTHON_VARIABLE, windows);
    String explicit = null;
    String origin = null;
    if (pythonProperty != null && !pythonProperty.isBlank()) {
      explicit = pythonProperty.strip();
      origin = "-D" + PYTHON_PROPERTY + "=" + explicit;
    } else if (variable != null && !variable.isBlank()) {
      explicit = variable.strip();
      origin = PYTHON_VARIABLE + "=" + explicit;
    }
    if (explicit != null) {
      Path executable = resolveExplicit(explicit, path, windows);
      if (executable == null) {
        throw new MojoExecutionException(MESSAGE_EXPLICIT_NOT_FOUND.formatted(origin) + SKIP_HINT);
      }
      Optional<Python> probed = probe(List.of(executable.toString()));
      if (probed.isEmpty()) {
        throw new MojoExecutionException(
            MESSAGE_EXPLICIT_NO_VERSION.formatted(origin, executable) + SKIP_HINT);
      }
      Python python = probed.get();
      if (!python.atLeast(minimum)) {
        throw new MojoExecutionException(
            MESSAGE_EXPLICIT_TOO_OLD.formatted(origin, python.version(), minimumText) + SKIP_HINT);
      }
      return Optional.of(python);
    }

    List<List<String>> candidates = new ArrayList<>();
    if (windows) {
      findExecutable("py", path, true)
          .ifPresent(py -> candidates.add(List.of(py.toString(), "-3")));
    }
    List<String> names = new ArrayList<>(List.of("python3", "python"));
    if (minimum[0] == 3) {
      for (int minor = HIGHEST_MINOR_TRIED; minor >= minimum[1]; minor--) {
        names.add("python3." + minor);
      }
    }
    for (String name : names) {
      findExecutable(name, path, windows)
          .ifPresent(found -> candidates.add(List.of(found.toString())));
    }
    for (List<String> candidate : candidates) {
      Optional<Python> python = probe(candidate);
      if (python.isPresent() && python.get().atLeast(minimum)) {
        return python;
      }
    }
    return Optional.empty();
  }

  private static Path resolveExplicit(String explicit, String path, boolean windows) {
    Path candidate;
    try {
      candidate = Path.of(explicit);
    } catch (RuntimeException e) {
      candidate = null;
    }
    if (candidate != null && isCandidate(candidate, windows)) {
      return candidate;
    }
    return findExecutable(explicit, path, windows).orElse(null);
  }

  /**
   * Runs {@code <python> --version}: empty when it fails, prints no version (the Windows Store stub
   * exits with 9009) or does not answer within a few seconds.
   */
  static Optional<Python> probe(List<String> python) {
    List<String> command = new ArrayList<>(python);
    command.add("--version");
    Process process;
    try {
      process = new ProcessBuilder(command).redirectErrorStream(true).start();
    } catch (IOException e) {
      return Optional.empty();
    }
    try (InputStream output = process.getInputStream()) {
      process.getOutputStream().close();
      if (!process.waitFor(VERSION_PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        return Optional.empty();
      }
      String text = new String(output.readAllBytes(), StandardCharsets.UTF_8);
      Matcher version = VERSION_OUTPUT.matcher(text);
      if (process.exitValue() != 0 || !version.find()) {
        return Optional.empty();
      }
      return Optional.of(
          new Python(
              python,
              Integer.parseInt(version.group(1)),
              Integer.parseInt(version.group(2)),
              version.group(1) + "." + version.group(2) + version.group(3)));
    } catch (IOException e) {
      process.destroyForcibly();
      return Optional.empty();
    } catch (InterruptedException e) {
      process.destroyForcibly();
      Thread.currentThread().interrupt();
      return Optional.empty();
    }
  }

  /**
   * Looks an executable up in a PATH-like list of directories, as the shell would. On Windows only
   * the {@code .exe} file is taken (ProcessBuilder cannot start an extensionless shell shim there),
   * and it only has to exist: the Microsoft Store app execution aliases are reparse points that are
   * neither regular nor executable files to Java, and the {@code --version} probe rejects the stub.
   */
  static Optional<Path> findExecutable(String name, String searchPath, boolean windows) {
    if (searchPath == null || searchPath.isBlank()) {
      return Optional.empty();
    }
    String fileName =
        windows && !name.toLowerCase(Locale.ROOT).endsWith(".exe") ? name + ".exe" : name;
    for (String directory : searchPath.split(File.pathSeparator)) {
      if (directory.isBlank()) {
        continue;
      }
      Path candidate;
      try {
        candidate = Path.of(directory.strip().replace("\"", ""), fileName);
      } catch (RuntimeException e) {
        // Malformed PATH entry: skip it as the shell would.
        continue;
      }
      if (isCandidate(candidate, windows)) {
        return Optional.of(candidate);
      }
    }
    return Optional.empty();
  }

  private static boolean isCandidate(Path candidate, boolean windows) {
    if (windows) {
      return Files.exists(candidate, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(candidate);
    }
    return Files.isRegularFile(candidate) && Files.isExecutable(candidate);
  }

  /**
   * Reads an environment variable. Names are case-insensitive on Windows, where PATH is usually
   * spelled {@code Path}, but the map of {@link System#getenv()} is not.
   */
  static String getenv(Map<String, String> env, String name, boolean windows) {
    String value = env.get(name);
    if (value != null || !windows) {
      return value;
    }
    return env.entrySet().stream()
        .filter(entry -> entry.getKey().equalsIgnoreCase(name))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse(null);
  }

  private int run(List<String> command, Path directory) throws MojoExecutionException {
    ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile());
    builder.redirectErrorStream(true);
    Map<String, String> childEnv = builder.environment();
    REMOVED_VARIABLES.forEach(name -> childEnv.keySet().removeIf(name::equalsIgnoreCase));
    // Python writes its output as UTF-8 whatever the console code page (Windows).
    childEnv.put("PYTHONUTF8", "1");
    Process process;
    try {
      process = builder.start();
    } catch (IOException e) {
      throw new MojoExecutionException("Cannot run " + String.join(" ", command), e);
    }
    // Stops the check (and the pip or MkDocs processes it started) when Maven is interrupted.
    Thread stopCheck = new Thread(() -> destroyTree(process));
    Runtime.getRuntime().addShutdownHook(stopCheck);
    try (BufferedReader output =
        new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      process.getOutputStream().close();
      String line;
      while ((line = output.readLine()) != null) {
        log(line);
      }
      return process.waitFor();
    } catch (IOException e) {
      destroyTree(process);
      throw new MojoExecutionException("Cannot read the output of the docs check", e);
    } catch (InterruptedException e) {
      destroyTree(process);
      Thread.currentThread().interrupt();
      throw new MojoExecutionException("Docs check interrupted", e);
    } finally {
      try {
        Runtime.getRuntime().removeShutdownHook(stopCheck);
      } catch (IllegalStateException e) {
        // The JVM is already shutting down: the hook runs.
      }
    }
  }

  private static void destroyTree(Process process) {
    process.descendants().forEach(ProcessHandle::destroyForcibly);
    process.destroyForcibly();
  }

  /** Logs a line of the check at the level MkDocs gave it, so problems stand out in the build. */
  private void log(String line) {
    String message = "[docs] " + line;
    String level = line.stripLeading();
    if (level.startsWith("ERROR")) {
      getLog().error(message);
    } else if (level.startsWith("WARNING")) {
      getLog().warn(message);
    } else {
      getLog().info(message);
    }
  }

  private static String offlineHint(boolean offline) {
    return offline ? MESSAGE_OFFLINE : "";
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
  }
}
