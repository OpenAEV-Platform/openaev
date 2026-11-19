package io.openaev.docs;

import static io.openaev.docs.CheckDocs.HIGHEST_MINOR_TRIED;
import static io.openaev.docs.CheckDocs.MESSAGE_ALREADY_CHECKED;
import static io.openaev.docs.CheckDocs.MESSAGE_DOCS_PROBLEMS;
import static io.openaev.docs.CheckDocs.MESSAGE_EXPLICIT_UNUSABLE;
import static io.openaev.docs.CheckDocs.MESSAGE_NOT_CHECKED;
import static io.openaev.docs.CheckDocs.MESSAGE_OFFLINE;
import static io.openaev.docs.CheckDocs.MESSAGE_PYTHON_NOT_FOUND;
import static io.openaev.docs.CheckDocs.MESSAGE_SCRIPT_NOT_FOUND;
import static io.openaev.docs.CheckDocs.MESSAGE_SETUP_FAILED;
import static io.openaev.docs.CheckDocs.MESSAGE_SKIPPED;
import static io.openaev.docs.CheckDocs.MESSAGE_SKIPPED_CI;
import static io.openaev.docs.CheckDocs.MESSAGE_TOOLCHAIN_FAILED;
import static io.openaev.docs.CheckDocs.MESSAGE_USING_PYTHON;
import static io.openaev.docs.CheckDocs.PROBLEM_NOT_FOUND;
import static io.openaev.docs.CheckDocs.PROBLEM_PROBE_FAILED;
import static io.openaev.docs.CheckDocs.PROBLEM_TOO_OLD;
import static io.openaev.docs.CheckDocs.PYTHON_VARIABLE;
import static io.openaev.docs.CheckDocs.SKIP_HINT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.codehaus.plexus.util.FileUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

@DisabledOnOs(value = OS.WINDOWS, disabledReason = "the fake Python executables are sh scripts")
public class CheckDocsTest {
  private static final int[] MINIMUM = {3, 11};
  private static final String MINIMUM_TEXT = "3.11";

  private File tempDir;
  private File docsDir;
  private File binDir;
  private File callsFile;
  private final Map<String, String> env = new HashMap<>();
  private final List<String> logged = new ArrayList<>();

  @BeforeEach
  void before() throws IOException {
    tempDir = Files.createTempDirectory("io.openaev-maven-plugin-docs-check").toFile();
    docsDir = new File(tempDir, "docs");
    docsDir.mkdirs();
    binDir = new File(tempDir, "bin");
    binDir.mkdirs();
    callsFile = new File(tempDir, "calls");
    Files.writeString(docsDir.toPath().resolve("run.py"), "", StandardCharsets.UTF_8);
    Files.writeString(
        docsDir.toPath().resolve(".python-version"), MINIMUM_TEXT + "\n", StandardCharsets.UTF_8);
    env.put("PATH", binDir.getAbsolutePath());
  }

  @AfterEach
  void after() throws IOException {
    FileUtils.deleteDirectory(tempDir);
  }

  private CheckDocs mojo(boolean offline) {
    CheckDocs mojo = new CheckDocs(docsDir, offline, env, false);
    mojo.setLog(
        new SystemStreamLog() {
          @Override
          public void info(CharSequence content) {
            logged.add(content.toString());
          }

          @Override
          public void warn(CharSequence content) {
            logged.add("WARN " + content);
          }
        });
    return mojo;
  }

  /**
   * Writes a fake Python. It answers the probe with the given version (or fails it, like a Python
   * without the venv module, when the version is null); otherwise it records its call, prints its
   * arguments and exits with the given code.
   */
  private Path fakePython(Path directory, String name, String version, int exitCode)
      throws IOException {
    Files.createDirectories(directory);
    Path python = directory.resolve(name);
    String probeAnswer =
        version == null
            ? "echo \"ModuleNotFoundError: No module named 'ensurepip'\"; exit 1"
            : "echo " + version + "; exit 0";
    Files.writeString(
        python,
        "#!/bin/sh\n"
            + "case \"$*\" in *'import sys, venv, ensurepip'*) "
            + probeAnswer
            + ";; esac\n"
            + "echo called >> '"
            + callsFile.getAbsolutePath()
            + "'\n"
            + "echo \"args: $*\"\n"
            + "echo oops >&2\n"
            + "echo \"WARNING -  bad link\"\n"
            + "exit "
            + exitCode
            + "\n",
        StandardCharsets.UTF_8);
    python.toFile().setExecutable(true);
    return python;
  }

