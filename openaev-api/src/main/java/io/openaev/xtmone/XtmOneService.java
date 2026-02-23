package io.openaev.xtmone;

import io.openaev.database.model.Token;
import io.openaev.database.model.User;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.rest.settings.response.PlatformSettings;
import io.openaev.service.PlatformSettingsService;
import io.openaev.service.UserService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Log
public class XtmOneService {

  private final XtmOneConfig config;
  private final XtmOneClient client;
  private final PlatformSettingsService platformSettingsService;
  private final EnterpriseEditionService eeService;
  private final UserService userService;

  @org.springframework.beans.factory.annotation.Value(
      "${openbas.admin.token:${openaev.admin.token:#{null}}}")
  private String adminToken;

  /**
   * Register this platform with XTM One. Called on every connectivity tick (the /register endpoint
   * is an upsert, so repeated calls are safe). Sends the current license state and all users with
   * their API tokens so XTM One creates one integration per matched user.
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
        // CE platform — no EE license available
      }

      List<Map<String, String>> userEntries = collectUserEntries();

      String version = platformSettingsService.getPlatformVersion();

      Map<String, Object> result =
          client.register(
              "openaev",
              settings.getPlatformBaseUrl() != null ? settings.getPlatformBaseUrl() : "",
              settings.getPlatformName() != null ? settings.getPlatformName() : "OpenAEV Platform",
              version != null ? version : "",
              settings.getPlatformId() != null ? settings.getPlatformId() : "",
              licensePem,
              adminToken,
              userEntries);
      if (result != null) {
        Object chatToken = result.get("chat_web_token");
        if (chatToken instanceof String s && !s.isBlank()) {
          config.setDiscoveredWebToken(s);
          log.info("[XTM One] Chat web token discovered from registration");
        }
        log.info(
            "[XTM One] Registration successful (ee_enabled="
                + result.getOrDefault("ee_enabled", false)
                + ", user_integrations="
                + result.getOrDefault("user_integrations", 0)
                + ")");
      } else {
        log.warning("[XTM One] Registration failed, will retry on next tick");
      }
    } catch (Exception e) {
      log.warning("[XTM One] Registration failed: " + e.getMessage() + ", will retry on next tick");
    }
  }

  private List<Map<String, String>> collectUserEntries() {
    List<Map<String, String>> entries = new ArrayList<>();
    for (User user : userService.users()) {
      if (user.getEmail() == null) continue;
      List<Token> tokens = user.getTokens();
      if (tokens == null || tokens.isEmpty()) continue;
      String tokenValue = tokens.getFirst().getValue();
      if (tokenValue == null || tokenValue.isBlank()) continue;
      entries.add(
          Map.of(
              "email",
              user.getEmail(),
              "display_name",
              user.getName() != null ? user.getName() : user.getEmail(),
              "api_key",
              tokenValue));
    }
    return entries;
  }
}
