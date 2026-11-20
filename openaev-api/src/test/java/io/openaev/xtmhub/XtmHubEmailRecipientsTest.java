package io.openaev.xtmhub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Group;
import io.openaev.database.model.Role;
import io.openaev.database.model.Setting;
import io.openaev.database.model.SettingKeys;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantXtmHubRegistration;
import io.openaev.database.model.User;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.RoleRepository;
import io.openaev.database.repository.SettingRepository;
import io.openaev.database.repository.TenantXtmHubRegistrationRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.service.MailingService;
import io.openaev.service.PlatformSettingsService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PlatformRoleFixture;
import io.openaev.utils.fixtures.TenantGroupFixture;
import io.openaev.utils.fixtures.TenantRoleFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.WithoutTenantScope;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * The XTM Hub lost-connectivity emails go to the administrators of the scope they are about: the
 * platform email to every platform administrator, whatever their memberships, a tenant's email to
 * the administrators of that tenant. Both run on the scheduler thread, which carries no request
 * tenant, so the recipients come from the tenant each path names and never from the thread.
 *
 * <p>Not {@code @Transactional}: the email opens its own scoped transaction, which refuses to open
 * inside an active one, so the fixture commits and the cleanup removes what it created.
 */
@WithoutTenantScope
@WithMockUser(isAdmin = true)
@DisplayName("XTM Hub lost-connectivity email recipients")
class XtmHubEmailRecipientsTest extends IntegrationTest {

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String FLAG = SettingKeys.XTM_HUB_SHOULD_SEND_CONNECTIVITY_EMAIL.key();

  @Autowired private XtmHubEmailService xtmHubEmailService;
  @Autowired private XtmHubService xtmHubService;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private UserRepository userRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private GroupRepository groupRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private SettingRepository settingRepository;
  @Autowired private PlatformSettingsService platformSettingsService;
  @Autowired private TenantXtmHubRegistrationRepository registrationRepository;
  @Autowired private TenantScopedTransaction tenantScopedTransaction;

  @MockitoSpyBean private MailingService mailingService;
  @MockitoBean private XtmHubClient xtmHubClient;

  private String tenantB;
  private String tenantC;
  private String adminOfDefault;
  private String adminOfB;
  private Optional<String> flagBeforeTest;
  private final List<String> userIds = new ArrayList<>();
  private final List<String> groupIds = new ArrayList<>();
  private final List<String> roleIds = new ArrayList<>();
  private final List<String> registeredTenantIds = new ArrayList<>();

  @BeforeEach
  void seedOneAdministratorPerTenant() throws Exception {
    tenantB = tenantHelper.createTenant("xtmhub-recipients-b").getId();
    tenantC = tenantHelper.createTenant("xtmhub-recipients-c").getId();
    adminOfDefault = seedUser(true, Tenant.DEFAULT_TENANT_UUID);
    adminOfB = seedUser(true, tenantB);
    flagBeforeTest = platformSettingsService.setting(FLAG).map(Setting::getValue);
    writeFlag("true");
    doNothing().when(mailingService).sendEmail(anyString(), anyString(), anyList());
    doNothing().when(mailingService).sendEmail(anyString(), anyString(), anyList(), anyString());
    when(xtmHubClient.refreshRegistrationStatusAllTenants(any(), any(), any(), any()))
        .thenReturn(Map.of());
  }

  @AfterEach
  void cleanup() {
    TenantContext.clearCurrentTenant();
    registeredTenantIds.forEach(registrationRepository::deleteByTenantId);
    // Plain JDBC: the entities hold each other eagerly, so a session delete trips on the other side
    groupIds.forEach(id -> jdbcTemplate.update("delete from users_groups where group_id = ?", id));
    groupIds.forEach(id -> jdbcTemplate.update("delete from groups_roles where group_id = ?", id));
    groupIds.forEach(id -> jdbcTemplate.update("delete from groups where group_id = ?", id));
    // a platform role is deleted only by a platform-write transaction
    tenantScopedTransaction.executeAsPlatformWrite(
        TxCtx.allTenants(),
        () ->
            roleIds.forEach(id -> jdbcTemplate.update("delete from roles where role_id = ?", id)));
    userIds.forEach(id -> jdbcTemplate.update("delete from users where user_id = ?", id));
    tenantHelper.deleteCommittedTenants(tenantB, tenantC);
    if (flagBeforeTest.isPresent()) {
      writeFlag(flagBeforeTest.get());
    } else {
      tenantScopedTransaction.executeAsPlatformWrite(
          TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
          () ->
              settingRepository
                  .findByKeyAndTenantIsNull(FLAG)
                  .ifPresent(settingRepository::delete));
    }
  }

  @Nested
  @DisplayName("Tenant email")
  class TenantEmail {

