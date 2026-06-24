package io.openaev.config;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;

/**
 * Starts (or reuses) a PostgreSQL container before the application context is created, then
 * overrides {@code spring.datasource.url} and {@code spring.flyway.url} to point to it.
 *
 * <p>Activated by setting {@code openaev.dev.auto-start-database=true} in any property source
 * (typically {@code application-dev.properties}).
 *
 * <p>The container name is derived from the current Git branch so that each branch gets its own
 * isolated database. Switching back to a branch reuses the existing (stopped) container instead of
 * recreating it from scratch.
 *
 * <p>Credentials are read from {@code spring.datasource.username} and {@code
 * spring.datasource.password} so they stay consistent with the rest of the configuration.
 *
 * <p>On JVM shutdown the container is <b>stopped</b> (not removed) so it can be restarted later.
 *
 * <p>Supports both Docker and Podman. The runtime is auto-detected (Podman first, then Docker) or
 * can be forced via {@code openaev.dev.container-runtime=podman|docker}.
 *
 * <p><b>This file is NOT versioned in openaev-api.</b> It lives in {@code
 * openaev-dev/test-containers/} and is copied by {@code openaev-dev/setup-auto-db.sh}.
 */
public class DevDatabaseEnvironmentPostProcessor implements EnvironmentPostProcessor {

  private static final String ENABLED_PROPERTY = "openaev.dev.auto-start-database";
  private static final String PORT_PROPERTY = "openaev.dev.database-port";
  private static final String RUNTIME_PROPERTY = "openaev.dev.container-runtime";
  private static final String CONTAINER_PREFIX = "openaev-db-";
  private static final String PG_IMAGE = "postgres:17-alpine";
  private static final String DB_NAME = "openaev";
  private static final int READINESS_TIMEOUT_SECONDS = 30;
  private static final int PORT_RANGE_START = 10000;
  private static final int PORT_RANGE_END = 65536; // exclusive

  /** The resolved container runtime command ({@code "podman"} or {@code "docker"}). */
  private String runtime;

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {

    if (!"true".equalsIgnoreCase(environment.getProperty(ENABLED_PROPERTY))) {
      return;
    }

    if (!environment.acceptsProfiles(Profiles.of("dev"))) {
      log("Auto-start database is enabled, but active profile is not 'dev' — skipping.");
      return;
    }

    runtime = resolveContainerRuntime(environment);

    String dbUser = environment.getProperty("spring.datasource.username", "openaev");
    String dbPassword = environment.getProperty("spring.datasource.password", "openaev");

    String branch = detectGitBranch();
    String containerName = CONTAINER_PREFIX + sanitize(branch);

    int port = resolvePort(environment, containerName);

    log(
        "Auto-start database enabled — runtime: "
            + runtime
            + ", branch: "
            + branch
            + ", container: "
            + containerName
            + ", port: "
            + port);

    try {
      ensureContainerRunning(containerName, dbUser, dbPassword, port);
      waitForReady(containerName, dbUser, port);

      String jdbcUrl =
          "jdbc:postgresql://localhost:"
              + port
              + "/"
              + DB_NAME
              + "?reWriteBatchedInserts=true&application-name=OpenAEV-AutoDB";

      Map<String, Object> props = new HashMap<>();
      props.put("spring.datasource.url", jdbcUrl);
      props.put("spring.flyway.url", jdbcUrl);
      // username/password are already set in application-dev.properties — no need to override

      // Highest precedence — overrides everything from application-*.properties
      environment.getPropertySources().addFirst(new MapPropertySource("autoStartDatabase", props));

      log("Database ready → " + jdbcUrl);

      registerShutdownHook(containerName);

    } catch (Exception e) {
      throw new IllegalStateException("Failed to auto-start database container", e);
    }
  }

  // ---------------------------------------------------------------------------
  // Container lifecycle
  // ---------------------------------------------------------------------------

