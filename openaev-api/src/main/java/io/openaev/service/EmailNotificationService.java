package io.openaev.service;

import static io.openaev.helper.TemplateHelper.buildContextualContent;

import io.openaev.database.model.NotificationRule;
import io.openaev.execution.ExecutionContext;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailNotificationService {

  private final MailingService mailingService;
  private final ResourceLoader resourceLoader;

  public void sendNotification(
      @NotNull final NotificationRule rule, @NotNull final Map<String, String> data) {

    // get the template
    String template = getTemplate(rule);

    // replace the dynamic variable
    ExecutionContext executionContext = new ExecutionContext(rule.getOwner(), null);
    executionContext.putAll(data);

    try {

      String body = buildContextualContent(template, executionContext);

      // send the email
      mailingService.sendEmail(rule.getSubject(), body, List.of(rule.getOwner()));

    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Build the name of the template based on the rule information The template name needs to have
   * the format: notification_template_[resource type]_[trigger]_[language].html
   *
   * @param rule
   * @return template content
   */
  private String getTemplate(@NotNull final NotificationRule rule) {

    // TODO update this method to get the template in the user's language and default to english if
    // not possible
    String templatePath =
        String.format(
            "classpath:email/notification_template_%s_%s_%s.html",
            rule.getNotificationResourceType().name().toLowerCase(),
            rule.getTrigger().name().toLowerCase(),
            "en");

    try (InputStream inputStream = resourceLoader.getResource(templatePath).getInputStream()) {
      return new String(inputStream.readAllBytes());
    } catch (IOException e) {
      throw new RuntimeException("Failed to read template", e);
    }
  }
}
