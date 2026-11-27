package io.openaev.docs;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Runs {@code docs/run.py check}. The script chooses the Python of the check, manages its own
 * virtual environment, builds the docs site when it changed and checks that every link of {@code
 * docs/links.json} points to an existing page and anchor. Running it on every local build makes a
 * broken docs link fail on the developer's computer, not only in CI.
 *
 * <p>Only docs problems (exit code 1 of {@code run.py}) fail the build. When the check cannot run
 * (no Python to start run.py with, an environment that cannot be set up, a crash, a timeout), it
 * logs a warning saying why and what to do, and the build goes on: the CI Docs Check job still
 * checks the docs.
 *
 * <p>This goal only starts a Python for run.py: an explicit one ({@code -Dopenaev.docs.python},
 * then the {@code OPENAEV_DOCS_PYTHON} environment variable; a relative path is resolved against
 * the directory Maven runs from), otherwise the first of {@code py -3} (Windows), {@code python3},
 * {@code python} on the PATH that answers {@code --version} as Python 3. run.py then checks the
 * version and the venv module, and starts a newer {@code python3.N} itself when needed (rule in
 * docs/README.md).
 */
@Mojo(name = "docs-check", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true)
public class CheckDocs extends AbstractMojo {

  static final String SKIP_PROPERTY = "openaev.docs.check.skip";
  static final String TIMEOUT_PROPERTY = "openaev.docs.check.timeout";
  static final String PYTHON_PROPERTY = "openaev.docs.python";
  static final String PYTHON_VARIABLE = "OPENAEV_DOCS_PYTHON";
  static final String CI_VARIABLE = "CI";
  static final String RUN_SCRIPT = "run.py";
  static final int EXIT_DOCS_PROBLEMS = 1;
  static final int EXIT_SETUP_FAILED = 4;
  static final long DEFAULT_TIMEOUT_SECONDS = 900;

  /** Lines of output kept to explain a failure when Maven runs quiet ({@code -q}). */
  static final int TAIL_LINES = 20;

  private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(10);

  /** How long the output is still read once the process ended or was stopped. */
  private static final long OUTPUT_DRAIN_MILLIS = 5_000;

  private static final Pattern PYTHON_3_VERSION = Pattern.compile("^Python 3\\.\\d+");

  /**
   * The lines that say why run.py or check.py stopped: their own {@code Docs setup failed} or
   * {@code Docs check failed / could not run} line, and the last line of a Python traceback.
   */
  private static final Pattern FAILURE_LINE =
      Pattern.compile(
          "^(Docs (setup|check) (failed|could not)|[\\w.]*(Error|Exception|Interrupt)(:|$))");

  /** Variables that would make a Python load modules from elsewhere than its venv. */
  private static final List<String> REMOVED_VARIABLES =
      List.of("PYTHONPATH", "PYTHONHOME", "PYTHONSAFEPATH");

  static final String SKIP_HINT = " Or skip the check with -D" + SKIP_PROPERTY + "=true.";
  static final String MESSAGE_SKIPPED = "Docs check skipped (" + SKIP_PROPERTY + "=true)";
  static final String MESSAGE_SKIPPED_CI =
      "Docs check skipped: the "
          + CI_VARIABLE
          + " environment variable is set, and the Docs Check job checks the docs in CI";
  static final String MESSAGE_ALREADY_CHECKED = "Docs already checked in this build";
  static final String MESSAGE_NOT_CHECKED =
      "Docs links NOT checked, the build goes on (the CI Docs Check job still checks them): ";
  static final String MESSAGE_SCRIPT_NOT_FOUND = "the docs check script %s is missing.";
  static final String MESSAGE_PYTHON_NOT_FOUND =
      "no Python 3 was found on the PATH (tried %s). Install Python at the version of"
          + " docs/.python-version or later (with python3-venv on Debian/Ubuntu), or choose one with"
          + " -D"
          + PYTHON_PROPERTY
          + "=<path> or "
          + PYTHON_VARIABLE
          + "=<path>.";
  static final String MESSAGE_STARTING = "Checking docs links: %s (run.py chooses the Python)";
  static final String MESSAGE_DOCS_PROBLEMS =
      "Docs check found problems: fix the docs or docs/links.json as reported above.";
  static final String MESSAGE_SETUP_FAILED =
      "run.py could not set up its Python environment (exit code 4).";
  static final String MESSAGE_TOOLCHAIN_FAILED =
      "the docs check stopped with exit code %d (bad arguments, toolchain failure, crash or"
          + " signal).";
  static final String MESSAGE_TIMED_OUT =
      "the docs check did not finish within %d seconds and was stopped. Raise the limit with -D"
          + TIMEOUT_PROPERTY
          + "=<seconds>.";
  static final String MESSAGE_CANNOT_RUN = "cannot run %s: %s.";
  static final String MESSAGE_OUTPUT = " Its output ended with:";
  static final String MESSAGE_OFFLINE =
      " Maven runs offline (-o), so the docs packages could not be downloaded: run the build once"
          + " online.";
  static final String OUTPUT_PREFIX = "[docs] ";

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

