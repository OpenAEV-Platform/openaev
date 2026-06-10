package io.openaev.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.database.model.NotificationRule;
import io.openaev.database.model.NotificationRuleResourceType;
import io.openaev.database.model.NotificationRuleTrigger;
import io.openaev.database.model.NotificationRuleType;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantSettingKeys;
import io.openaev.database.repository.NotificationRuleRepository;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.service.settings.TenantSettingsService;
import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class NotificationRuleServiceTest {

  @Mock private EntityManager entityManager;
  @Mock private Session session;
  @Mock private NotificationRuleRepository notificationRuleRepository;

  @Mock private UserService userService;
  @Mock private ScenarioService scenarioService;
  @Mock private EmailNotificationService emailNotificationService;

  @Mock private TenantSettingsService tenantSettingsService;
  @Mock private PlatformSettingsService platformSettingsService;

  // Explicit construction ensures the exact mock instances are injected into the final fields,
  // avoiding the JIT-caching issue that occurs when Mockito falls back to reflection-based
  // field injection on private-final fields (Lombok @RequiredArgsConstructor) in Java 21.
  private NotificationRuleService notificationRuleService;

  @BeforeEach
  void setUp() {
    notificationRuleService =
        new NotificationRuleService(
            entityManager,
            notificationRuleRepository,
            userService,
            scenarioService,
            emailNotificationService,
            platformSettingsService,
            tenantSettingsService);
  }

  @Test
  public void given_notificationRuleWithEmailType_should_sendNotification() {
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
    // doReturn avoids calling the generic unwrap() method on the mock before the stub is
    // registered, which prevents a null-return when Mockito cannot infer the generic type <T>.
    doReturn(session).when(entityManager).unwrap(Session.class);
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