  /**
   * Ensures the container is running. Three cases:
   *
   * <ol>
   *   <li>Container is already running → no-op
   *   <li>Container exists but is stopped → start it
   *   <li>Container does not exist → create and start it
   * </ol>
   */
  private void ensureContainerRunning(
      String containerName, String dbUser, String dbPassword, int hostPort) throws Exception {

    String state = inspectContainerState(containerName);

    switch (state) {
      case "running" -> log("Container " + containerName + " is already running.");
      case "exited", "created" -> {
        log("Starting existing container " + containerName + "…");
        int rc = exec(runtime, "start", containerName);
        if (rc != 0) {
          throw new IllegalStateException(runtime + " start failed (exit " + rc + ")");
        }
      }
      default -> {
        // "not_found" or unexpected state — (re)create
        log("Creating new container " + containerName + "…");
        exec(runtime, "rm", "-f", containerName); // clean up if in a weird state
        int rc =
            exec(
                runtime,
                "run",
                "-d",
                "--name",
                containerName,
                "-p",
                hostPort + ":5432", // deterministic port derived from container name
                "-e",
                "POSTGRES_DB=" + DB_NAME,
                "-e",
                "POSTGRES_USER=" + dbUser,
                "-e",
                "POSTGRES_PASSWORD=" + dbPassword,
                PG_IMAGE);
        if (rc != 0) {
          throw new IllegalStateException(
              runtime + " run failed (exit " + rc + "). Is " + runtime + " running?");
        }
      }
    }
  }

