package io.openaev.service;

import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.database.model.*;
import io.openaev.database.model.TenantSettingKeys;
import io.openaev.database.repository.NotificationRuleRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.service.settings.TenantSettingsService;
import io.openaev.utils.ImageUtils;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
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
  private final TenantSettingsService tenantSettingsService;

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
    if (rules.isEmpty()) {
      return;
    }

    Map<String, List<NotificationRule>> rulesByTenant =
        rules.stream().collect(Collectors.groupingBy(rule -> rule.getTenant().getId()));

    Map<String, String> themeByTenant = new HashMap<>();
    boolean hideFiligranLogo = platformSettingsService.isPlatformWhiteMarked();

    for (Map.Entry<String, List<NotificationRule>> entry : rulesByTenant.entrySet()) {
      String tenantId = entry.getKey();
      List<NotificationRule> tenantRules = entry.getValue();

      String theme =
          themeByTenant.computeIfAbsent(
              tenantId,
              id -> tenantSettingsService.resolveSettingValue(id, TenantSettingKeys.DEFAULT_THEME));

      // TODO fix: custom logo only working with png because of the html template
      String logoKey = theme + "." + Theme.THEME_KEYS.LOGO_URL.name().toLowerCase();
      String b64CustomLogo =
          tenantSettingsService
              .findSetting(tenantId, logoKey)
              .or(() -> platformSettingsService.setting(logoKey))
              .map(setting -> ImageUtils.downloadImageAndEncodeBase64(setting.getValue()))
              .orElse("");

      Map<String, String> tenantData = new HashMap<>(data);
      tenantData.put("custom_logo_b64", b64CustomLogo);
      tenantData.put("hide_filigran_logo", Boolean.toString(hideFiligranLogo));

      for (NotificationRule rule : tenantRules) {
        if (NotificationRuleType.EMAIL.equals(rule.getType())) {
          emailNotificationService.sendNotification(rule, tenantData);
        }
      }
    }
  }
}
