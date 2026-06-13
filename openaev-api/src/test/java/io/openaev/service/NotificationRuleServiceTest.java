package io.openaev.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

/**
 * Pure Mockito unit test: the service is exercised with mocks only, so no Spring context is needed
 * (previously this was a {@code @SpringBootTest}, which only slowed the suite down).
 *
 * <p>The service is constructed explicitly instead of via {@code @InjectMocks}: {@link Session}
 * extends {@link EntityManager} (Hibernate 6), so constructor injection has two candidate mocks for
 * the {@code EntityManager} parameter and may inject the {@code session} mock depending on field
 * iteration order — making the test order/JVM dependent (the {@code session} mock's {@code unwrap}
 * is not stubbed, yielding an NPE).
 */
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
    when(entityManager.unwrap(Session.class)).thenReturn(session);
    when(tenantSettingsService.resolveSettingValue(eq("tenant-id"), any(TenantSettingKeys.class)))
        .thenReturn("dark");
    when(tenantSettingsService.findSetting(eq("tenant-id"), any(String.class)))
        .thenReturn(Optional.empty());
    when(platformSettingsService.isPlatformWhiteMarked()).thenReturn(false);

    // -------- Act --------
    notificationRuleService.activateNotificationRules(
        rule.getResourceId(), rule.getTrigger(), data);

    // -------- Assert --------
    verify(emailNotificationService).sendNotification(eq(rule), any(), anyString());
  }
}
