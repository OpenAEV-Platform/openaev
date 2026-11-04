package io.openaev.docs;

import static io.openaev.docs.CheckDocs.HIGHEST_MINOR_TRIED;
import static io.openaev.docs.CheckDocs.MESSAGE_ALREADY_CHECKED;
import static io.openaev.docs.CheckDocs.MESSAGE_DOCS_PROBLEMS;
import static io.openaev.docs.CheckDocs.MESSAGE_EXPLICIT_NOT_FOUND;
import static io.openaev.docs.CheckDocs.MESSAGE_EXPLICIT_NO_VERSION;
import static io.openaev.docs.CheckDocs.MESSAGE_EXPLICIT_TOO_OLD;
import static io.openaev.docs.CheckDocs.MESSAGE_OFFLINE;
import static io.openaev.docs.CheckDocs.MESSAGE_PYTHON_NOT_FOUND;
import static io.openaev.docs.CheckDocs.MESSAGE_SCRIPT_NOT_FOUND;
import static io.openaev.docs.CheckDocs.MESSAGE_SETUP_FAILED;
import static io.openaev.docs.CheckDocs.MESSAGE_SKIPPED;
import static io.openaev.docs.CheckDocs.MESSAGE_SKIPPED_CI;
import static io.openaev.docs.CheckDocs.MESSAGE_TOOLCHAIN_FAILED;
import static io.openaev.docs.CheckDocs.MESSAGE_USING_PYTHON;
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
import org.apache.maven.plugin.MojoExecutionException;
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
   * Writes a fake Python that answers {@code --version} with the given version, and otherwise
   * records its call, prints its arguments and exits with the given code.
   */
  private Path fakePython(Path directory, String name, String version, int exitCode)
      throws IOException {
    Files.createDirectories(directory);
    Path python = directory.resolve(name);
    Files.writeString(
        python,
        "#!/bin/sh\n"
            + "case \"$*\" in *--version) echo 'Python "
            + version
            + "'; exit 0;; esac\n"
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
    return fakePython(binDir.toPath(), "python3", "3.12.3", exitCode);
  }

  /** Writes an executable that exits like the Windows Store stub, without printing a version. */
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

  private Optional<List<String>> foundCommand(String pythonProperty, boolean windows)
      throws MojoExecutionException {
    return CheckDocs.findPython(env, pythonProperty, windows, MINIMUM)
        .map(CheckDocs.Python::command);
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
    @DisplayName("It does nothing in CI, where the Docs Check job runs it, even without Python")
    void given_ciVariable_should_notRun() throws Exception {
      // Arrange
      env.put("CI", "true");

      // Act
      mojo(false).execute();

      // Assert
      assertThat(logged).contains(MESSAGE_SKIPPED_CI);
    }
  }

  @Nested
  @DisplayName("When the check cannot start")
  class CannotStart {
    @Test
    @DisplayName("It fails telling to install Python, point to it, or skip the check")
    void given_noPython_should_failWithHint() {
      // Act / Assert
      assertThatThrownBy(() -> mojo(false).execute())
          .isInstanceOf(MojoExecutionException.class)
          .hasMessage(MESSAGE_PYTHON_NOT_FOUND.formatted(MINIMUM_TEXT));
    }

    @Test
    @DisplayName("It fails naming the missing script")
    void given_noRunScript_should_fail() throws IOException {
      // Arrange
      Files.delete(docsDir.toPath().resolve("run.py"));

      // Act / Assert
      assertThatThrownBy(() -> mojo(false).execute())
          .isInstanceOf(MojoExecutionException.class)
          .hasMessageContaining(MESSAGE_SCRIPT_NOT_FOUND);
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
              MESSAGE_USING_PYTHON.formatted("3.12.3", python),
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
    @DisplayName("It fails asking to fix the docs when the check finds problems (exit code 1)")
    void given_docsProblems_should_failAskingToFixDocs() throws IOException {
      // Arrange
      fakePython(1);

      // Act / Assert
      assertThatThrownBy(() -> mojo(false).execute())
          .isInstanceOf(MojoFailureException.class)
          .hasMessage(MESSAGE_DOCS_PROBLEMS + SKIP_HINT);
    }

    @Test
    @DisplayName("It reports a toolchain failure on exit code 3")
    void given_toolchainFailure_should_failAsCouldNotRun() throws IOException {
      // Arrange
      fakePython(3);

      // Act / Assert
      assertThatThrownBy(() -> mojo(false).execute())
          .isInstanceOf(MojoExecutionException.class)
          .hasMessage(MESSAGE_TOOLCHAIN_FAILED.formatted(3) + SKIP_HINT);
    }

    @Test
    @DisplayName("It reports an environment setup failure on exit code 4")
    void given_setupFailure_should_explainPythonNeeds() throws IOException {
      // Arrange
      fakePython(4);

      // Act / Assert
      assertThatThrownBy(() -> mojo(false).execute())
          .isInstanceOf(MojoExecutionException.class)
          .hasMessage(MESSAGE_SETUP_FAILED.formatted(MINIMUM_TEXT) + SKIP_HINT);
    }

    @Test
    @DisplayName("It suggests running once online when the setup fails offline")
    void given_setupFailureOffline_should_suggestOnlineRun() throws IOException {
      // Arrange
      fakePython(4);

      // Act / Assert
      assertThatThrownBy(() -> mojo(true).execute())
          .isInstanceOf(MojoExecutionException.class)
          .hasMessage(MESSAGE_SETUP_FAILED.formatted(MINIMUM_TEXT) + MESSAGE_OFFLINE + SKIP_HINT);
    }

    @Test
    @DisplayName("It does not pass PYTHONPATH or PYTHONHOME to the check")
    void given_pythonPathSet_should_removeIt() throws Exception {
      // Arrange
      Path python = binDir.toPath().resolve("python3");
      Files.writeString(
          python,
          "#!/bin/sh\n"
              + "if [ \"$1\" = --version ]; then echo 'Python 3.12.3'; exit 0; fi\n"
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
    @DisplayName("It takes -Dopenaev.docs.python, then PYTHON, before the PATH")
    void given_explicitPythons_should_takeThemFirst() throws Exception {
      // Arrange
      fakePython(0);
      Path property = fakePython(tempDir.toPath().resolve("property"), "python", "3.12.0", 0);
      Path variable = fakePython(tempDir.toPath().resolve("variable"), "python", "3.12.0", 0);
      env.put("PYTHON", variable.toString());

      // Act
      Optional<List<String>> withProperty = foundCommand(property.toString(), false);
      Optional<List<String>> withVariable = foundCommand(null, false);

      // Assert
      assertThat(withProperty).contains(List.of(property.toString()));
      assertThat(withVariable).contains(List.of(variable.toString()));
    }

    @Test
    @DisplayName("It resolves an explicit command name on the PATH")
    void given_explicitName_should_resolveOnPath() throws Exception {
      // Arrange
      Path python312 = fakePython("python3.12", "3.12.0");

      // Act / Assert
      assertThat(foundCommand("python3.12", false)).contains(List.of(python312.toString()));
    }

    @Test
    @DisplayName("It fails naming an explicit Python that does not exist, without falling back")
    void given_explicitNotFound_should_fail() throws IOException {
      // Arrange
      fakePython(0);
      String missing = new File(tempDir, "missing/python").getPath();

      // Act / Assert
      assertThatThrownBy(() -> foundCommand(missing, false))
          .isInstanceOf(MojoExecutionException.class)
          .hasMessage(
              MESSAGE_EXPLICIT_NOT_FOUND.formatted("-Dopenaev.docs.python=" + missing) + SKIP_HINT);
    }

    @Test
    @DisplayName("It fails naming an explicit Python that does not answer --version")
    void given_explicitStub_should_fail() throws IOException {
      // Arrange
      Path stub = storeStub("python3");
      env.put("PYTHON", stub.toString());

      // Act / Assert
      assertThatThrownBy(() -> foundCommand(null, false))
          .isInstanceOf(MojoExecutionException.class)
          .hasMessage(MESSAGE_EXPLICIT_NO_VERSION.formatted("PYTHON=" + stub, stub) + SKIP_HINT);
    }

    @Test
    @DisplayName("It fails naming an explicit Python below the minimum version")
    void given_explicitTooOld_should_fail() throws IOException {
      // Arrange
      fakePython("python3", "3.12.0");
      Path old = fakePython("python3.9", "3.9.18");

      // Act / Assert
      assertThatThrownBy(() -> foundCommand(old.toString(), false))
          .isInstanceOf(MojoExecutionException.class)
          .hasMessage(
              MESSAGE_EXPLICIT_TOO_OLD.formatted(
                      "-Dopenaev.docs.python=" + old, "3.9.18", MINIMUM_TEXT)
                  + SKIP_HINT);
    }

    @Test
    @DisplayName("It takes python3 before python on the PATH")
    void given_python3AndPython_should_takePython3() throws Exception {
      // Arrange
      Path python3 = fakePython("python3", "3.12.0");
      fakePython("python", "3.12.0");

      // Act / Assert
      assertThat(foundCommand(null, false)).contains(List.of(python3.toString()));
    }

    @Test
    @DisplayName("It skips a Python below the minimum and tries the versioned names, newest first")
    void given_oldPython3_should_takeNewestVersionedName() throws Exception {
      // Arrange
      fakePython("python3", "3.10.12");
      fakePython("python", "2.7.18");
      fakePython("python3.11", "3.11.9");
      Path python313 = fakePython("python3.13", "3.13.1");
      fakePython("python3.10", "3.10.12");

      // Act / Assert
      assertThat(foundCommand(null, false)).contains(List.of(python313.toString()));
    }

    @Test
    @DisplayName("It tries versioned names up to python3." + HIGHEST_MINOR_TRIED)
    void given_highestVersionedName_should_findIt() throws Exception {
      // Arrange
      Path newest = fakePython("python3." + HIGHEST_MINOR_TRIED, "3." + HIGHEST_MINOR_TRIED);

      // Act / Assert
      assertThat(foundCommand(null, false)).contains(List.of(newest.toString()));
    }

    @Test
    @DisplayName("It skips a Python that does not answer --version, like the Store stub")
    void given_storeStub_should_skipIt() throws Exception {
      // Arrange
      storeStub("python3");
      Path python = fakePython("python", "3.12.0");

      // Act / Assert
      assertThat(foundCommand(null, false)).contains(List.of(python.toString()));
    }

    @Test
    @DisplayName("It finds nothing when no candidate answers")
    void given_onlyStubs_should_findNothing() throws Exception {
      // Arrange
      storeStub("python3");
      storeStub("python");

      // Act / Assert
      assertThat(foundCommand(null, false)).isEmpty();
    }

    @Test
    @DisplayName("It takes the py launcher with -3 first on Windows, then python3.exe, python.exe")
    void given_windows_should_preferPyLauncher() throws Exception {
      // Arrange
      Path py = fakePython("py.exe", "3.12.0");
      storeStub("python3.exe");
      Path python = fakePython("python.exe", "3.12.0");
      env.clear();
      env.put("Path", binDir.getPath());

      // Act
      Optional<List<String>> withLauncher = foundCommand(null, true);
      Files.delete(py);
      Optional<List<String>> withoutLauncher = foundCommand(null, true);

      // Assert
      assertThat(withLauncher).contains(List.of(py.toString(), "-3"));
      assertThat(withoutLauncher).contains(List.of(python.toString()));
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

    @Test
    @DisplayName("It takes the first PATH directory holding the executable")
    void given_pathWithPython_should_findIt() throws IOException {
      // Arrange
      Path python3 = fakePython(0);
      String searchPath =
          String.join(
              File.pathSeparator, "", new File(tempDir, "nowhere").getPath(), binDir.getPath());

      // Act / Assert
      assertThat(CheckDocs.findExecutable("python3", searchPath, false)).contains(python3);
      assertThatCode(() -> CheckDocs.findExecutable("python3", null, false))
          .doesNotThrowAnyException();
      assertThat(CheckDocs.findExecutable("python3", "", false)).isEmpty();
    }
  }
}
