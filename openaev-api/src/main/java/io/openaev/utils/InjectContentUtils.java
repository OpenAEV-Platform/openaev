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
}
