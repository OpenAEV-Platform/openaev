package io.openaev.notification.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.Notification;
import io.openaev.database.model.NotificationTriggerType;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.NotificationRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.helper.TemplateHelper;
import io.openaev.service.MailingService;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

/**
 * Publisher stage of the notifications engine: delivers a matched (trigger, users, content) tuple
 * through each of the trigger's notifiers - UI (persisted {@link Notification} + SSE via the entity
 * listener), email ({@link MailingService}) and webhook (templated HTTP call).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationDispatchService {

  private static final String DEFAULT_EMAIL_TEMPLATE_PATH =
      "classpath:email/notification_template_default_en.html";
  private static final Duration WEBHOOK_TIMEOUT = Duration.ofSeconds(30);

  private final NotificationRepository notificationRepository;
  private final UserRepository userRepository;
  private final MailingService mailingService;
  private final ResourceLoader resourceLoader;
  private final ObjectMapper mapper;
  private final OpenAEVConfig openAEVConfig;

  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(WEBHOOK_TIMEOUT).build();

  /**
   * Delivers the given content groups to every recipient of the trigger through each of its
   * notifiers. Falls back to no-op when the trigger has no notifiers.
   */
  public void dispatch(
      ResolvedNotificationTrigger trigger,
      NotificationTriggerType notificationType,
      List<String> recipientUserIds,
      List<NotificationContent.Group> groups) {
    if (groups.isEmpty() || trigger.notifiers().isEmpty()) {
      return;
    }
    for (String userId : recipientUserIds) {
      Optional<User> user = userRepository.findById(userId);
      if (user.isEmpty()) {
        continue;
      }
      for (ResolvedNotifier notifier : trigger.notifiers()) {
        try {
          switch (notifier.type()) {
            case UI -> handleUiNotification(trigger, notificationType, user.get(), groups);
            case EMAIL ->
                handleEmailNotification(trigger, notificationType, user.get(), notifier, groups);
            case WEBHOOK ->
                handleWebhookNotification(trigger, notificationType, user.get(), notifier, groups);
          }
        } catch (Exception e) {
          log.error(
              "Notification dispatch failed (trigger {}, notifier {} [{}], user {})",
              trigger.id(),
              notifier.id(),
              notifier.type(),
              userId,
              e);
        }
      }
    }
  }

  // -- UI --

  // Repository save is transactional by itself; the entity listener then streams the new
  // notification to the owning user's SSE sessions.
  private void handleUiNotification(
      ResolvedNotificationTrigger trigger,
      NotificationTriggerType notificationType,
      User user,
      List<NotificationContent.Group> groups) {
    Notification notification = new Notification();
    notification.setName(trigger.name());
    notification.setType(notificationType);
    notification.setContent(NotificationContent.toContentJson(groups));
    notification.setUser(user);
    notification.setTenant(new Tenant(trigger.tenantId()));
    notificationRepository.save(notification);
  }

  // -- EMAIL --

  private void handleEmailNotification(
      ResolvedNotificationTrigger trigger,
      NotificationTriggerType notificationType,
      User user,
      ResolvedNotifier notifier,
      List<NotificationContent.Group> groups)
      throws Exception {
    Map<String, Object> data = buildTemplateData(trigger, notificationType, user, groups);
    String subjectTemplate =
        stringConfig(notifier, "subject").orElse("[OpenAEV] ${notification_name}");
    String bodyTemplate = stringConfig(notifier, "template").orElseGet(this::defaultEmailTemplate);
    String subject = TemplateHelper.buildContentWithDataMap(subjectTemplate, data);
    String body = TemplateHelper.buildContentWithDataMap(bodyTemplate, data);
    mailingService.sendEmail(subject, body, List.of(user), trigger.tenantId());
  }

  private String defaultEmailTemplate() {
    try (InputStream inputStream =
        resourceLoader.getResource(DEFAULT_EMAIL_TEMPLATE_PATH).getInputStream()) {
      return new String(inputStream.readAllBytes());
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read default notification email template", e);
    }
  }

  // -- WEBHOOK --

  private void handleWebhookNotification(
      ResolvedNotificationTrigger trigger,
      NotificationTriggerType notificationType,
      User user,
      ResolvedNotifier notifier,
      List<NotificationContent.Group> groups)
      throws Exception {
    String url =
        stringConfig(notifier, "url")
            .orElseThrow(() -> new IllegalStateException("Webhook notifier has no url"));
    URI uri = URI.create(url);
    String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase() : "";
    if (!"http".equals(scheme) && !"https".equals(scheme)) {
      throw new IllegalStateException("Webhook notifier url must be http(s): " + url);
    }
    String verb = stringConfig(notifier, "verb").orElse("POST").toUpperCase();

    Map<String, Object> data = buildTemplateData(trigger, notificationType, user, groups);
    String body;
    Optional<String> bodyTemplate = stringConfig(notifier, "template");
    if (bodyTemplate.isPresent()) {
      body = TemplateHelper.buildContentWithDataMap(bodyTemplate.get(), data);
    } else {
      body = mapper.writeValueAsString(data);
    }

    HttpRequest.Builder requestBuilder =
        HttpRequest.newBuilder()
            .uri(uri)
            .timeout(WEBHOOK_TIMEOUT)
            .header("Content-Type", "application/json")
            .method(verb, HttpRequest.BodyPublishers.ofString(body));
    headersConfig(notifier)
        .forEach(
            (key, value) -> {
              if (key != null && !key.isBlank() && value != null) {
                requestBuilder.header(key, value);
              }
            });
    HttpResponse<String> response =
        httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() >= 400) {
      throw new IllegalStateException(
          "Webhook notifier call failed with status " + response.statusCode());
    }
  }

  // -- TEMPLATE DATA --

  private Map<String, Object> buildTemplateData(
      ResolvedNotificationTrigger trigger,
      NotificationTriggerType notificationType,
      User user,
      List<NotificationContent.Group> groups) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("notification_name", trigger.name());
    data.put("notification_type", notificationType.name().toLowerCase());
    data.put("platform_url", openAEVConfig.getBaseUrl());
    data.put("user_email", user.getEmail());
    data.put("user_name", user.getNameOrEmail());
    data.put("content", NotificationContent.toContentJson(groups));
    return data;
  }

  private Optional<String> stringConfig(ResolvedNotifier notifier, String key) {
    Object value = notifier.configuration().get(key);
    if (value instanceof String stringValue && !stringValue.isBlank()) {
      return Optional.of(stringValue);
    }
    return Optional.empty();
  }

  @SuppressWarnings("unchecked")
  private Map<String, String> headersConfig(ResolvedNotifier notifier) {
    Object value = notifier.configuration().get("headers");
    if (value instanceof Map<?, ?> mapValue) {
      Map<String, String> headers = new HashMap<>();
      mapValue.forEach(
          (key, headerValue) -> {
            if (key != null && headerValue != null) {
              headers.put(String.valueOf(key), String.valueOf(headerValue));
            }
          });
      return headers;
    }
    return Map.of();
  }
}