  private Path fakePython(String name, String version) throws IOException {
    return fakePython(binDir.toPath(), name, version, 0);
  }

  private Path fakePython(int exitCode) throws IOException {
    return fakePython(binDir.toPath(), "python3", "3.12", exitCode);
  }

  /** Writes an executable that exits like the Windows Store stub, without printing anything. */
  private Path storeStub(String name) throws IOException {
    Path stub = binDir.toPath().resolve(name);
    Files.writeString(stub, "#!/bin/sh\nexit 9009\n", StandardCharsets.UTF_8);
    stub.toFile().setExecutable(true);
    return stub;
  }

  private int calls() throws IOException {
    return callsFile.exists() ? Files.readAllLines(callsFile.toPath()).size() : 0;
  }

  private String runScript() {
    return docsDir.toPath().toAbsolutePath().normalize().resolve("run.py").toString();
  }

  private String warning(String reason) {
    return "WARN " + MESSAGE_NOT_CHECKED + reason + SKIP_HINT;
  }

  private CheckDocs.Lookup lookup(String pythonProperty, boolean windows) {
    return CheckDocs.findPython(env, pythonProperty, windows, MINIMUM);
  }

  private List<String> foundCommand(String pythonProperty, boolean windows) {
    CheckDocs.Python python = lookup(pythonProperty, windows).python();
    return python == null ? null : python.command();
  }

  @Nested
  @DisplayName("When the check is skipped")
  class Skipped {
    @Test
    @DisplayName("It does nothing with -Dopenaev.docs.check.skip=true")
    void given_skipProperty_should_notRun() throws Exception {
      // Arrange
      fakePython(0);
      CheckDocs mojo = mojo(false);
      mojo.setSkip(true);

      // Act
      mojo.execute();

      // Assert
      assertThat(calls()).isZero();
      assertThat(logged).contains(MESSAGE_SKIPPED);
    }

    @Test
    @DisplayName("It does nothing in CI, where the Docs Check job runs it")
    void given_ciVariable_should_notRun() throws Exception {
      // Arrange
      fakePython(0);
      env.put("CI", "true");

      // Act
      mojo(false).execute();

      // Assert
      assertThat(calls()).isZero();
      assertThat(logged).contains(MESSAGE_SKIPPED_CI);
    }
  }

  @Nested
  @DisplayName("When the check cannot run")
  class CannotRun {
    @Test
    @DisplayName("It warns that no usable Python was found and the build goes on")
    void given_noPython_should_warnAndPass() {
      // Arrange
      List<String> names = new ArrayList<>(List.of("python3", "python"));
      for (int minor = HIGHEST_MINOR_TRIED; minor >= MINIMUM[1]; minor--) {
        names.add("python3." + minor);
      }

      // Act / Assert
      assertThatCode(() -> mojo(false).execute()).doesNotThrowAnyException();
      assertThat(logged)
          .contains(
              warning(
                  MESSAGE_PYTHON_NOT_FOUND.formatted(
                      MINIMUM_TEXT, "none of " + String.join(", ", names) + " is on it")));
    }

    @Test
    @DisplayName("It warns naming the missing script and the build goes on")
    void given_noRunScript_should_warnAndPass() throws IOException {
      // Arrange
      fakePython(0);
      Path script = docsDir.toPath().resolve("run.py");
      Files.delete(script);

      // Act / Assert
      assertThatCode(() -> mojo(false).execute()).doesNotThrowAnyException();
      assertThat(logged)
          .contains(warning(MESSAGE_SCRIPT_NOT_FOUND.formatted(script.toAbsolutePath())));
    }

