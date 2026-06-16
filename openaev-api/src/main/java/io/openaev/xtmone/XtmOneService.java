package io.openaev.xtmone;

import io.openaev.api.xtmone.dto.ChatbotAgentOutput;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.rest.settings.response.PlatformSettings;
import io.openaev.service.PlatformSettingsService;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class XtmOneService {

  private final XtmOneConfig config;
  private final XtmOneClient client;
  private final PlatformSettingsService platformSettingsService;
  private final EnterpriseEditionService eeService;

  private static final List<Map<String, String>> DEFAULT_INTENTS =
      List.of(
          Map.of(
              "name",
              "global.assistant",
              "description",
              "General-purpose assistant for adversary emulation"),
          Map.of("name", "summarize", "description", "Summarize content or findings"),
          Map.of("name", "make.it.shorter", "description", "Shorten or condense content"),
          Map.of("name", "make.it.longer", "description", "Expand or elaborate content"),
          Map.of("name", "fix.spelling", "description", "Fix spelling and grammar"),
          Map.of("name", "change.tone", "description", "Change tone of content"),
          Map.of("name", "explain", "description", "Explain content in simple terms"),
          Map.of(
              "name",
              "ttp.extractor",
              "description",
              "Extract MITRE ATT&CK TTPs from documents and text"),
          Map.of(
              "name",
              "detection.generate",
              "description",
              "Generate detection and remediation rules for security collectors"),
          Map.of(
              "name",
              "generate.message",
              "description",
              "Generate email messages for adversary emulation injects"),
          Map.of(
              "name",
              "generate.media",
              "description",
              "Generate media articles for adversary emulation scenarios"));

  /** Intent catalog received from the last successful registration. */
  @SuppressWarnings("unchecked")
  private volatile List<Map<String, Object>> discoveredIntentCatalog = Collections.emptyList();

  /** Returns the intent catalog discovered from the last XTM One registration. */
  public List<Map<String, Object>> getIntentCatalog() {
    return discoveredIntentCatalog;
  }

  /**
   * Returns the list of enabled agents bound to the given intent in the discovered catalog. Empty
   * when no agent is bound (or when no catalog has been discovered yet).
   */
  @SuppressWarnings("unchecked")
  public List<ChatbotAgentOutput> listEnabledAgentsForIntent(String intent) {
    return discoveredIntentCatalog.stream()
        .filter(e -> Objects.equals(intent, e.get("intent")))
        .flatMap(
            e -> {
              Object agentsObj = e.get("agents");
              if (agentsObj instanceof List<?> agentList) {
                return agentList.stream()
                    .filter(Map.class::isInstance)
                    .map(a -> (Map<String, Object>) a)
                    .filter(a -> Boolean.TRUE.equals(a.get("enabled")));
              }
              return java.util.stream.Stream.empty();
            })
        .map(
            a ->
                // Use Objects.toString so both missing keys and explicit null JSON values map to
                // the empty string. String.valueOf(getOrDefault(...)) would surface the literal
                // "null" when the catalog ships an agent entry with a null id/name/slug.
                new ChatbotAgentOutput(
                    Objects.toString(a.get("agent_id"), ""),
                    Objects.toString(a.get("agent_name"), ""),
                    Objects.toString(a.get("agent_slug"), ""),
                    Objects.toString(a.get("agent_description"), "")))
        .toList();
  }

  /**
   * Resolves the agent slug to use for the given intent. When a client supplies a slug it must be
   * present in the enabled catalog for that intent; otherwise we fall back to the first enabled
   * agent. Returns {@code null} when no agent is registered for the intent.
   *
   * @throws org.springframework.web.server.ResponseStatusException with status 400 when the
   *     supplied slug is not enabled for the requested intent.
   */
  public String resolveAgentSlugForIntent(String intent, String requestedSlug) {
    List<ChatbotAgentOutput> enabled = listEnabledAgentsForIntent(intent);
    if (enabled.isEmpty()) {
      return null;
    }
    if (requestedSlug == null || requestedSlug.isBlank()) {
      return enabled.get(0).slug();
    }
    boolean known = enabled.stream().anyMatch(a -> requestedSlug.equals(a.slug()));
    if (!known) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Agent slug is not enabled for this intent");
    }
    return requestedSlug;
  }

  /**
   * Ensures the supplied agent slug is registered as an enabled agent on at least one intent in the
   * discovered catalog. Used by the generic streaming/non-streaming endpoints that aren't bound to
   * a specific intent — they still must refuse to forward arbitrary client-controlled slugs.
   *
   * @throws org.springframework.web.server.ResponseStatusException with status 400 when the slug is
   *     not found.
   */
  @SuppressWarnings("unchecked")
  public String requireEnabledAgentSlug(String requestedSlug) {
    if (requestedSlug == null || requestedSlug.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "agent_slug is required");
    }
    boolean known =
        discoveredIntentCatalog.stream()
            .flatMap(
                e -> {
                  Object agentsObj = e.get("agents");
                  if (agentsObj instanceof List<?> agentList) {
                    return agentList.stream()
                        .filter(Map.class::isInstance)
                        .map(a -> (Map<String, Object>) a);
                  }
                  return java.util.stream.Stream.empty();
                })
            .filter(a -> Boolean.TRUE.equals(a.get("enabled")))
            .anyMatch(a -> requestedSlug.equals(a.get("agent_slug")));
    if (!known) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown or disabled agent_slug");
    }
    return requestedSlug;
  }

  /**
   * Register this platform with XTM One. Called on every connectivity tick (the /register endpoint
   * is an upsert, so repeated calls are safe). Sends the current license state, business vertical,
   * and declared intents for agent binding.
   */
  @Transactional(readOnly = true)
  public void autoRegister() {
    if (!config.isConfigured()) {
      return;
    }
    try {
      PlatformSettings settings = platformSettingsService.findSettings();
      String licensePem = null;
      try {
        licensePem = eeService.getEncodedCertificate();
      } catch (Exception ignored) {
        // CE platform or NFR — certificate not available as PEM
      }

      String licenseType = null;
      try {
        var license = eeService.getEnterpriseEditionInfo();
        if (license != null && license.isLicenseValidated()) {
          licenseType =
              license.getType() != null ? license.getType().name().toLowerCase() : "enterprise";
        }
      } catch (Exception ignored) {
        // license info not available
      }

      String version = platformSettingsService.getPlatformVersion();
      String platformUrl =
          settings.getPlatformBaseUrl() != null ? settings.getPlatformBaseUrl() : "";
      String platformName =
          settings.getPlatformName() != null ? settings.getPlatformName() : "OpenAEV Platform";

      config.setPlatformUrl(platformUrl);
      config.setPlatformVersion(version != null ? version : "");

      Map<String, Object> result =
          client.register(
              "openaev",
              platformUrl,
              platformName,
              version != null ? version : "",
              settings.getPlatformId() != null ? settings.getPlatformId() : "",
              licensePem,
              licenseType,
              "aev",
              DEFAULT_INTENTS);
      if (result != null) {
        Object chatToken = result.get("chat_web_token");
        if (chatToken instanceof String s && !s.isBlank()) {
          config.setDiscoveredWebToken(s);
          log.info("[XTM One] Chat web token discovered from registration");
        }
        Object catalog = result.get("intent_catalog");
        if (catalog instanceof List<?> catalogList) {
          discoveredIntentCatalog =
              catalogList.stream()
                  .filter(Map.class::isInstance)
                  .map(e -> (Map<String, Object>) e)
                  .toList();
          int agentCount =
              discoveredIntentCatalog.stream()
                  .mapToInt(e -> e.get("agents") instanceof List<?> a ? a.size() : 0)
                  .sum();
          log.info(
              "[XTM One] Intent catalog updated (intents={}, agents={})",
              discoveredIntentCatalog.size(),
              agentCount);
        }
        log.info(
            "[XTM One] Registration successful (ee_enabled={})",
            result.getOrDefault("ee_enabled", false));
      } else {
        log.warn("[XTM One] Registration failed, will retry on next tick");
      }
    } catch (Exception e) {
      log.warn("[XTM One] Registration failed, will retry on next tick", e);
    }
  }
}