    @Test
    @DisplayName(
        "given administrators and capability holders of several tenants, should send tenant B's"
            + " email to B's administrators only")
    void given_administratorsOfSeveralTenants_should_sendTenantBEmailToTenantBAdministratorsOnly() {
      // Arrange
      String holderThroughGroupOfB = seedUser(false, tenantB);
      grant(holderThroughGroupOfB, tenantB, Set.of(Capability.MANAGE_TENANT_SETTINGS));
      String holderThroughPlatformGroup = seedUser(false, tenantB);
      grant(holderThroughPlatformGroup, null, Set.of(Capability.MANAGE_TENANT_SETTINGS));
      String holderThroughGroupOfC = seedUser(false, tenantB, tenantC);
      grant(holderThroughGroupOfC, tenantC, Set.of(Capability.MANAGE_TENANT_SETTINGS));
      String plainMemberOfB = seedUser(false, tenantB);
      String holderOutsideB = seedUser(false, tenantC);
      grant(holderOutsideB, tenantC, Set.of(Capability.MANAGE_TENANT_SETTINGS));

      // Act
      xtmHubEmailService.sendTenantLostConnectivityEmail(tenantB, "http://localhost/" + tenantB);

      // Assert
      assertThat(ids(tenantRecipients(tenantB)))
          .contains(adminOfB, holderThroughGroupOfB, holderThroughPlatformGroup)
          .doesNotContain(adminOfDefault, holderThroughGroupOfC, plainMemberOfB, holderOutsideB);
    }

    @Test
    @DisplayName(
        "given a lost tenant, should send its email from the scheduled refresh to its"
            + " administrators and mark it sent")
    void given_lostTenant_should_sendItsEmailFromTheScheduledRefreshAndMarkItSent() {
      // Arrange
      TenantContext.clearCurrentTenant();
      String holderThroughGroupOfB = seedUser(false, tenantB);
      grant(holderThroughGroupOfB, tenantB, Set.of(Capability.MANAGE_TENANT_SETTINGS));
      String holderThroughGroupOfC = seedUser(false, tenantB, tenantC);
      grant(holderThroughGroupOfC, tenantC, Set.of(Capability.MANAGE_TENANT_SETTINGS));
      registerLost(tenantB);

      // Act
      xtmHubService.refreshConnectivityAllTenants();

      // Assert
      assertThat(ids(tenantRecipients(tenantB)))
          .contains(adminOfB, holderThroughGroupOfB)
          .doesNotContain(adminOfDefault, holderThroughGroupOfC);
      assertThat(isEligible(tenantB)).isFalse();
    }

    @Test
    @DisplayName(
        "given a lost tenant whose email fails, should send the other tenants' emails and the"
            + " platform email, and leave the failed tenant to be notified again")
    void given_lostTenantWhoseEmailFails_should_notifyTheOthersAndTheFailedOneAgainLater() {
      // Arrange: tenant C is lost and has no administrator (the mock user, an admin, enrolled in it
      // when it was created)
      TenantContext.clearCurrentTenant();
      tenantHelper.detachCurrentUserFromTenant(tenantC);
      registerLost(tenantC);
      registerLost(tenantB);

      // Act
      xtmHubService.refreshConnectivityAllTenants();

      // Assert
      assertThat(ids(tenantRecipients(tenantB))).contains(adminOfB);
      assertThat(isEligible(tenantB)).isFalse();
      assertThat(isEligible(tenantC)).isTrue();
      verify(mailingService).sendEmail(anyString(), anyString(), anyList());
    }
  }

  @Nested
  @DisplayName("Platform email")
  class PlatformEmail {

    @Test
    @DisplayName(
        "given platform administrators outside the default tenant, should send the platform email"
            + " to every platform administrator")
    void given_administratorsOutsideTheDefaultTenant_should_sendPlatformEmailToAllOfThem() {
      // Arrange
      TenantContext.setCurrentTenant(tenantB);
      String adminOfNoTenant = seedUser(true);
      String holderThroughPlatformGroup = seedUser(false, tenantC);
      grant(holderThroughPlatformGroup, null, Set.of(Capability.MANAGE_PLATFORM_SETTINGS));
      String holderThroughGroupOfB = seedUser(false, tenantB);
      grant(holderThroughGroupOfB, tenantB, Set.of(Capability.MANAGE_PLATFORM_SETTINGS));
      String tenantAdminOnly = seedUser(false, tenantB);
      grant(tenantAdminOnly, null, Set.of(Capability.MANAGE_TENANT_SETTINGS));

      // Act
      xtmHubEmailService.sendLostConnectivityEmail();

      // Assert
      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<User>> recipients = ArgumentCaptor.forClass(List.class);
      verify(mailingService).sendEmail(anyString(), anyString(), recipients.capture());
      assertThat(ids(recipients.getValue()))
          .contains(adminOfDefault, adminOfB, adminOfNoTenant, holderThroughPlatformGroup)
          .doesNotContain(holderThroughGroupOfB, tenantAdminOnly);
    }