  /** Python to start run.py with: an executable path or a command name, without arguments. */
  @Parameter(property = PYTHON_PROPERTY)
  private String pythonExecutable;

  /** Seconds after which the check is stopped, with a warning. */
  @Parameter(property = TIMEOUT_PROPERTY, defaultValue = "" + DEFAULT_TIMEOUT_SECONDS)
  private long timeoutSeconds;

  @Parameter(defaultValue = "${session.offline}", readonly = true)
  private boolean offline;

  @Parameter(defaultValue = "${session.request.startTime}", readonly = true)
  private Date buildStartTime;

  private final Map<String, String> env;
  private final boolean windows;
  private final Path workingDirectory;

  public CheckDocs() {
    this(null, false, System.getenv(), isWindows(), Path.of("").toAbsolutePath());
  }

  CheckDocs(
      File docsDirectory,
      boolean offline,
      Map<String, String> env,
      boolean windows,
      Path workingDirectory) {
    this.docsDirectory = docsDirectory;
    this.offline = offline;
    this.env = env;
    this.windows = windows;
    this.workingDirectory = workingDirectory;
    this.timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
  }

  void setSkip(boolean skip) {
    this.skip = skip;
  }

  void setPythonExecutable(String pythonExecutable) {
    this.pythonExecutable = pythonExecutable;
  }

  void setTimeoutSeconds(long timeoutSeconds) {
    this.timeoutSeconds = timeoutSeconds;
  }

  void setBuildStartTime(Date buildStartTime) {
    this.buildStartTime = buildStartTime;
  }

  /** The Python that starts run.py, and the value run.py gets in {@code OPENAEV_DOCS_PYTHON}. */
  private record Python(List<String> command, String explicit) {}

  /** How a process ended: its exit code, or a timeout. */
  private record Outcome(int exitCode, boolean timedOut) {}

  /** Why the check could not run; the build goes on with a warning. */
  private static class NotChecked extends Exception {
    private final String output;

    NotChecked(String reason) {
      this(reason, "");
    }

    NotChecked(String reason, String output) {
      super(reason);
      this.output = output;
    }
  }

  public void execute() throws MojoFailureException {
    if (skip) {
      getLog().info(MESSAGE_SKIPPED);
      return;
    }
    if (isCi(env)) {
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
      getLog().warn(MESSAGE_NOT_CHECKED + e.getMessage() + SKIP_HINT + e.output);
    }
  }

  private void check(Path docs) throws NotChecked, MojoFailureException {
    Path script = docs.resolve(RUN_SCRIPT);
    if (!Files.isRegularFile(script)) {
      throw new NotChecked(MESSAGE_SCRIPT_NOT_FOUND.formatted(script));
    }
    Python python = findPython();
    List<String> command = new ArrayList<>(python.command());
    command.add(script.toString());
    if (offline) {
      command.add("--offline");
    }
    command.add("check");
    getLog().info(MESSAGE_STARTING.formatted(String.join(" ", command)));

    Deque<String> tail = new ArrayDeque<>();
    Outcome outcome =
        run(
            command,
            docs,
            childEnv(python.explicit()),
            Duration.ofSeconds(timeoutSeconds),
            line -> {
              log(line);
              keep(tail, line);
            });
    if (outcome.timedOut()) {
      throw new NotChecked(MESSAGE_TIMED_OUT.formatted(timeoutSeconds), explain(tail));
    }
    int exitCode = outcome.exitCode();
    if (exitCode == EXIT_DOCS_PROBLEMS) {
      throw new MojoFailureException(MESSAGE_DOCS_PROBLEMS + SKIP_HINT);
    }
    if (exitCode == EXIT_SETUP_FAILED) {
      throw new NotChecked(MESSAGE_SETUP_FAILED + offlineHint(), explain(tail));
    }
    if (exitCode != 0) {
      throw new NotChecked(
          MESSAGE_TOOLCHAIN_FAILED.formatted(exitCode) + offlineHint(), explain(tail));
    }
  }

