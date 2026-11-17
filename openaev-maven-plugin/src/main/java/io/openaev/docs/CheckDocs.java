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
 * <p>Only docs problems (exit code 1 of {@code run.py}) fail the build. When the check cannot run
 * (no usable Python, an environment that cannot be set up, a crash), it logs a warning saying why
 * and what to do, and the build goes on: the CI Docs Check job still checks the docs.
 *
 * <p>Python is found with the rule shared by every caller of {@code run.py} (Maven, the front
 * scripts, the docs serve scripts): an explicit choice ({@code -Dopenaev.docs.python}, then the
 * {@code OPENAEV_DOCS_PYTHON} environment variable) is the only one tried; without one, the first
 * usable of {@code py -3} (Windows), {@code python3}, {@code python}, {@code python3.20} down to
 * {@code python3.<minimum>} on the PATH is taken. A Python is usable when it imports {@code venv}
 * and {@code ensurepip} and is at least the version of {@code docs/.python-version}.
 */
@Mojo(name = "docs-check", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true)
public class CheckDocs extends AbstractMojo {

  static final String SKIP_PROPERTY = "openaev.docs.check.skip";
  static final String PYTHON_PROPERTY = "openaev.docs.python";
  static final String PYTHON_VARIABLE = "OPENAEV_DOCS_PYTHON";
  static final String RUN_SCRIPT = "run.py";
  static final String PYTHON_VERSION_FILE = ".python-version";
  static final int EXIT_DOCS_PROBLEMS = 1;
  static final int EXIT_SETUP_FAILED = 4;

  /** Highest {@code python3.N} name tried on the PATH. */
  static final int HIGHEST_MINOR_TRIED = 20;

  /**
   * Checks in one run that a Python can create the docs virtual environment and prints its version:
   * an Ubuntu Python without python3-venv, or the Windows Store stub, fails it.
   */
  static final String PROBE_SCRIPT =
      "import sys, venv, ensurepip; print('%d.%d' % sys.version_info[:2])";

  private static final long PROBE_TIMEOUT_SECONDS = 10;
  private static final Pattern PROBE_OUTPUT = Pattern.compile("^(\\d+)\\.(\\d+)\\s*$");
  private static final Pattern VERSION_FILE = Pattern.compile("^(\\d+)\\.(\\d+)");

  /** Variables that would make the child Python load modules from elsewhere than its venv. */
  private static final List<String> REMOVED_VARIABLES = List.of("PYTHONPATH", "PYTHONHOME");

  static final String SKIP_HINT = " Or skip the check with -D" + SKIP_PROPERTY + "=true.";
  static final String MESSAGE_SKIPPED = "Docs check skipped (" + SKIP_PROPERTY + "=true)";
  static final String MESSAGE_SKIPPED_CI =
      "Docs check skipped: the "
          + Ci.VARIABLE
          + " environment variable is set, and the Docs Check job checks the docs in CI";
  static final String MESSAGE_ALREADY_CHECKED = "Docs already checked in this build";
  static final String MESSAGE_NOT_CHECKED =
      "Docs links NOT checked, the build goes on (the CI Docs Check job still checks them): ";
  static final String MESSAGE_SCRIPT_NOT_FOUND = "the docs check script %s is missing.";
  static final String MESSAGE_VERSION_FILE = "cannot read the minimum Python version from %s.";
  static final String MESSAGE_PYTHON_NOT_FOUND =
      "no Python %s or later with the venv module was found on the PATH (%s). Install one (with"
          + " python3-venv on Debian/Ubuntu), or choose one with -D"
          + PYTHON_PROPERTY
          + "=<path> or "
          + PYTHON_VARIABLE
          + "=<path>.";
  static final String MESSAGE_EXPLICIT_UNUSABLE =
      "%s is not a usable Python: %s. It must be Python %s or later with the venv module (python3-venv"
          + " on Debian/Ubuntu).";
  static final String PROBLEM_NOT_FOUND = "not found";
  static final String PROBLEM_PROBE_FAILED = "it cannot import venv and ensurepip";
  static final String PROBLEM_TOO_OLD = "it is Python %s";
  static final String MESSAGE_USING_PYTHON = "Docs check uses Python %s (%s)";
  static final String MESSAGE_DOCS_PROBLEMS =
      "Docs check found problems: fix the docs or docs/links.json as reported above.";
  static final String MESSAGE_SETUP_FAILED =
      "run.py could not set up its Python environment: see the message above.";
  static final String MESSAGE_TOOLCHAIN_FAILED =
      "the docs check stopped with exit code %d (toolchain failure, crash or signal): see the output"
          + " above.";
  static final String MESSAGE_CANNOT_RUN = "cannot run %s: %s.";
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

  /** A usable Python: the command that starts it and its {@code major.minor} version. */
  record Python(List<String> command, String version) {}

  /** The result of the lookup: the Python found, or why there is none. */
  record Lookup(Python python, String problem) {}

  /** Why the check could not run; the build goes on with a warning. */
  private static class NotChecked extends Exception {
    NotChecked(String reason) {
      super(reason);
    }
  }

  public void execute() throws MojoFailureException {
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
    try {
      check(docs);
    } catch (NotChecked e) {
      getLog().warn(MESSAGE_NOT_CHECKED + e.getMessage() + SKIP_HINT);
    }
  }

