package io.openaev.xtmone;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.service.detection_remediation.DetectionRemediationCrowdstrikeResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Formats raw agent responses (JSON content emitted by detection.generate agents) into the HTML /
 * SPL string expected by the OpenAEV editors, reusing the legacy {@link
 * DetectionRemediationCrowdstrikeResponse#formateRules()} formatter for CrowdStrike and producing
 * the SPL query directly for Splunk.
 *
 * <p>Centralising this in the backend keeps the OpenAEV ↔ XTM One contract in one place and avoids
 * duplicating parsing/formatting logic in every frontend caller.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XtmOneFormattingService {

  private static final Pattern FENCE_PATTERN =
      Pattern.compile("```(?:json)?\\s*\\n?([\\s\\S]*?)\\n?```");

  private final ObjectMapper objectMapper;

  /**
   * Parse the agent's content as detection.generate JSON and format it into the editor-ready
   * string. Returns the raw {@code content} when parsing fails so users always get something
   * usable.
   */
  public String formatRemediationRules(String content, String collectorType) {
    if (content == null || content.isBlank()) {
      return content;
    }

    String jsonStr = content.trim();
    Matcher fenceMatch = FENCE_PATTERN.matcher(jsonStr);
    if (fenceMatch.find()) {
      jsonStr = fenceMatch.group(1).trim();
    }

    try {
      JsonNode root = objectMapper.readTree(jsonStr);
      if (collectorType != null && collectorType.toLowerCase().contains("splunk")) {
        String splunk = formatSplunk(root);
        return splunk != null ? splunk : content;
      }
      DetectionRemediationCrowdstrikeResponse parsed =
          objectMapper.treeToValue(root, DetectionRemediationCrowdstrikeResponse.class);
      if (parsed.getRules() == null || parsed.getRules().isEmpty()) {
        return content;
      }
      return parsed.formateRules();
    } catch (Exception e) {
      log.debug("[XTM One] Could not parse remediation agent JSON, returning raw content.", e);
      return content;
    }
  }

  /**
   * Extract the SPL query from the agent payload. Supports both legacy ({@code spl_query} at the
   * root) and current ({@code rules[].spl_query}) shapes.
   */
  private String formatSplunk(JsonNode root) {
    JsonNode topQuery = root.get("spl_query");
    if (topQuery != null && topQuery.isTextual() && !topQuery.asText().isBlank()) {
      return topQuery.asText();
    }
    JsonNode rules = root.get("rules");
    if (rules == null || !rules.isArray() || rules.isEmpty()) {
      return null;
    }
    List<String> queries = new ArrayList<>();
    for (JsonNode rule : rules) {
      JsonNode q = rule.get("spl_query");
      if (q != null && q.isTextual() && !q.asText().isBlank()) {
        queries.add(q.asText());
      }
    }
    if (queries.isEmpty()) {
      return null;
    }
    return String.join("\n\n", queries);
  }
}