  private static boolean isCi(Map<String, String> env) {
    String value = env.get(CI_VARIABLE);
    if (value == null || value.isBlank()) {
      return false;
    }
    String normalized = value.strip().toLowerCase(Locale.ROOT);
    return !normalized.equals("false") && !normalized.equals("0");
  }

  /**
   * The explicit Python, used as given; otherwise the first candidate on the PATH that answers
   * {@code --version} as Python 3, which skips the Windows Store stub (it exits 9009) and a Python
   * 2 {@code python}, on which run.py cannot start.
   */
  private Python findPython() throws NotChecked {
    String explicit = explicitPython();
    if (explicit != null) {
      return new Python(List.of(explicit), explicit);
    }
    String path = getenv(env, "PATH", windows);
    List<List<String>> candidates = new ArrayList<>();
    List<String> tried = new ArrayList<>();
    if (windows) {
      tried.add("py -3");
      findExecutable("py", path, true)
          .ifPresent(py -> candidates.add(List.of(py.toString(), "-3")));
    }
    for (String name : List.of("python3", "python")) {
      tried.add(name);
      findExecutable(name, path, windows)
          .ifPresent(found -> candidates.add(List.of(found.toString())));
    }
    Map<String, String> probeEnv = childEnv(null);
    for (List<String> candidate : candidates) {
      if (answersAsPython3(candidate, probeEnv)) {
        return new Python(candidate, null);
      }
    }
    throw new NotChecked(MESSAGE_PYTHON_NOT_FOUND.formatted(String.join(", ", tried)));
  }

  /**
   * {@code -Dopenaev.docs.python}, then {@code OPENAEV_DOCS_PYTHON}. A value with a path separator
   * is a path, made absolute against the Maven working directory since run.py runs from docs/; a
   * bare command name is left to the PATH.
   */
  private String explicitPython() {
    String value = pythonExecutable;
    if (value == null || value.isBlank()) {
      value = getenv(env, PYTHON_VARIABLE, windows);
    }
    if (value == null || value.isBlank()) {
      return null;
    }
    String explicit = value.strip();
    boolean isPath = explicit.contains("/") || (windows && explicit.contains("\\"));
    return isPath ? workingDirectory.resolve(explicit).normalize().toString() : explicit;
  }

  private boolean answersAsPython3(List<String> python, Map<String, String> probeEnv) {
    List<String> command = new ArrayList<>(python);
    command.add("--version");
    List<String> output = new ArrayList<>();
    try {
      Outcome outcome = run(command, null, probeEnv, PROBE_TIMEOUT, output::add);
      return !outcome.timedOut()
          && outcome.exitCode() == 0
          && output.stream().anyMatch(line -> PYTHON_3_VERSION.matcher(line.strip()).find());
    } catch (NotChecked e) {
      return false;
    }
  }

  /**
   * The environment of the processes this goal starts: the build's, without the variables that
   * would make Python load foreign modules, with UTF-8 output whatever the console code page
   * (Windows), and with the explicit Python named so that run.py does not replace it.
   */
  private Map<String, String> childEnv(String explicitPython) {
    Map<String, String> childEnv = new HashMap<>(env);
    REMOVED_VARIABLES.forEach(name -> childEnv.keySet().removeIf(name::equalsIgnoreCase));
    childEnv.keySet().removeIf(PYTHON_VARIABLE::equalsIgnoreCase);
    if (explicitPython != null) {
      childEnv.put(PYTHON_VARIABLE, explicitPython);
    }
    childEnv.put("PYTHONUTF8", "1");
    return childEnv;
  }