    @Test
    @DisplayName("It warns on an environment setup failure (exit code 4) and the build goes on")
    void given_setupFailure_should_warnAndPass() throws IOException {
      // Arrange
      fakePython(4);

      // Act / Assert
      assertThatCode(() -> mojo(false).execute()).doesNotThrowAnyException();
      assertThat(logged).contains(warning(MESSAGE_SETUP_FAILED));
    }

    @Test
    @DisplayName("It suggests running once online when the setup fails offline")
    void given_setupFailureOffline_should_suggestOnlineRun() throws IOException {
      // Arrange
      fakePython(4);

      // Act / Assert
      assertThatCode(() -> mojo(true).execute()).doesNotThrowAnyException();
      assertThat(logged).contains(warning(MESSAGE_SETUP_FAILED + MESSAGE_OFFLINE));
    }

    @Test
    @DisplayName("It warns on a toolchain failure (exit code 3) and the build goes on")
    void given_toolchainFailure_should_warnAndPass() throws IOException {
      // Arrange
      fakePython(3);

      // Act / Assert
      assertThatCode(() -> mojo(false).execute()).doesNotThrowAnyException();
      assertThat(logged).contains(warning(MESSAGE_TOOLCHAIN_FAILED.formatted(3)));
    }
  }

  @Nested
  @DisplayName("When Python runs the check")
  class Run {
    @Test
    @DisplayName("It runs docs/run.py check, logs the Python it uses and streams the output")
    void given_passingCheck_should_succeedAndLogOutput() throws Exception {
      // Arrange
      Path python = fakePython(0);

      // Act
      mojo(false).execute();

      // Assert
      assertThat(logged)
          .contains(
              MESSAGE_USING_PYTHON.formatted("3.12", python),
              "[docs] args: " + runScript() + " check",
              "[docs] oops",
              "WARN [docs] WARNING -  bad link");
    }

    @Test
    @DisplayName("It passes --offline to run.py when Maven runs offline")
    void given_offlineBuild_should_passOffline() throws Exception {
      // Arrange
      fakePython(0);

      // Act
      mojo(true).execute();

      // Assert
      assertThat(logged).contains("[docs] args: " + runScript() + " --offline check");
    }

    @Test
    @DisplayName("It fails the build when the check finds docs problems (exit code 1)")
    void given_docsProblems_should_failTheBuild() throws IOException {
      // Arrange
      fakePython(1);

      // Act / Assert
      assertThatThrownBy(() -> mojo(false).execute())
          .isInstanceOf(MojoFailureException.class)
          .hasMessage(MESSAGE_DOCS_PROBLEMS + SKIP_HINT);
    }

    @Test
    @DisplayName("It runs once per build, when a forked lifecycle runs the goal again")
    void given_sameBuild_should_runOnce() throws Exception {
      // Arrange
      fakePython(0);
      Date buildStart = new Date(System.nanoTime());
      CheckDocs first = mojo(false);
      first.setBuildStartTime(buildStart);
      CheckDocs second = mojo(false);
      second.setBuildStartTime(buildStart);
      CheckDocs nextBuild = mojo(false);
      nextBuild.setBuildStartTime(new Date(buildStart.getTime() + 1));

      // Act
      first.execute();
      second.execute();
      nextBuild.execute();

      // Assert
      assertThat(calls()).isEqualTo(2);
      assertThat(logged).contains(MESSAGE_ALREADY_CHECKED);
    }

