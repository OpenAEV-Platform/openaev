package io.openaev;

import static io.openaev.database.model.SettingKeys.PLATFORM_INSTANCE;
import static io.openaev.database.model.SettingKeys.PLATFORM_INSTANCE_CREATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_CLASS;

import io.openaev.config.OpenAEVConfig;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Setting;
import io.openaev.database.repository.SettingRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.utils.fixtures.LicenceFixture;
import java.security.cert.X509Certificate;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class AppTest extends IntegrationTest {

  @Mock private SettingRepository settingRepository;
  @Mock private TenantScopedTransaction tenantScopedTransaction;
  @MockitoSpyBean private EnterpriseEditionService enterpriseEditionService;
  @Autowired private OpenAEVConfig openAEVConfig;
  @Autowired private LicenseCacheManager licenseCacheManager;

  /**
   * {@code App.init()} runs its instance-id bootstrap through {@code
   * TenantScopedTransaction#executeAsPlatformWrite}: these unit tests construct {@code App}
   * directly with a mocked repository and never open a real transaction, so the mock runs the
   * supplied work inline instead of opening one.
   */
  @BeforeEach
  void stubPlatformWritePassthrough() {
    when(tenantScopedTransaction.executeAsPlatformWrite(any(TxCtx.class), any(Supplier.class)))
        .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
  }

  @DisplayName("Should throw an exception when having an incorrect instance id")
  @Test
  void shouldThrowExceptionWithInvalidInstanceId() {
    openAEVConfig.setInstanceId("pouet");
    when(settingRepository.findByKeyAndTenantIsNull(PLATFORM_INSTANCE.key()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                new App(
                        settingRepository,
                        openAEVConfig,
                        licenseCacheManager,
                        tenantScopedTransaction)
                    .init())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid UUID string: pouet");
    verify(settingRepository).findByKeyAndTenantIsNull(PLATFORM_INSTANCE.key());
    verify(settingRepository).findByKeyAndTenantIsNull(PLATFORM_INSTANCE_CREATION.key());
  }

  @DisplayName("Should update the instance id with the one in the properties")
  @Test
  void shouldUpdateInstanceIdWithProperty() {
    String uuid = UUID.randomUUID().toString();
    openAEVConfig.setInstanceId(uuid);
    new App(settingRepository, openAEVConfig, licenseCacheManager, tenantScopedTransaction).init();
    verify(settingRepository).findByKeyAndTenantIsNull(PLATFORM_INSTANCE.key());
    verify(settingRepository).findByKeyAndTenantIsNull(PLATFORM_INSTANCE_CREATION.key());
    verify(settingRepository)
        .save(
            argThat(
                setting ->
                    PLATFORM_INSTANCE.key().equals(setting.getKey())
                        && uuid.equals(setting.getValue())));
    verify(settingRepository)
        .save(argThat(setting -> PLATFORM_INSTANCE_CREATION.key().equals(setting.getKey())));
  }

  @DisplayName("Should update the instance id when there is none in db")
  @Test
  void shouldUpdateInstanceIdWhenNoneInDb() {
    // Mock le repository pour retourner empty
    when(settingRepository.findByKeyAndTenantIsNull(PLATFORM_INSTANCE.key()))
        .thenReturn(Optional.empty());
    new App(settingRepository, openAEVConfig, licenseCacheManager, tenantScopedTransaction).init();
    verify(settingRepository).findByKeyAndTenantIsNull(PLATFORM_INSTANCE.key());
    verify(settingRepository).findByKeyAndTenantIsNull(PLATFORM_INSTANCE_CREATION.key());
    verify(settingRepository)
        .save(argThat(setting -> PLATFORM_INSTANCE.key().equals(setting.getKey())));
    verify(settingRepository)
        .save(argThat(setting -> PLATFORM_INSTANCE_CREATION.key().equals(setting.getKey())));
  }

  @DisplayName(
      "Should update the instance id when there is none in db and the specified one is blank")
  @Test
  void shouldUpdateInstanceIdWhenNoneInDbAndSpecifiedOneIsBlank() {
    openAEVConfig.setInstanceId("");
    when(settingRepository.findByKeyAndTenantIsNull(PLATFORM_INSTANCE.key()))
        .thenReturn(Optional.empty());
    new App(settingRepository, openAEVConfig, licenseCacheManager, tenantScopedTransaction).init();
    verify(settingRepository).findByKeyAndTenantIsNull(PLATFORM_INSTANCE.key());
    verify(settingRepository).findByKeyAndTenantIsNull(PLATFORM_INSTANCE_CREATION.key());
    verify(settingRepository)
        .save(argThat(setting -> PLATFORM_INSTANCE.key().equals(setting.getKey())));
    verify(settingRepository)
        .save(argThat(setting -> PLATFORM_INSTANCE_CREATION.key().equals(setting.getKey())));
  }

  @DisplayName("Shouldn't update anything if instance id in db and the specified one is blank")
  @Test
  void shouldNotUpdateInstanceIdWhenInDbAndSpecifiedOneIsBlank() {
    openAEVConfig.setInstanceId("");
    when(settingRepository.findByKeyAndTenantIsNull(PLATFORM_INSTANCE.key()))
        .thenReturn(
            Optional.of(new Setting(PLATFORM_INSTANCE.key(), UUID.randomUUID().toString())));
    new App(settingRepository, openAEVConfig, licenseCacheManager, tenantScopedTransaction).init();
    verify(settingRepository).findByKeyAndTenantIsNull(PLATFORM_INSTANCE.key());
    verify(settingRepository).findByKeyAndTenantIsNull(PLATFORM_INSTANCE_CREATION.key());
    verifyNoMoreInteractions(settingRepository);
  }

  @DisplayName("Shouldn't update anything if instance id in db and there is no specified one")
  @Test
  void shouldNotUpdateInstanceIdWhenInDbAndNoSpecifiedOne() {
    openAEVConfig.setInstanceId(null);
    when(settingRepository.findByKeyAndTenantIsNull(PLATFORM_INSTANCE.key()))
        .thenReturn(
            Optional.of(new Setting(PLATFORM_INSTANCE.key(), UUID.randomUUID().toString())));
    new App(settingRepository, openAEVConfig, licenseCacheManager, tenantScopedTransaction).init();
    verify(settingRepository).findByKeyAndTenantIsNull(PLATFORM_INSTANCE.key());
    verify(settingRepository).findByKeyAndTenantIsNull(PLATFORM_INSTANCE_CREATION.key());
    verifyNoMoreInteractions(settingRepository);
  }

  private void givenStoredInstance(SettingRepository mock, String instanceId, String creationDate) {
    when(mock.findByKeyAndTenantIsNull(PLATFORM_INSTANCE.key()))
        .thenReturn(Optional.of(new Setting(PLATFORM_INSTANCE.key(), instanceId)));
    when(mock.findByKeyAndTenantIsNull(PLATFORM_INSTANCE_CREATION.key()))
        .thenReturn(
            creationDate == null
                ? Optional.empty()
                : Optional.of(new Setting(PLATFORM_INSTANCE_CREATION.key(), creationDate)));
  }

  @Nested
  @SpringBootTest
  @DirtiesContext(classMode = BEFORE_CLASS)
  @DisplayName("Licence cache test")
  @Transactional
  public class LicenceCacheTest {
    @Autowired private SettingRepository settingRepository;
    @Autowired private TenantScopedTransaction platformWriteTransaction;

    @DisplayName("Should validate licence for configured instance id")
    @Test
    void shouldValidateLicenceForConfiguredInstanceId() throws Exception {
      String correctInstanceId = UUID.randomUUID().toString();

      openAEVConfig.setInstanceId(correctInstanceId);
      openAEVConfig.setApplicationLicense(
          LicenceFixture.getLicenceStringForInstanceId(correctInstanceId));
      when(enterpriseEditionService.verifyCertificate(
              any(X509Certificate.class), any(X509Certificate.class)))
          .thenReturn(true);

      // The mocked transaction runs the bootstrap inline on this test's transaction, so this
      // transaction carries the platform-write marker that the real primitive sets for App.init().
      platformWriteTransaction.setPlatformWriteOnCurrentTransaction();
      new App(settingRepository, openAEVConfig, licenseCacheManager, tenantScopedTransaction)
          .init();

      assertThat(licenseCacheManager.isEnterpriseEditionActive()).isTrue();
    }
  }

  @DisplayName("Should write the creation date of a new instance as an ISO-8601 instant")
  @Test
  void shouldWriteCreationDateAsInstantForNewInstance() {
    openAEVConfig.setInstanceId(null);
    Instant before = Instant.now();

    new App(settingRepository, openAEVConfig, licenseCacheManager, tenantScopedTransaction).init();
    Instant after = Instant.now();

    ArgumentCaptor<Setting> saved = ArgumentCaptor.forClass(Setting.class);
    verify(settingRepository, times(2)).save(saved.capture());
    Setting creation =
        saved.getAllValues().stream()
            .filter(setting -> PLATFORM_INSTANCE_CREATION.key().equals(setting.getKey()))
            .findFirst()
            .orElseThrow();
    assertThat(Instant.parse(creation.getValue())).isBetween(before, after);
  }

  @DisplayName("Should keep the creation date when restarted with another instance id")
  @Test
  void shouldKeepCreationDateWhenInstanceIdChanges() {
    String createdAt = "2026-09-01T08:00:00Z";
    givenStoredInstance(settingRepository, UUID.randomUUID().toString(), createdAt);
    // The same Setting instance is saved at each restart: record what each save wrote
    List<String> saved = new ArrayList<>();
    when(settingRepository.save(any(Setting.class)))
        .thenAnswer(
            invocation -> {
              Setting setting = invocation.getArgument(0);
              saved.add(setting.getKey() + "=" + setting.getValue());
              return setting;
            });
    List<String> expected = new ArrayList<>();

    // Two restarts, each with another configured instance id
    for (int restart = 0; restart < 2; restart++) {
      String configuredId = UUID.randomUUID().toString();
      openAEVConfig.setInstanceId(configuredId);
      new App(settingRepository, openAEVConfig, licenseCacheManager, tenantScopedTransaction)
          .init();
      expected.add(PLATFORM_INSTANCE.key() + "=" + configuredId);
    }

    assertThat(saved).containsExactlyElementsOf(expected);
  }

  @DisplayName("Should not set a missing creation date again on an existing instance")
  @Test
  void shouldNotSetMissingCreationDateOnExistingInstance() {
    openAEVConfig.setInstanceId(UUID.randomUUID().toString());
    givenStoredInstance(settingRepository, UUID.randomUUID().toString(), null);

    new App(settingRepository, openAEVConfig, licenseCacheManager, tenantScopedTransaction).init();

    verify(settingRepository, never())
        .save(argThat(setting -> PLATFORM_INSTANCE_CREATION.key().equals(setting.getKey())));
  }

  @DisplayName("Should leave a creation date in the future unchanged")
  @Test
  void shouldLeaveFutureCreationDateUnchanged() {
    openAEVConfig.setInstanceId(null);
    String future = Instant.now().plus(365, ChronoUnit.DAYS).toString();
    givenStoredInstance(settingRepository, UUID.randomUUID().toString(), future);

    new App(settingRepository, openAEVConfig, licenseCacheManager, tenantScopedTransaction).init();

    verify(settingRepository).findByKeyAndTenantIsNull(PLATFORM_INSTANCE.key());
    verify(settingRepository).findByKeyAndTenantIsNull(PLATFORM_INSTANCE_CREATION.key());
    verifyNoMoreInteractions(settingRepository);
  }

  @DisplayName("Should rewrite a legacy creation date as the same ISO-8601 instant")
  @Test
  void shouldRewriteLegacyCreationDateAsInstant() {
    openAEVConfig.setInstanceId(null);
    String legacy = "2026-09-01 10:00:00.123";
    givenStoredInstance(settingRepository, UUID.randomUUID().toString(), legacy);

    new App(settingRepository, openAEVConfig, licenseCacheManager, tenantScopedTransaction).init();

    Instant expected = Timestamp.valueOf(legacy).toInstant();
    verify(settingRepository)
        .save(
            argThat(
                setting ->
                    PLATFORM_INSTANCE_CREATION.key().equals(setting.getKey())
                        && setting.getValue().equals(expected.toString())));
  }

  @DisplayName("Should rewrite a creation date with another offset as the same instant in UTC")
  @Test
  void shouldRewriteOffsetCreationDateAsUtcInstant() {
    openAEVConfig.setInstanceId(null);
    givenStoredInstance(
        settingRepository, UUID.randomUUID().toString(), "2026-09-01T10:00:00.123+02:00");

    new App(settingRepository, openAEVConfig, licenseCacheManager, tenantScopedTransaction).init();

    verify(settingRepository)
        .save(
            argThat(
                setting ->
                    PLATFORM_INSTANCE_CREATION.key().equals(setting.getKey())
                        && "2026-09-01T08:00:00.123Z".equals(setting.getValue())));
  }
}
