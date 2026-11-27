package io.openaev.docs;

import static io.openaev.docs.CheckDocs.MESSAGE_ALREADY_CHECKED;
import static io.openaev.docs.CheckDocs.MESSAGE_DOCS_PROBLEMS;
import static io.openaev.docs.CheckDocs.MESSAGE_NOT_CHECKED;
import static io.openaev.docs.CheckDocs.MESSAGE_OFFLINE;
import static io.openaev.docs.CheckDocs.MESSAGE_OUTPUT;
import static io.openaev.docs.CheckDocs.MESSAGE_PYTHON_NOT_FOUND;
import static io.openaev.docs.CheckDocs.MESSAGE_SCRIPT_NOT_FOUND;
import static io.openaev.docs.CheckDocs.MESSAGE_SETUP_FAILED;
import static io.openaev.docs.CheckDocs.MESSAGE_SKIPPED;
import static io.openaev.docs.CheckDocs.MESSAGE_SKIPPED_CI;
import static io.openaev.docs.CheckDocs.MESSAGE_STARTING;
import static io.openaev.docs.CheckDocs.MESSAGE_TIMED_OUT;
import static io.openaev.docs.CheckDocs.MESSAGE_TOOLCHAIN_FAILED;
import static io.openaev.docs.CheckDocs.OUTPUT_PREFIX;
import static io.openaev.docs.CheckDocs.PYTHON_VARIABLE;
import static io.openaev.docs.CheckDocs.SKIP_HINT;
import static io.openaev.docs.CheckDocs.TAIL_LINES;
import static org.assertj.core.api.Assertions.assertThat;
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
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.codehaus.plexus.util.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("CheckDocs Tests")
@DisabledOnOs(value = OS.WINDOWS, disabledReason = "the fake Python executables are sh scripts")
class CheckDocsTest {
  private static final String PYTHON_3 = "Python 3.12.3";
  private static final String NL = System.lineSeparator();

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
    env.put("PATH", binDir.getAbsolutePath());
  }

  @AfterEach
  void after() throws IOException {
    FileUtils.deleteDirectory(tempDir);
  }

  private CheckDocs mojo(boolean offline, boolean windows) {
    CheckDocs mojo = new CheckDocs(docsDir, offline, env, windows, tempDir.toPath());
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

          @Override
          public void error(CharSequence content) {
            logged.add("ERROR " + content);
          }
        });
    return mojo;
  }

  private CheckDocs mojo(boolean offline) {
    return mojo(offline, false);
  }

  /**
   * Writes a fake Python. It answers {@code --version} with the given text (or exits like the
   * Windows Store stub when it is null); otherwise it records its call, prints its arguments, runs
   * the given shell lines and exits with the given code.
   */
  private Path fakePython(Path directory, String name, String version, String body, int exitCode)
      throws IOException {
    Files.createDirectories(directory);
    Path python = directory.resolve(name);
    String versionAnswer = version == null ? "exit 9009" : "echo '" + version + "'; exit 0";
    Files.writeString(
        python,
        "#!/bin/sh\n"
            + "case \" $* \" in *' --version '*) "
            + versionAnswer
            + ";; esac\n"
            + "echo called >> '"
            + callsFile.getAbsolutePath()
            + "'\n"
            + "echo \"args: $*\"\n"
            + body
            + "exit "
            + exitCode
            + "\n",
        StandardCharsets.UTF_8);
    python.toFile().setExecutable(true);
    return python;
  }

  private Path fakePython(String name, String version) throws IOException {
    return fakePython(binDir.toPath(), name, version, "", 0);
  }

  private Path fakePython(String body, int exitCode) throws IOException {
    return fakePython(binDir.toPath(), "python3", PYTHON_3, body, exitCode);
  }

  private Path fakePython(int exitCode) throws IOException {
    return fakePython("echo oops >&2\necho 'WARNING -  bad link'\n", exitCode);
  }

  private int calls() throws IOException {
    return callsFile.exists() ? Files.readAllLines(callsFile.toPath()).size() : 0;
  }

  private String runScript() {
    return docsDir.toPath().toAbsolutePath().normalize().resolve("run.py").toString();
  }

  private static String warning(String reason, String... output) {
    StringBuilder warning = new StringBuilder("WARN " + MESSAGE_NOT_CHECKED + reason + SKIP_HINT);
    if (output.length > 0) {
      warning.append(MESSAGE_OUTPUT);
      for (String line : output) {
        warning.append(NL).append(OUTPUT_PREFIX).append(line);
      }
    }
    return warning.toString();
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

    @ParameterizedTest
    @ValueSource(strings = {"false", "0", " "})
    @DisplayName("It runs when the CI variable says it is not CI")
    void given_ciVariableOff_should_run(String value) throws Exception {
      // Arrange
      fakePython(0);
      env.put("CI", value);

      // Act
      mojo(false).execute();

      // Assert
      assertThat(calls()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("When the check cannot run")
  class CannotRun {
    @Test
    @DisplayName("It warns that no Python was found and the build goes on")
    void given_noPython_should_warnAndPass() throws Exception {
      // Act
      mojo(false).execute();

      // Assert
      assertThat(logged).contains(warning(MESSAGE_PYTHON_NOT_FOUND.formatted("python3, python")));
    }

    @Test
    @DisplayName("It warns naming the missing script and the build goes on")
    void given_noRunScript_should_warnAndPass() throws Exception {
      // Arrange
      fakePython(0);
      Path script = docsDir.toPath().resolve("run.py");
      Files.delete(script);

      // Act
      mojo(false).execute();

      // Assert
      assertThat(logged)
          .contains(warning(MESSAGE_SCRIPT_NOT_FOUND.formatted(script.toAbsolutePath())));
    }

    @Test
    @DisplayName("It warns on a setup failure (exit code 4) with the line saying why")
    void given_setupFailure_should_warnWithReason() throws Exception {
      // Arrange
      fakePython("echo 'Docs setup: installing'\necho 'Docs setup failed: no pip' >&2\n", 4);

      // Act
      mojo(false).execute();

      // Assert
      assertThat(logged).contains(warning(MESSAGE_SETUP_FAILED, "Docs setup failed: no pip"));
    }

    @Test
    @DisplayName("It suggests running once online when the setup fails offline")
    void given_setupFailureOffline_should_suggestOnlineRun() throws Exception {
      // Arrange
      fakePython("echo 'Docs setup failed: offline' >&2\n", 4);

      // Act
      mojo(true).execute();

      // Assert
      assertThat(logged)
          .contains(warning(MESSAGE_SETUP_FAILED + MESSAGE_OFFLINE, "Docs setup failed: offline"));
    }

    @Test
    @DisplayName("It warns on a crash (exit code 3) with the error and the failure line")
    void given_crash_should_warnWithErrorLines() throws Exception {
      // Arrange
      fakePython(
          "echo 'Traceback (most recent call last):'\n"
              + "echo '  File \"check.py\", line 1'\n"
              + "echo 'ValueError: bad value'\n"
              + "echo 'Docs check could not run: the error above is in the toolchain' >&2\n",
          3);

      // Act
      mojo(false).execute();

      // Assert
      assertThat(logged)
          .contains(
              warning(
                  MESSAGE_TOOLCHAIN_FAILED.formatted(3),
                  "ValueError: bad value",
                  "Docs check could not run: the error above is in the toolchain"));
    }

    @Test
    @DisplayName("It warns with the last lines of the output when none says why")
    void given_failureWithoutReason_should_warnWithLastLines() throws Exception {
      // Arrange
      fakePython("i=0; while [ $i -lt 30 ]; do echo \"line $i\"; i=$((i+1)); done\n", 2);
      List<String> lastLines = new ArrayList<>();
      for (int i = 30 - TAIL_LINES; i < 30; i++) {
        lastLines.add("line " + i);
      }

      // Act
      mojo(false).execute();

      // Assert
      assertThat(logged)
          .contains(
              warning(MESSAGE_TOOLCHAIN_FAILED.formatted(2), lastLines.toArray(String[]::new)));
    }

    @Test
    @DisplayName("It stops a check that runs too long, with its children, and warns")
    void given_checkTooLong_should_stopItAndWarn() throws Exception {
      // Arrange
      fakePython("/bin/sleep 30\n", 0);
      CheckDocs mojo = mojo(false);
      mojo.setTimeoutSeconds(1);
      long start = System.nanoTime();

      // Act
      mojo.execute();

      // Assert
      assertThat((System.nanoTime() - start) / 1_000_000_000L).isLessThan(15);
      assertThat(logged)
          .contains(warning(MESSAGE_TIMED_OUT.formatted(1), "args: " + runScript() + " check"));
    }
  }

  @Nested
  @DisplayName("When Python runs the check")
  class Run {
    @Test
    @DisplayName("It runs docs/run.py check and streams the output at its level")
    void given_passingCheck_should_succeedAndLogOutput() throws Exception {
      // Arrange
      Path python = fakePython(0);

      // Act
      mojo(false).execute();

      // Assert
      assertThat(logged)
          .contains(
              MESSAGE_STARTING.formatted(python + " " + runScript() + " check"),
              OUTPUT_PREFIX + "args: " + runScript() + " check",
              OUTPUT_PREFIX + "oops",
              "WARN " + OUTPUT_PREFIX + "WARNING -  bad link");
    }

    @Test
    @DisplayName("It passes --offline to run.py when Maven runs offline")
    void given_offlineBuild_should_passOffline() throws Exception {
      // Arrange
      fakePython(0);

      // Act
      mojo(true).execute();

      // Assert
      assertThat(logged).contains(OUTPUT_PREFIX + "args: " + runScript() + " --offline check");
    }

    @Test
    @DisplayName("It fails the build when the check finds docs problems (exit code 1)")
    void given_docsProblems_should_failTheBuild() throws IOException {
      // Arrange
      fakePython(1);
      CheckDocs mojo = mojo(false);

      // Act / Assert
      assertThatThrownBy(mojo::execute).hasMessage(MESSAGE_DOCS_PROBLEMS + SKIP_HINT);
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
    @DisplayName("It removes the variables that make Python load foreign modules")
    void given_pythonVariablesSet_should_removeThem() throws Exception {
      // Arrange
      fakePython(
          "echo \"path=${PYTHONPATH-unset} home=${PYTHONHOME-unset} safe=${PYTHONSAFEPATH-unset}"
              + " utf8=$PYTHONUTF8 explicit=${"
              + PYTHON_VARIABLE
              + "-unset}\"\n",
          0);
      env.put("PYTHONPATH", "/elsewhere");
      env.put("PYTHONHOME", "/elsewhere");
      env.put("PYTHONSAFEPATH", "1");

      // Act
      mojo(false).execute();

      // Assert
      assertThat(logged)
          .contains(OUTPUT_PREFIX + "path=unset home=unset safe=unset utf8=1 explicit=unset");
    }
  }

  @Nested
  @DisplayName("When choosing the Python that starts run.py")
  class ChoosePython {
    private String explicitLine(Path python) {
      return OUTPUT_PREFIX + "explicit=" + python;
    }

    private Path explicitPython(String directory) throws IOException {
      return fakePython(
          tempDir.toPath().resolve(directory),
          "python",
          null,
          "echo \"explicit=$" + PYTHON_VARIABLE + "\"\n",
          0);
    }

    @Test
    @DisplayName("It takes -Dopenaev.docs.python, then OPENAEV_DOCS_PYTHON, without probing them")
    void given_explicitPythons_should_takeThemAsGiven() throws Exception {
      // Arrange
      fakePython(0);
      Path property = explicitPython("property");
      Path variable = explicitPython("variable");
      env.put(PYTHON_VARIABLE, variable.toString());
      CheckDocs withProperty = mojo(false);
      withProperty.setPythonExecutable(property.toString());

      // Act
      withProperty.execute();
      mojo(false).execute();

      // Assert
      assertThat(logged).contains(explicitLine(property), explicitLine(variable));
      assertThat(calls()).isEqualTo(2);
    }

    @Test
    @DisplayName("It resolves a relative path against the Maven working directory")
    void given_relativeExplicitPath_should_resolveIt() throws Exception {
      // Arrange
      Path python = explicitPython("venv/bin");
      CheckDocs mojo = mojo(false);
      mojo.setPythonExecutable("venv/bin/python");

      // Act
      mojo.execute();

      // Assert
      assertThat(logged).contains(explicitLine(python));
    }

    @Test
    @DisplayName("It warns when the explicit Python cannot be started")
    void given_missingExplicit_should_warnAndPass() throws Exception {
      // Arrange
      fakePython(0);
      CheckDocs mojo = mojo(false);
      mojo.setPythonExecutable(new File(tempDir, "missing/python").getPath());

      // Act
      mojo.execute();

      // Assert
      assertThat(calls()).isZero();
      assertThat(logged).anyMatch(line -> line.startsWith("WARN " + MESSAGE_NOT_CHECKED));
    }

    @Test
    @DisplayName("It ignores the PYTHON variable, which other tools such as node-gyp read")
    void given_pythonVariable_should_beIgnored() throws Exception {
      // Arrange
      Path python3 = fakePython("python3", PYTHON_3);
      Path other = fakePython(tempDir.toPath().resolve("other"), "python", PYTHON_3, "", 0);
      env.put("PYTHON", other.toString());

      // Act
      mojo(false).execute();

      // Assert
      assertThat(logged)
          .contains(MESSAGE_STARTING.formatted(python3 + " " + runScript() + " check"));
    }

    @Test
    @DisplayName("It takes python3 before python")
    void given_python3AndPython_should_takePython3() throws Exception {
      // Arrange
      Path python3 = fakePython("python3", PYTHON_3);
      fakePython("python", PYTHON_3);

      // Act
      mojo(false).execute();

      // Assert
      assertThat(logged)
          .contains(MESSAGE_STARTING.formatted(python3 + " " + runScript() + " check"));
    }

    @Test
    @DisplayName("It skips a command that does not answer --version as Python 3")
    void given_storeStubAndPython2_should_skipThem() throws Exception {
      // Arrange
      fakePython("python3", null);
      fakePython("python", "Python 2.7.18");

      // Act
      mojo(false).execute();

      // Assert
      assertThat(calls()).isZero();
      assertThat(logged).contains(warning(MESSAGE_PYTHON_NOT_FOUND.formatted("python3, python")));
    }

    @Test
    @DisplayName("It takes the py launcher with -3 first on Windows, then python3.exe, python.exe")
    void given_windows_should_preferPyLauncher() throws Exception {
      // Arrange
      Path py = fakePython("py.exe", PYTHON_3);
      fakePython("python3.exe", null);
      Path python = fakePython("python.exe", PYTHON_3);
      env.clear();
      env.put("Path", binDir.getPath());

      // Act
      mojo(false, true).execute();
      Files.delete(py);
      mojo(false, true).execute();

      // Assert
      assertThat(logged)
          .contains(
              MESSAGE_STARTING.formatted(py + " -3 " + runScript() + " check"),
              MESSAGE_STARTING.formatted(python + " " + runScript() + " check"));
    }
  }
}