  /** Returns the container state ({@code running}, {@code exited}, …) or {@code not_found}. */
  private String inspectContainerState(String containerName) throws Exception {
    ProcessBuilder pb =
        new ProcessBuilder(runtime, "inspect", "-f", "{{.State.Status}}", containerName);
    pb.redirectErrorStream(true);
    Process process = pb.start();
    String output;
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(process.getInputStream()))) {
      output = reader.readLine();
    }
    boolean finished = process.waitFor(5, TimeUnit.SECONDS);
    int exitCode = finished ? process.exitValue() : -1;

    if (exitCode != 0 || output == null || output.isBlank() || output.contains("Error")) {
      return "not_found";
    }
    return output.trim().toLowerCase();
  }

  // ---------------------------------------------------------------------------
  // Git branch detection
  // ---------------------------------------------------------------------------

  /** Reads the current branch name from {@code git rev-parse --abbrev-ref HEAD}. */
  private String detectGitBranch() {
    try {
      ProcessBuilder pb = new ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD");
      pb.redirectErrorStream(true);
      Process process = pb.start();
      String output;
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        output = reader.readLine();
      }
      boolean finished = process.waitFor(5, TimeUnit.SECONDS);
      if (finished && process.exitValue() == 0 && output != null && !output.isBlank()) {
        return output.trim();
      }
    } catch (Exception ignored) {
      // fall through
    }
    log("Could not detect Git branch — falling back to 'unknown'");
    return "unknown";
  }

  /**
   * Turns a branch name into a valid container name suffix. Container names must match {@code
   * [a-zA-Z0-9][a-zA-Z0-9_.-]}.
   */
  private static String sanitize(String branch) {
    return branch
        .replaceAll("[^a-zA-Z0-9._-]", "-") // replace invalid chars with dash
        .replaceAll("-{2,}", "-") // collapse multiple dashes
        .replaceAll("^-|-$", ""); // strip leading/trailing dashes
  }

  // ---------------------------------------------------------------------------
  // Container helpers
  // ---------------------------------------------------------------------------

  /**
   * Resolves the container runtime to use. If {@value RUNTIME_PROPERTY} is set, uses that value
   * (must be {@code "podman"} or {@code "docker"}). Otherwise auto-detects: prefers {@code podman},
   * falls back to {@code docker}.
   *
   * @throws IllegalStateException if neither runtime is available
   */
  private static String resolveContainerRuntime(ConfigurableEnvironment environment) {
    String explicit = environment.getProperty(RUNTIME_PROPERTY);
    if (explicit != null && !explicit.isBlank()) {
      String normalized = explicit.trim().toLowerCase();
      if (!"podman".equals(normalized) && !"docker".equals(normalized)) {
        throw new IllegalStateException(
            "Invalid value for "
                + RUNTIME_PROPERTY
                + ": '"
                + explicit
                + "' — expected 'podman' or 'docker'");
      }
      log("Using container runtime from " + RUNTIME_PROPERTY + ": " + normalized);
      return normalized;
    }
    // Auto-detect: prefer podman, fall back to docker
    if (isCommandAvailable("podman")) {
      log("Auto-detected container runtime: podman");
      return "podman";
    }
    if (isCommandAvailable("docker")) {
      log("Auto-detected container runtime: docker");
      return "docker";
    }
    throw new IllegalStateException(
        "Neither 'podman' nor 'docker' found on PATH. "
            + "Install one of them or set "
            + RUNTIME_PROPERTY);
  }

  /** Returns {@code true} if the given command is available on the system PATH. */
  private static boolean isCommandAvailable(String command) {
    try {
      ProcessBuilder pb = new ProcessBuilder(command, "--version");
      pb.redirectErrorStream(true);
      pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
      Process process = pb.start();
      boolean finished = process.waitFor(5, TimeUnit.SECONDS);
      return finished && process.exitValue() == 0;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Resolves the host port for the database container. If {@value PORT_PROPERTY} is set, uses that
   * fixed port (e.g. {@code 5432}); otherwise computes a deterministic port from the container name
   * so that each branch gets its own port.
   */
  private static int resolvePort(ConfigurableEnvironment environment, String containerName) {
    String portValue = environment.getProperty(PORT_PROPERTY);
    if (portValue != null && !portValue.isBlank()) {
      try {
        int port = Integer.parseInt(portValue.trim());
        log("Using fixed database port from " + PORT_PROPERTY + ": " + port);
        return port;
      } catch (NumberFormatException e) {
        throw new IllegalStateException(
            "Invalid value for " + PORT_PROPERTY + ": '" + portValue + "' — expected an integer");
      }
    }
    int port = computePort(containerName);
    log("Using computed database port: " + port);
    return port;
  }

  /**
   * Computes a deterministic host port from the container name. Formula: {@code abs(hash) % (65536
   * − 10000) + 10000}, yielding a stable port in [10000, 65535] for each branch.
   */
  private static int computePort(String containerName) {
    int hash = containerName.hashCode();
    return Math.abs(hash % (PORT_RANGE_END - PORT_RANGE_START)) + PORT_RANGE_START;
  }

  private void waitForReady(String containerName, String dbUser, int port) throws Exception {
    log("Waiting for PostgreSQL on port " + port + "…");
    long deadline = System.currentTimeMillis() + READINESS_TIMEOUT_SECONDS * 1000L;

    while (System.currentTimeMillis() < deadline) {
      int rc =
          exec(runtime, "exec", containerName, "pg_isready", "-U", dbUser, "-d", DB_NAME, "-q");
      if (rc == 0) {
        return;
      }
      Thread.sleep(500);
    }
    throw new IllegalStateException(
        "PostgreSQL did not become ready within " + READINESS_TIMEOUT_SECONDS + "s");
  }

  /** On shutdown: stop the container (don't remove it) so it can be reused next time. */
  private void registerShutdownHook(String containerName) {
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  log("Stopping container " + containerName + " (will be reused next startup)…");
                  try {
                    exec(runtime, "stop", containerName);
                  } catch (Exception ignored) {
                    // best-effort
                  }
                },
                "auto-db-shutdown"));
  }

  // ---------------------------------------------------------------------------
  // Utilities
  // ---------------------------------------------------------------------------

  /** Executes a command and returns the exit code. stdout/stderr are discarded. */
  private static int exec(String... cmd) throws Exception {
    ProcessBuilder pb = new ProcessBuilder(cmd);
    pb.redirectErrorStream(true);
    pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
    Process process = pb.start();
    boolean finished = process.waitFor(30, TimeUnit.SECONDS);
    return finished ? process.exitValue() : -1;
  }

  /** Log to stdout — loggers are not yet available in EnvironmentPostProcessor. */
  private static void log(String message) {
    System.out.println("[auto-db] " + message);
  }
}