    @Test
    @DisplayName("It does not pass PYTHONPATH or PYTHONHOME to the check")
    void given_pythonPathSet_should_removeIt() throws Exception {
      // Arrange
      Path python = binDir.toPath().resolve("python3");
      Files.writeString(
          python,
          "#!/bin/sh\n"
              + "case \"$*\" in *'import sys, venv'*) echo 3.12; exit 0;; esac\n"
              + "echo \"path=${PYTHONPATH-unset} home=${PYTHONHOME-unset} utf8=$PYTHONUTF8\"\n",
          StandardCharsets.UTF_8);
      python.toFile().setExecutable(true);

      // Act
      mojo(false).execute();

      // Assert
      assertThat(logged).contains("[docs] path=unset home=unset utf8=1");
    }
  }

  @Nested
  @DisplayName("When looking Python up")
  class FindPython {
    @Test
    @DisplayName("It reads the minimum version from docs/.python-version")
    void given_pythonVersionFile_should_readMinimum() throws Exception {
      // Arrange
      Files.writeString(
          docsDir.toPath().resolve(".python-version"), "3.13.1\n", StandardCharsets.UTF_8);

      // Act
      int[] minimum = CheckDocs.minimumVersion(docsDir.toPath());

      // Assert
      assertThat(minimum).containsExactly(3, 13);
    }

    @Test
    @DisplayName("It takes -Dopenaev.docs.python, then OPENAEV_DOCS_PYTHON, before the PATH")
    void given_explicitPythons_should_takeThemFirst() throws Exception {
      // Arrange
      fakePython(0);
      Path property = fakePython(tempDir.toPath().resolve("property"), "python", "3.12", 0);
      Path variable = fakePython(tempDir.toPath().resolve("variable"), "python", "3.12", 0);
      env.put(PYTHON_VARIABLE, variable.toString());

      // Act
      List<String> withProperty = foundCommand(property.toString(), false);
      List<String> withVariable = foundCommand(null, false);

      // Assert
      assertThat(withProperty).containsExactly(property.toString());
      assertThat(withVariable).containsExactly(variable.toString());
    }

    @Test
    @DisplayName("It ignores the PYTHON variable, which other tools such as node-gyp read")
    void given_pythonVariable_should_beIgnored() throws Exception {
      // Arrange
      Path python3 = fakePython("python3", "3.12");
      Path other = fakePython(tempDir.toPath().resolve("other"), "python", "3.12", 0);
      env.put("PYTHON", other.toString());

      // Act / Assert
      assertThat(foundCommand(null, false)).containsExactly(python3.toString());
    }

    @Test
    @DisplayName("It resolves an explicit command name on the PATH")
    void given_explicitName_should_resolveOnPath() throws Exception {
      // Arrange
      Path python312 = fakePython("python3.12", "3.12");

      // Act / Assert
      assertThat(foundCommand("python3.12", false)).containsExactly(python312.toString());
    }

    @Test
    @DisplayName("It reports an explicit Python that is missing, without venv or too old")
    void given_unusableExplicit_should_reportItWithoutFallback() throws IOException {
      // Arrange
      fakePython(0);
      String missing = new File(tempDir, "missing/python").getPath();
      Path noVenv = fakePython("python3.12", null);
      Path old = fakePython("python3.9", "3.9");

      // Act
      CheckDocs.Lookup missingLookup = lookup(missing, false);
      CheckDocs.Lookup noVenvLookup = lookup(noVenv.toString(), false);
      env.put(PYTHON_VARIABLE, old.toString());
      CheckDocs.Lookup oldLookup = lookup(null, false);

      // Assert
      assertThat(missingLookup.python()).isNull();
      assertThat(missingLookup.problem())
          .isEqualTo(
              MESSAGE_EXPLICIT_UNUSABLE.formatted(
                  "-Dopenaev.docs.python=" + missing, PROBLEM_NOT_FOUND, MINIMUM_TEXT));
      assertThat(noVenvLookup.problem())
          .isEqualTo(
              MESSAGE_EXPLICIT_UNUSABLE.formatted(
                  "-Dopenaev.docs.python=" + noVenv, PROBLEM_PROBE_FAILED, MINIMUM_TEXT));
      assertThat(oldLookup.problem())
          .isEqualTo(
              MESSAGE_EXPLICIT_UNUSABLE.formatted(
                  PYTHON_VARIABLE + "=" + old, PROBLEM_TOO_OLD.formatted("3.9"), MINIMUM_TEXT));
    }