    @Test
    @DisplayName(
        "given the platform email fails, should leave the flag on so the next run sends it")
    void given_platformEmailFails_should_leaveTheFlagOn() {
      // Arrange
      TenantContext.clearCurrentTenant();
      doThrow(new IllegalStateException("smtp down"))
          .when(mailingService)
          .sendEmail(anyString(), anyString(), anyList());
      registerLost(tenantB);

      // Act
      try {
        xtmHubService.refreshConnectivityAllTenants();
      } catch (RuntimeException expected) {
        // the scheduler logs it
      }

      // Assert
      assertThat(platformSettingsService.setting(FLAG).map(Setting::getValue)).contains("true");
    }

    @Test
    @DisplayName("given the platform email is sent, should switch the flag off")
    void given_platformEmailSent_should_switchTheFlagOff() {
      // Arrange
      TenantContext.clearCurrentTenant();
      registerLost(tenantB);

      // Act
      xtmHubService.refreshConnectivityAllTenants();

      // Assert
      verify(mailingService).sendEmail(anyString(), anyString(), anyList());
      assertThat(platformSettingsService.setting(FLAG).map(Setting::getValue)).contains("false");
    }
  }

  @Nested
  @DisplayName("Once per loss")
  class OncePerLoss {

    @Test
    @DisplayName("given a tenant already notified, should not send its email again")
    void given_tenantAlreadyNotified_should_notSendItsEmailAgain() {
      // Arrange
      TenantContext.clearCurrentTenant();
      registerLost(tenantB, false);

      // Act
      xtmHubService.refreshConnectivityAllTenants();

      // Assert
      verify(mailingService, never()).sendEmail(anyString(), anyString(), anyList(), eq(tenantB));
    }
  }

  private List<User> tenantRecipients(String tenantId) {
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<User>> recipients = ArgumentCaptor.forClass(List.class);
    verify(mailingService).sendEmail(anyString(), anyString(), recipients.capture(), eq(tenantId));
    return recipients.getValue();
  }

  private static List<String> ids(List<User> users) {
    return users.stream().map(User::getId).toList();
  }

  private String seedUser(boolean admin, String... tenantIds) {
    User user =
        userRepository.save(
            UserFixture.getUser(
                "Admin",
                "Recipient",
                "xtmhub-%s@unittests.invalid".formatted(UUID.randomUUID()),
                admin));
    userIds.add(user.getId());
    for (String tenantId : tenantIds) {
      tenantHelper.attachUserToTenant(user.getId(), tenantId);
    }
    return user.getId();
  }

  /**
   * Gives the user the capabilities through a role and a group of the tenant, or of the platform.
   */
  private void grant(String userId, String tenantId, Set<Capability> capabilities) {
    Tenant tenant = tenantId == null ? null : tenantRepository.findById(tenantId).orElseThrow();
    Role role =
        tenantId == null
            ? PlatformRoleFixture.getPlatformRole("xtmhub-role-" + UUID.randomUUID(), capabilities)
            : TenantRoleFixture.getRole("xtmhub-role-" + UUID.randomUUID(), capabilities);
    role.setTenant(tenant);
    Role toSave = role;
    // a platform role is written only by a platform-write transaction
    role =
        tenantId == null
            ? tenantScopedTransaction.executeAsPlatformWrite(
                TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID), () -> roleRepository.save(toSave))
            : roleRepository.save(toSave);
    roleIds.add(role.getId());
    Group group = TenantGroupFixture.getGroup("xtmhub-group-" + UUID.randomUUID());
    group.setTenant(tenant);
    group.setRoles(new ArrayList<>(List.of(role)));
    group.setUsers(new ArrayList<>(List.of(userRepository.findById(userId).orElseThrow())));
    groupIds.add(groupRepository.save(group).getId());
  }

  private void registerLost(String tenantId) {
    registerLost(tenantId, true);
  }

  private void registerLost(String tenantId, boolean eligible) {
    TenantXtmHubRegistration registration = new TenantXtmHubRegistration();
    registration.setToken("token-" + tenantId);
    registration.setRegistrationStatus(XtmHubRegistrationStatus.LOST_CONNECTIVITY);
    registration.setLastConnectivityCheck(LocalDateTime.now().minusHours(48));
    registration.setConnectivityEmailEligible(eligible);
    registration.setTenant(tenantRepository.findById(tenantId).orElseThrow());
    registrationRepository.save(registration);
    registeredTenantIds.add(tenantId);
  }

  private boolean isEligible(String tenantId) {
    return tenantScopedTransaction.execute(
        TxCtx.forTenant(tenantId),
        () ->
            registrationRepository
                .findByTenantId(tenantId)
                .orElseThrow()
                .isConnectivityEmailEligible());
  }

  private void writeFlag(String value) {
    tenantScopedTransaction.executeAsPlatformWrite(
        TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
        () -> {
          Setting setting =
              settingRepository
                  .findByKeyAndTenantIsNull(FLAG)
                  .orElseGet(() -> new Setting(FLAG, value));
          setting.setValue(value);
          settingRepository.save(setting);
        });
  }
}