  /**
   * Starts a process, reads its merged output line by line on another thread (so that a full pipe
   * never blocks it), and waits for it at most {@code timeout}; then stops it and its descendants.
   */
  private static Outcome run(
      List<String> command,
      Path directory,
      Map<String, String> childEnv,
      Duration timeout,
      Consumer<String> onLine)
      throws NotChecked {
    ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
    if (directory != null) {
      builder.directory(directory.toFile());
    }
    builder.environment().clear();
    builder.environment().putAll(childEnv);
    Process process;
    try {
      process = builder.start();
    } catch (IOException e) {
      throw new NotChecked(MESSAGE_CANNOT_RUN.formatted(command.get(0), e.getMessage()));
    }
    Thread stopOnShutdown = new Thread(() -> destroyTree(process));
    Runtime.getRuntime().addShutdownHook(stopOnShutdown);
    Thread reader = new Thread(() -> readLines(process, onLine), "docs-check-output");
    reader.setDaemon(true);
    reader.start();
    try {
      process.getOutputStream().close();
      boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
      if (!finished) {
        destroyTree(process);
      }
      reader.join(OUTPUT_DRAIN_MILLIS);
      return finished ? new Outcome(process.exitValue(), false) : new Outcome(-1, true);
    } catch (IOException e) {
      destroyTree(process);
      throw new NotChecked(MESSAGE_CANNOT_RUN.formatted(command.get(0), e.getMessage()));
    } catch (InterruptedException e) {
      destroyTree(process);
      Thread.currentThread().interrupt();
      throw new NotChecked(MESSAGE_CANNOT_RUN.formatted(command.get(0), "interrupted"));
    } finally {
      try {
        Runtime.getRuntime().removeShutdownHook(stopOnShutdown);
      } catch (IllegalStateException e) {
        // The JVM is already shutting down: the hook runs.
      }
    }
  }

  private static void readLines(Process process, Consumer<String> onLine) {
    try (BufferedReader output =
        new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = output.readLine()) != null) {
        onLine.accept(line);
      }
    } catch (IOException e) {
      // The stream closes when the process is stopped: nothing more to read.
    }
  }

  private static void destroyTree(Process process) {
    process.descendants().forEach(ProcessHandle::destroyForcibly);
    process.destroyForcibly();
  }

  private static void keep(Deque<String> tail, String line) {
    synchronized (tail) {
      if (line.isBlank()) {
        return;
      }
      if (tail.size() == TAIL_LINES) {
        tail.removeFirst();
      }
      tail.addLast(line);
    }
  }

  /**
   * The failure lines of the last output, or all of it when none matches, so that the warning still
   * says why with {@code mvn -q}, which hides the output logged at info level.
   */
  private static String explain(Deque<String> tail) {
    List<String> lines;
    synchronized (tail) {
      lines = new ArrayList<>(tail);
    }
    if (lines.isEmpty()) {
      return "";
    }
    List<String> failures =
        lines.stream().filter(line -> FAILURE_LINE.matcher(line.strip()).find()).toList();
    StringBuilder explanation = new StringBuilder(MESSAGE_OUTPUT);
    for (String line : failures.isEmpty() ? lines : failures) {
      explanation.append(System.lineSeparator()).append(OUTPUT_PREFIX).append(line);
    }
    return explanation.toString();
  }

  /**
   * Looks an executable up in a PATH-like list of directories, as the shell would. On Windows only
   * the {@code .exe} file is taken (ProcessBuilder cannot start an extensionless shell shim there),
   * and it only has to exist: the Microsoft Store app execution aliases are reparse points that are
   * neither regular nor executable files to Java, and {@code --version} rejects the stub.
   */
  private static Optional<Path> findExecutable(String name, String searchPath, boolean windows) {
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
  private static String getenv(Map<String, String> env, String name, boolean windows) {
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

  /** Logs a line of the check at the level MkDocs gave it, so problems stand out in the build. */
  private void log(String line) {
    String message = OUTPUT_PREFIX + line;
    String level = line.stripLeading();
    if (level.startsWith("ERROR")) {
      getLog().error(message);
    } else if (level.startsWith("WARNING")) {
      getLog().warn(message);
    } else {
      getLog().info(message);
    }
  }

  private String offlineHint() {
    return offline ? MESSAGE_OFFLINE : "";
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
  }
}
