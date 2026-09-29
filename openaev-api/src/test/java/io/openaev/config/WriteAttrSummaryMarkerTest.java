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
 * Ties the CI shadow summary's extraction of wrong-tenant writes to the exact wording of {@link
 * WriteAttrGateExtension}'s failure message, so an innocuous copy-edit to either side cannot make
 * the summary print "No wrong-tenant production write" while the gate is failing (the script
 * locates the offending-signatures block by grepping the surefire report for the failure message's
 * open and close lines, which live only as free text otherwise). Proven by changing either marker
 * in isolation: the test goes red (evidence in the task report), because the script's literal
 * pattern no longer matches {@link WriteAttrGateExtension#FAILURE_MARKER} / {@link
 * WriteAttrGateExtension#ATTRIBUTE_MARKER}.
 */
@DisplayName("Write-attribution shadow summary stays in sync with the gate's failure message")
class WriteAttrSummaryMarkerTest {

  private static final String ACTION_YML = ".github/actions/api-tests/action.yml";

  private static String actionYmlText() {
    Path repoRoot = findRepoRoot();
    Path path = repoRoot.resolve(ACTION_YML);
    try {
      return Files.readString(path);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read " + path, e);
    }
  }

  /**
   * Surefire runs tests with the module directory as the working directory ({@code openaev-api});
   * walk up looking for the action file so the test also works from the repository root.
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
  @DisplayName("the summary's open-block marker matches the gate's failure message")
  void given_actionYml_should_containTheGateFailureMarker() {
    assertThat(actionYmlText())
        .as(
            "the shadow summary's awk pattern locates the offending-signatures block by this exact"
                + " text; it must match WriteAttrGateExtension.FAILURE_MARKER verbatim")
        .contains(WriteAttrGateExtension.FAILURE_MARKER);
  }

  @Test
  @DisplayName("the summary's close-block marker matches the gate's failure message")
  void given_actionYml_should_containTheGateAttributeMarker() {
    assertThat(actionYmlText())
        .as(
            "the shadow summary's awk pattern ends the offending-signatures block on this exact"
                + " text; it must match WriteAttrGateExtension.ATTRIBUTE_MARKER verbatim")
        .contains(WriteAttrGateExtension.ATTRIBUTE_MARKER);
  }
}