    @Test
    @DisplayName("It warns, without running the check, when the explicit Python is unusable")
    void given_unusableExplicit_should_warnAndPass() throws IOException {
      // Arrange
      fakePython(0);
      Path noVenv = fakePython("python3.12", null);
      CheckDocs mojo = mojo(false);
      mojo.setPythonExecutable(noVenv.toString());

      // Act / Assert
      assertThatCode(mojo::execute).doesNotThrowAnyException();
      assertThat(calls()).isZero();
      assertThat(logged)
          .contains(
              warning(
                  MESSAGE_EXPLICIT_UNUSABLE.formatted(
                      "-Dopenaev.docs.python=" + noVenv, PROBLEM_PROBE_FAILED, MINIMUM_TEXT)));
    }

    @Test
    @DisplayName("It takes python3 before python on the PATH")
    void given_python3AndPython_should_takePython3() throws Exception {
      // Arrange
      Path python3 = fakePython("python3", "3.12");
      fakePython("python", "3.12");

      // Act / Assert
      assertThat(foundCommand(null, false)).containsExactly(python3.toString());
    }

    @Test
    @DisplayName(
        "It skips Pythons without venv or too old, and tries versioned names, newest first")
    void given_unusablePython3_should_takeNewestVersionedName() throws Exception {
      // Arrange
      fakePython("python3", null);
      fakePython("python", "2.7");
      fakePython("python3.11", "3.11");
      Path python313 = fakePython("python3.13", "3.13");
      fakePython("python3.10", "3.10");

      // Act / Assert
      assertThat(foundCommand(null, false)).containsExactly(python313.toString());
    }

    @Test
    @DisplayName("It tries versioned names from python3." + HIGHEST_MINOR_TRIED)
    void given_highestVersionedName_should_findIt() throws Exception {
      // Arrange
      Path newest = fakePython("python3." + HIGHEST_MINOR_TRIED, "3." + HIGHEST_MINOR_TRIED);

      // Act / Assert
      assertThat(foundCommand(null, false)).containsExactly(newest.toString());
    }

    @Test
    @DisplayName("It skips a Python that does not answer the probe, like the Store stub")
    void given_storeStub_should_skipIt() throws Exception {
      // Arrange
      storeStub("python3");
      Path python = fakePython("python", "3.12");

      // Act / Assert
      assertThat(foundCommand(null, false)).containsExactly(python.toString());
    }

    @Test
    @DisplayName("It takes the py launcher with -3 first on Windows, then python3.exe, python.exe")
    void given_windows_should_preferPyLauncher() throws Exception {
      // Arrange
      Path py = fakePython("py.exe", "3.12");
      storeStub("python3.exe");
      Path python = fakePython("python.exe", "3.12");
      env.clear();
      env.put("Path", binDir.getPath());

      // Act
      List<String> withLauncher = foundCommand(null, true);
      Files.delete(py);
      List<String> withoutLauncher = foundCommand(null, true);

      // Assert
      assertThat(withLauncher).containsExactly(py.toString(), "-3");
      assertThat(withoutLauncher).containsExactly(python.toString());
    }

    @Test
    @DisplayName("It takes a Windows PATH entry that is not an executable file, like a Store alias")
    void given_windowsAlias_should_beCandidate() throws Exception {
      // Arrange
      Path alias = binDir.toPath().resolve("python3.exe");
      Files.writeString(alias, "", StandardCharsets.UTF_8);
      alias.toFile().setExecutable(false);

      // Act
      Optional<Path> onWindows = CheckDocs.findExecutable("python3", binDir.getPath(), true);
      Optional<Path> elsewhere = CheckDocs.findExecutable("python3.exe", binDir.getPath(), false);

      // Assert
      assertThat(onWindows).contains(alias);
      assertThat(elsewhere).isEmpty();
    }
  }
}