  private void check(Path docs) throws NotChecked, MojoFailureException {
    Path script = docs.resolve(RUN_SCRIPT);
    if (!Files.isRegularFile(script)) {
      throw new NotChecked(MESSAGE_SCRIPT_NOT_FOUND.formatted(script));
    }
    int[] minimum = minimumVersion(docs);
    Lookup lookup = findPython(env, pythonExecutable, windows, minimum);
    if (lookup.python() == null) {
      throw new NotChecked(lookup.problem());
    }
    Python python = lookup.python();
    getLog()
        .info(MESSAGE_USING_PYTHON.formatted(python.version(), String.join(" ", python.command())));

    List<String> command = command(python.command(), script, offline);
    getLog().info("Checking docs links: " + String.join(" ", command));
    int exitCode = run(command, docs);
    if (exitCode == EXIT_DOCS_PROBLEMS) {
      throw new MojoFailureException(MESSAGE_DOCS_PROBLEMS + SKIP_HINT);
    }
    if (exitCode == EXIT_SETUP_FAILED) {
      throw new NotChecked(MESSAGE_SETUP_FAILED + offlineHint(offline));
    }
    if (exitCode != 0) {
      throw new NotChecked(MESSAGE_TOOLCHAIN_FAILED.formatted(exitCode) + offlineHint(offline));
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
  static int[] minimumVersion(Path docs) throws NotChecked {
    Path file = docs.resolve(PYTHON_VERSION_FILE);
    try {
      Matcher matcher =
          VERSION_FILE.matcher(Files.readString(file, StandardCharsets.UTF_8).strip());
      if (matcher.find()) {
        return new int[] {Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))};
      }
    } catch (IOException e) {
      // Reported below, as for a file without a version.
    }
    throw new NotChecked(MESSAGE_VERSION_FILE.formatted(file));
  }

  /**
   * Finds the Python to run the check with (see the class comment for the rule). An explicit choice
   * that is not usable is reported, never replaced by another Python.
   */
  static Lookup findPython(
      Map<String, String> env, String pythonProperty, boolean windows, int[] minimum) {
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
      String problem;
      if (executable == null) {
        problem = PROBLEM_NOT_FOUND;
      } else {
        List<String> command = List.of(executable.toString());
        Optional<int[]> version = probe(command);
        if (version.isEmpty()) {
          problem = PROBLEM_PROBE_FAILED;
        } else if (!atLeast(version.get(), minimum)) {
          problem = PROBLEM_TOO_OLD.formatted(text(version.get()));
        } else {
          return new Lookup(new Python(command, text(version.get())), null);
        }
      }
      return new Lookup(null, MESSAGE_EXPLICIT_UNUSABLE.formatted(origin, problem, minimumText));
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
      Optional<int[]> version = probe(candidate);
      if (version.isPresent() && atLeast(version.get(), minimum)) {
        return new Lookup(new Python(candidate, text(version.get())), null);
      }
    }
    String tried =
        candidates.isEmpty()
            ? "none of " + (windows ? "py, " : "") + String.join(", ", names) + " is on it"
            : "unusable: "
                + String.join(", ", candidates.stream().map(c -> String.join(" ", c)).toList());
    return new Lookup(null, MESSAGE_PYTHON_NOT_FOUND.formatted(minimumText, tried));
  }

  private static boolean atLeast(int[] version, int[] minimum) {
    return version[0] > minimum[0] || (version[0] == minimum[0] && version[1] >= minimum[1]);
  }

  private static String text(int[] version) {
    return version[0] + "." + version[1];
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
   * Runs the probe script with a Python: its {@code major.minor} version when it exits 0 within a
   * few seconds and prints it, empty otherwise (no venv or ensurepip module, the Windows Store stub
   * that exits 9009, not a Python at all).
   */
  static Optional<int[]> probe(List<String> python) {
    List<String> command = new ArrayList<>(python);
    command.add("-c");
    command.add(PROBE_SCRIPT);
    Process process;
    try {
      process = new ProcessBuilder(command).redirectErrorStream(true).start();
    } catch (IOException e) {
      return Optional.empty();
    }
    try (InputStream output = process.getInputStream()) {
      process.getOutputStream().close();
      if (!process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        return Optional.empty();
      }
      String text = new String(output.readAllBytes(), StandardCharsets.UTF_8).strip();
      Matcher version = PROBE_OUTPUT.matcher(text);
      if (process.exitValue() != 0 || !version.matches()) {
        return Optional.empty();
      }
      return Optional.of(
          new int[] {Integer.parseInt(version.group(1)), Integer.parseInt(version.group(2))});
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
   * neither regular nor executable files to Java, and the probe rejects the stub.
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

  private int run(List<String> command, Path directory) throws NotChecked {
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
      throw new NotChecked(MESSAGE_CANNOT_RUN.formatted(command.get(0), e.getMessage()));
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
      throw new NotChecked(MESSAGE_CANNOT_RUN.formatted(command.get(0), e.getMessage()));
    } catch (InterruptedException e) {
      destroyTree(process);
      Thread.currentThread().interrupt();
      throw new NotChecked(MESSAGE_CANNOT_RUN.formatted(command.get(0), "interrupted"));
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
