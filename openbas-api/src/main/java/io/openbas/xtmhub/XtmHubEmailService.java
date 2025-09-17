package io.openbas.xtmhub;

import static io.openbas.helper.TemplateHelper.buildContextualContent;

import io.openbas.database.model.Capability;
import io.openbas.database.model.User;
import io.openbas.database.repository.UserRepository;
import io.openbas.execution.ExecutionContext;
import io.openbas.rest.settings.response.PlatformSettings;
import io.openbas.service.MailingService;
import io.openbas.service.PlatformSettingsService;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class XtmHubEmailService {
  private static final String EMAIL_SUBJECT =
      "Action Required: Re-register OpenAEV Platform Due to Lost Connectivity with XTM Hub";
  private static final String TEMPLATE_PATH_FORMAT = "classpath:email/generic_template_%s.html";

  private final UserRepository userRepository;
  private final MailingService mailingService;
  private final PlatformSettingsService platformSettingsService;
  private final ResourceLoader resourceLoader;

  public void sendLostConnectivityEmail() {
    List<User> administrators = findPlatformAdministrators();
    if (administrators.isEmpty()) {
      log.warn("No administrators found to send XTM Hub lost connectivity email");
      return;
    }

    try {
      String emailBody = buildEmailBody(administrators.getFirst());
      mailingService.sendEmail(EMAIL_SUBJECT, emailBody, administrators);
      log.info("XTM Hub lost connectivity email sent to {} administrators", administrators.size());
    } catch (Exception e) {
      log.error("Failed to send lost connectivity email", e);
      throw new RuntimeException(e);
    }
  }

  private String buildEmailBody(User user) throws Exception {
    PlatformSettings settings = platformSettingsService.findSettings();
    String body = createBodyContent(settings.getPlatformBaseUrl());
    String template = getTemplate();
    ExecutionContext executionContext = new ExecutionContext(user, null);
    executionContext.put("body", body);
    executionContext.put("platformTitle", settings.getPlatformName());
    return buildContextualContent(template, executionContext);
  }

  private String createBodyContent(String baseUrl) {
    return String.format(
        """
      <p>We wanted to inform you that the connectivity between OpenAEV and the XTM Hub has been lost.
      As a result, the integration is currently inactive.</p>
      <p>To restore functionality, please navigate to the <strong>Settings</strong> section and
      re-initiate the registration process for the OpenAEV platform. This will re-establish the
      connection and allow continued use of the integrated features.</p>
      <p>If you need assistance during the process, don't hesitate to reach out.</p>
      <p>
        <a href="%s">Access OpenAEV</a><br />
        Best,<br />
        Filigran Team<br />
      </p>
      """,
        baseUrl);
  }

  private List<User> findPlatformAdministrators() {
    return userRepository.usersHavingCapability(Capability.MANAGE_PLATFORM_SETTINGS.toString());
  }

  private String getTemplate() {
    String templatePath = String.format(TEMPLATE_PATH_FORMAT, "en");
    try (InputStream inputStream = resourceLoader.getResource(templatePath).getInputStream()) {
      return new String(inputStream.readAllBytes());
    } catch (IOException e) {
      throw new RuntimeException("Failed to read template", e);
    }
  }
}
