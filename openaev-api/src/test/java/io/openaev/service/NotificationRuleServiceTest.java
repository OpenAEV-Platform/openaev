package io.openaev.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.IntegrationTest;
import io.openaev.database.model.NotificationRule;
import io.openaev.database.model.NotificationRuleResourceType;
import io.openaev.database.model.NotificationRuleTrigger;
import io.openaev.database.model.NotificationRuleType;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantSettingKeys;
import io.openaev.database.repository.NotificationRuleRepository;
import io.openaev.service.settings.TenantSettingsService;
import io.openaev.utilstest.RabbitMQTestListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public class NotificationRuleServiceTest extends IntegrationTest {

  @Mock private NotificationRuleRepository notificationRuleRepository;

  @Mock private EmailNotificationService emailNotificationService;

  @Mock private TenantSettingsService tenantSettingsService;

  @Mock private PlatformSettingsService platformSettingsService;

  @InjectMocks private NotificationRuleService notificationRuleService;

  @Test
  public void test_activateNotificationRules() {
    // -------- Arrange --------
    Map<String, String> data = new HashMap<>();
    NotificationRule rule = new NotificationRule();
    rule.setResourceId("id");
    rule.setNotificationResourceType(NotificationRuleResourceType.SCENARIO);
    rule.setType(NotificationRuleType.EMAIL);
    rule.setSubject("subject");
    rule.setTrigger(NotificationRuleTrigger.DIFFERENCE);
    rule.setTenant(new Tenant("tenant-id"));

    when(notificationRuleRepository.findNotificationRuleByResourceAndTrigger(
            rule.getResourceId(), rule.getTrigger()))
        .thenReturn(List.of(rule));
    when(tenantSettingsService.resolveSettingValue(eq("tenant-id"), any(TenantSettingKeys.class)))
        .thenReturn("dark");
    when(tenantSettingsService.findSetting(eq("tenant-id"), any(String.class)))
        .thenReturn(Optional.empty());
    when(platformSettingsService.isPlatformWhiteMarked()).thenReturn(false);

    // -------- Act --------
    notificationRuleService.activateNotificationRules(
        rule.getResourceId(), rule.getTrigger(), data);

    // -------- Assert --------
    verify(emailNotificationService).sendNotification(eq(rule), any());
  }
}
