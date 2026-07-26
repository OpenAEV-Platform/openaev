package io.openaev.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

/**
 * Shared readers for well-known inject content keys, so the display path (target search adaptors)
 * and the execution path (asset resolution) can never diverge on the content schema.
 */
public final class InjectContentUtils {

  /** Inject content key holding the referenced AI target id (see the ai-redteam injector). */
  public static final String AI_TARGET_CONTENT_KEY = "ai_target";

  /** Inject content key holding the targeting mode (assets / asset groups / manual). */
  public static final String TARGET_SELECTOR_CONTENT_KEY = "target_selector";

  /** Inject content key holding the raw manual target (IP, subnet, or hostname). */
  public static final String MANUAL_TARGETS_CONTENT_KEY = "targets";

  /** Value of {@link #TARGET_SELECTOR_CONTENT_KEY} selecting a raw manual target. */
  public static final String MANUAL_TARGET_SELECTOR = "manual";

  private InjectContentUtils() {}

  /**
   * Extract the AI target id referenced from the inject content ({@code ai_target} key), if any.
   */
  public static Optional<String> contentAiTargetId(ObjectNode content) {
    if (content == null) {
      return Optional.empty();
    }
    JsonNode node = content.get(AI_TARGET_CONTENT_KEY);
    if (node == null || node.isNull()) {
      return Optional.empty();
    }
    return Optional.ofNullable(StringUtils.trimToNull(node.asText()));
  }

  /**
   * Extract the raw manual target (IP, subnet, or hostname) set via the inject content, if the
   * content selects manual targeting ({@code target_selector = "manual"}).
   */
  public static Optional<String> contentManualTarget(ObjectNode content) {
    if (content == null) {
      return Optional.empty();
    }
    JsonNode selector = content.get(TARGET_SELECTOR_CONTENT_KEY);
    if (selector == null || !MANUAL_TARGET_SELECTOR.equals(selector.asText())) {
      return Optional.empty();
    }
    JsonNode targets = content.get(MANUAL_TARGETS_CONTENT_KEY);
    if (targets == null || targets.isNull()) {
      return Optional.empty();
    }
    return Optional.ofNullable(StringUtils.trimToNull(targets.asText()));
  }
}
