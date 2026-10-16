package io.openaev.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ties the CI shadow summary's reading of the fail-closed shadow report to the gate's own
 * constants, the same way {@link WriteAttrSummaryMarkerTest} does for the write side. Under a
 * shadow mode the per-test assertion is inert, so the report file is the ONLY channel that reaches
 * CI: surefire keeps the fork's stdout (where the {@code [FAILCLOSED]} markers go) out of its
 * report files. A rename on either side would make the summary print nothing and the verdict pass
 * vacuously, since the script's {@code -f} check just finds no file.
 */
@DisplayName("Fail-closed shadow summary stays in sync with the gate's report contract")
class FailClosedShadowSummaryMarkerTest {

  private static final String ACTION_YML = ".github/actions/api-tests/action.yml";

  private static String actionYmlText() {
    Path path = findRepoRoot().resolve(ACTION_YML);
    try {
      return Files.readString(path);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read " + path, e);
    }
  }

  /**
   * Same walk-up as {@link WriteAttrSummaryMarkerTest}: surefire runs from the module directory.
   */
  private static Path findRepoRoot() {
    Path dir = Paths.get("").toAbsolutePath();
    for (int i = 0; i < 4; i++) {
      if (Files.exists(dir.resolve(ACTION_YML))) {
        return dir;
      }
      Path parent = dir.getParent();
      if (parent == null) {
        break;
      }
      dir = parent;
    }
    throw new IllegalStateException(
        "cannot locate " + ACTION_YML + " by walking up from " + Paths.get("").toAbsolutePath());
  }

  @Test
  @DisplayName("the summary reads the shadow report by its exact file name")
  void given_actionYml_should_containTheShadowReportFileName() {
    assertThat(actionYmlText())
        .as(
            "the shadow summary locates the fail-closed report by this exact file name; it must"
                + " match FailClosedGateExtension.SHADOW_REPORT_FILE verbatim")
        .contains(FailClosedGateExtension.SHADOW_REPORT_FILE);
  }

  @Test
  @DisplayName("the summary counts new signatures by the gate's own line prefix")
  void given_actionYml_should_countTheGateNewPrefix() {
    assertThat(actionYmlText())
        .as(
            "the verdict counts the report's lines prefixed by"
                + " FailClosedGateExtension.NEW_PREFIX; a prefix change on either side makes every"
                + " shadow run pass vacuously")
        .contains("^" + FailClosedGateExtension.NEW_PREFIX);
  }

  @Test
  @DisplayName("the summary arms the gate through the gate's own property name")
  void given_actionYml_should_armTheShadowModeProperty() {
    assertThat(actionYmlText())
        .as(
            "the mode reaches the JVM as this system property; a rename leaves the per-test"
                + " assertion armed in a shadow run, which is the state this gate moved away from")
        .contains(FailClosedGateExtension.SHADOW_MODE_PROPERTY);
  }

  @Test
  @DisplayName("every armed mode ships its own stored signature list")
  void given_everyShadowMode_should_shipItsOwnList() {
    for (String mode : FailClosedGateExtension.SHADOW_MODES) {
      String resource = FailClosedGateExtension.modeListResource(mode);
      assertThat(FailClosedGateExtension.class.getResourceAsStream(resource))
          .as(
              "mode '%s' has no stored list at %s: every signature would read as new and the"
                  + " shadow verdict would be red from the first night with no way to curate it",
              mode, resource)
          .isNotNull();
    }
  }

  @Test
  @DisplayName("the action arms every mode name the gate accepts")
  void given_actionYml_should_armOnlyKnownModes() {
    String yml = actionYmlText();
    for (String mode : FailClosedGateExtension.SHADOW_MODES) {
      assertThat(yml)
          .as(
              "mode '%s' is accepted by the gate but never named by the action: the gate refuses"
                  + " an unknown mode, so a vocabulary drift would error every test of a shadow"
                  + " shard instead of reporting",
              mode)
          .contains("GATE_MODE=" + mode);
    }
  }
}
