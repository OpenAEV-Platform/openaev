package io.openaev.service;

import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.database.model.*;
import io.openaev.database.repository.NotificationRuleRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.utils.ImageUtils;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class NotificationRuleService {
  private final NotificationRuleRepository notificationRuleRepository;

  private final UserService userService;
  private final ScenarioService scenarioService;
  private final EmailNotificationService emailNotificationService;
  private final PlatformSettingsService platformSettingsService;

  public Optional<NotificationRule> findById(final String id) {
    return notificationRuleRepository.findById(id);
  }

  public List<NotificationRule> findAll() {
    return StreamSupport.stream(notificationRuleRepository.findAll().spliterator(), false)
        .collect(Collectors.toList());
  }

  public List<NotificationRule> findNotificationRuleByResource(@NotBlank final String resourceId) {
    return notificationRuleRepository.findNotificationRuleByResource(resourceId);
  }

  public List<NotificationRule> findNotificationRuleByResourceAndUser(
      @NotBlank final String resourceId, @NotBlank final String userId) {

    return notificationRuleRepository.findNotificationRuleByResourceAndUser(resourceId, userId);
  }

  public NotificationRule createNotificationRule(@NotNull final NotificationRule notificationRule) {
    User currentUser = userService.currentUser();
    if (NotificationRuleResourceType.SCENARIO.equals(
        notificationRule.getNotificationResourceType())) {
      // verify if the scenario exists
      if (scenarioService.scenario(notificationRule.getResourceId()) == null) {
        throw new ElementNotFoundException(
            "Scenario not found with id: " + notificationRule.getResourceId());
      }
    } else {
      // currently only scenario is supported
      throw new UnsupportedOperationException(
          "Unsupported resource type: " + notificationRule.getNotificationResourceType().name());
    }
    notificationRule.setOwner(currentUser);
    return notificationRuleRepository.save(notificationRule);
  }

  public NotificationRule updateNotificationRule(
      @NotBlank final String id, @NotBlank final String subject) {
    // verify that the rule exists
    NotificationRule notificationRule =
        notificationRuleRepository
            .findById(id)
            .orElseThrow(
                () -> new ElementNotFoundException("NotificationRule not found with id: " + id));

    notificationRule.setSubject(subject);

    return notificationRuleRepository.save(notificationRule);
  }

  public void deleteNotificationRule(@NotBlank final String id) {
    // verify that the rule exists
    notificationRuleRepository
        .findById(id)
        .orElseThrow(
            () -> new ElementNotFoundException("NotificationRule not found with id: " + id));

    notificationRuleRepository.deleteById(id);
  }

  public Page<NotificationRule> searchNotificationRule(
      @NotNull final SearchPaginationInput searchPaginationInput) {
    return buildPaginationJPA(
        notificationRuleRepository::findAll, searchPaginationInput, NotificationRule.class);
  }

  @Transactional
  public void activateNotificationRules(
      @NotNull final String resourceId,
      @NotNull final NotificationRuleTrigger trigger,
      @NotNull final Map<String, String> data) {
    List<NotificationRule> rules =
        notificationRuleRepository.findNotificationRuleByResourceAndTrigger(resourceId, trigger);
    // TODO extract this logic from this method
    // TODO fix: custom logo only working with png because of the html template
    // add data about custom logo and whitemarked platform
    if (!rules.isEmpty()) {
      // check if there is a custom logo
      String theme =
          platformSettingsService
              .setting(SettingKeys.DEFAULT_THEME.name())
              .map(Setting::getValue)
              .orElseGet(SettingKeys.DEFAULT_THEME::defaultValue);
      String b64CustomLogo =
          platformSettingsService
              .setting(theme + "." + Theme.THEME_KEYS.LOGO_URL.name().toLowerCase())
              .map(setting -> ImageUtils.downloadImageAndEncodeBase64(setting.getValue()))
              .orElse("");
      data.put("custom_logo_b64", b64CustomLogo);
      data.put(
          "hide_filigran_logo", Boolean.toString(platformSettingsService.isPlatformWhiteMarked()));
    }

    for (NotificationRule rule : rules) {
      if (NotificationRuleType.EMAIL.equals(rule.getType())) {
        emailNotificationService.sendNotification(rule, data);
      }
    }
  }
}
