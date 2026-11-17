package io.openaev.utils;

import static io.openaev.utils.InjectContentUtils.MANUAL_TARGETS_CONTENT_KEY;
import static io.openaev.utils.InjectContentUtils.MANUAL_TARGET_SELECTOR;
import static io.openaev.utils.InjectContentUtils.TARGET_SELECTOR_CONTENT_KEY;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Inject content utils")
class InjectContentUtilsTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static ObjectNode content(String selector, String targets) {
    ObjectNode content = MAPPER.createObjectNode();
    content.put(TARGET_SELECTOR_CONTENT_KEY, selector);
    content.put(MANUAL_TARGETS_CONTENT_KEY, targets);
    return content;
  }

  @Nested
  @DisplayName("Manual targets")
  class ManualTargets {

    @Test
    @DisplayName("given_commaSeparatedTargets_should_splitTrimAndDeduplicateInOrder")
    void given_commaSeparatedTargets_should_splitTrimAndDeduplicateInOrder() {
      // Arrange
      ObjectNode content =
          content(MANUAL_TARGET_SELECTOR, " 127.0.0.1, scanme.nmap.org,,10.0.0.0/24,127.0.0.1 ");

      // Act
      List<String> targets = InjectContentUtils.contentManualTargets(content);

      // Assert
      assertThat(targets).containsExactly("127.0.0.1", "scanme.nmap.org", "10.0.0.0/24");
    }

    @Test
    @DisplayName("given_nonManualSelector_should_returnNoTarget")
    void given_nonManualSelector_should_returnNoTarget() {
      // Arrange
      ObjectNode content = content("asset-groups", "127.0.0.1");

      // Act
      List<String> targets = InjectContentUtils.contentManualTargets(content);

      // Assert
      assertThat(targets).isEmpty();
    }

    @Test
    @DisplayName("given_nullContent_should_returnNoTarget")
    void given_nullContent_should_returnNoTarget() {
      // Act
      List<String> targets = InjectContentUtils.contentManualTargets(null);

      // Assert
      assertThat(targets).isEmpty();
    }
  }
}
