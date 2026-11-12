package io.openaev.executors.caldera.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.BannerMessage;
import io.openaev.database.model.Executor;
import io.openaev.database.model.Setting;
import io.openaev.database.model.SettingKeys;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.SettingRepository;
import io.openaev.executors.caldera.client.CalderaExecutorClient;
import io.openaev.executors.caldera.config.CalderaExecutorConfig;
import io.openaev.service.AgentService;
import io.openaev.service.EndpointService;
import io.openaev.service.InjectorService;
import io.openaev.service.PlatformSettingsService;
import io.openaev.utilstest.WithoutTenantScope;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The Caldera-unavailable banner is one platform row derived from the reports of every Caldera
 * executor of every tenant, which are persisted. It shows while one executor's Caldera does not
 * answer, whichever executor ran last and whichever node restarted, and two writers of the first
 * row do not collide.
 *
 * <p>Deliberately NOT {@code @Transactional}: each banner write commits in a transaction of its
 * own, on the thread that asked for it, as in production. The row is removed after each test.
 */
@WithoutTenantScope
@DisplayName("Caldera unavailable banner across tenants")
class CalderaBannerAcrossTenantsTest extends IntegrationTest {

  private static final String TENANT_B = "caldera-banner-tenant-b";
  private static final BannerMessage.BANNER_KEYS BANNER =
      BannerMessage.BANNER_KEYS.CALDERA_UNAVAILABLE;
  private static final String BANNER_KEY = SettingKeys.PLATFORM_BANNER + "." + BANNER.key();

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private PlatformSettingsService platformSettingsService;
  @Autowired private SettingRepository settingRepository;
  @Autowired private TenantScopedTransaction tenantScopedTransaction;

  /** Runs the banner writes one at a time, off the thread of the run, as the scheduler does. */
  private final ExecutorService bannerThread = Executors.newSingleThreadExecutor();

  private final CalderaExecutorClient reachable = mock(CalderaExecutorClient.class);
  private final CalderaExecutorClient unreachable = mock(CalderaExecutorClient.class);

  private final Executor entityA = entityOf(Tenant.DEFAULT_TENANT_UUID, "caldera-a");
  private final Executor entityB = entityOf(TENANT_B, "caldera-b");
  private final Executor entitySecondOfB = entityOf(TENANT_B, "caldera-b-2");

  private CalderaExecutorService tenantA;
  private CalderaExecutorService tenantB;
  private CalderaExecutorService secondOfTenantB;

  private Executor entityOf(String tenantId, String executorId) {
    Executor executor = new Executor();
    executor.setId(executorId);
    executor.setName("Caldera");
    executor.setType("openaev_caldera_executor");
    executor.setTenantId(tenantId);
    return executor;
  }

  private CalderaExecutorService executorOf(Executor executor, CalderaExecutorClient client) {
    return new CalderaExecutorService(
        executor,
        client,
        mock(CalderaExecutorConfig.class),
        mock(CalderaExecutorContextService.class),
        mock(EndpointService.class),
        mock(InjectorService.class),
        platformSettingsService,
        mock(AgentService.class),
        tenantScopedTransaction,
        bannerThread);
  }

  private boolean bannerShown() {
    return settingRepository.findByKeyAndTenantIsNull(BANNER_KEY).isPresent();
  }

  private void awaitBannerWrites() throws Exception {
    bannerThread.submit(() -> {}).get(30, TimeUnit.SECONDS);
  }

  private void stop(Executor executor) {
    tenantScopedTransaction.executeAsPlatformWrite(
        TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
        () ->
            platformSettingsService.reportMessageSource(
                BANNER, CalderaExecutorService.bannerSource(executor), false));
  }

  private void clearBanner() {
    stop(entityA);
    stop(entityB);
    stop(entitySecondOfB);
    tenantScopedTransaction.executeAsPlatformWrite(
        TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
        () -> platformSettingsService.cleanMessage(BANNER));
  }

  @BeforeEach
  void setUp() {
    when(reachable.agents()).thenReturn(List.of());
    when(unreachable.agents()).thenThrow(new IllegalStateException("caldera is down"));
    clearBanner();
    tenantA = executorOf(entityA, reachable);
    tenantB = executorOf(entityB, unreachable);
    secondOfTenantB = executorOf(entitySecondOfB, reachable);
  }

  @AfterEach
  void restore() {
    bannerThread.shutdown();
    clearBanner();
  }

  @Nested
  @DisplayName("two tenants, one reachable and one not")
  class TwoTenants {

    @Test
    @DisplayName("given_theUnreachableTenantRunsFirst_should_showTheBanner")
    void given_theUnreachableTenantRunsFirst_should_showTheBanner() throws Exception {
      // Act: the reachable tenant runs last
      tenantB.run();
      tenantA.run();
      awaitBannerWrites();

      // Assert
      assertTrue(bannerShown());
    }

    @Test
    @DisplayName("given_theUnreachableTenantRunsLast_should_showTheBanner")
    void given_theUnreachableTenantRunsLast_should_showTheBanner() throws Exception {
      // Act
      tenantA.run();
      tenantB.run();
      awaitBannerWrites();

      // Assert
      assertTrue(bannerShown());
    }

    @Test
    @DisplayName("given_everyTenantAnswers_should_clearTheBanner")
    void given_everyTenantAnswers_should_clearTheBanner() throws Exception {
      // Arrange: the unreachable tenant raised it
      tenantB.run();
      tenantA.run();
      awaitBannerWrites();
      assertTrue(bannerShown());
      doReturn(List.of()).when(unreachable).agents();

      // Act
      tenantB.run();
      awaitBannerWrites();

      // Assert
      assertFalse(bannerShown());
    }

    @Test
    @DisplayName("given_theUnreachableTenantStops_should_clearItsPartOfTheBanner")
    void given_theUnreachableTenantStops_should_clearItsPartOfTheBanner() throws Exception {
      // Arrange
      tenantB.run();
      awaitBannerWrites();
      assertTrue(bannerShown());

      // Act: the integration of that tenant stops, as CalderaExecutorIntegration#innerStop does
      stop(entityB);

      // Assert
      assertFalse(bannerShown());
    }
  }

  @Nested
  @DisplayName("several nodes, several executors")
  class SeveralNodes {

    @Test
    @DisplayName("given_aNodeRestartsWhileATenantIsDown_should_keepTheBanner")
    void given_aNodeRestartsWhileATenantIsDown_should_keepTheBanner() throws Exception {
      // Arrange: the tenant that is down raised the banner
      tenantB.run();
      awaitBannerWrites();
      assertTrue(bannerShown());

      // Act: a node that just started holds no report in memory, and its first executor to run
      // is the one whose Caldera answers
      tenantA.run();
      awaitBannerWrites();

      // Assert
      assertTrue(bannerShown());
    }

    @Test
    @DisplayName("given_oneExecutorOfATenantIsDown_should_keepTheBannerWhenAnotherOfItAnswers")
    void given_oneExecutorOfATenantIsDown_should_keepTheBannerWhenAnotherOfItAnswers()
        throws Exception {
      // Arrange
      tenantB.run();
      awaitBannerWrites();

      // Act: a second Caldera of the same tenant answers
      secondOfTenantB.run();
      awaitBannerWrites();

      // Assert
      assertTrue(bannerShown());
    }

    @Test
    @DisplayName("given_oneExecutorOfATenantStops_should_keepTheBannerOfTheOthers")
    void given_oneExecutorOfATenantStops_should_keepTheBannerOfTheOthers() throws Exception {
      // Arrange
      tenantB.run();
      secondOfTenantB.run();
      awaitBannerWrites();
      assertTrue(bannerShown());

      // Act: the healthy executor of the tenant stops
      stop(entitySecondOfB);

      // Assert
      assertTrue(bannerShown());
    }

    @Test
    @DisplayName("given_aReportIsNotRenewed_should_ignoreItAndDeleteIt")
    void given_aReportIsNotRenewed_should_ignoreItAndDeleteIt() throws Exception {
      // Arrange: a node died six minutes after it reported that its Caldera is down
      String report = "platform_banner_source." + BANNER.key() + ".dead-node";
      long sixMinutesAgo = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(6);
      tenantScopedTransaction.executeAsPlatformWrite(
          TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
          () -> {
            settingRepository.save(new Setting(report, String.valueOf(sixMinutesAgo)));
            platformSettingsService.errorMessage(BANNER);
          });
      assertTrue(bannerShown());

      // Act: a Caldera that answers reports
      tenantA.run();
      awaitBannerWrites();

      // Assert
      assertFalse(bannerShown());
      assertTrue(settingRepository.findByKeyAndTenantIsNull(report).isEmpty());
    }
  }

  @Nested
  @DisplayName("two writers of the first row")
  class FirstWrite {

    @Test
    @DisplayName("given_twoConcurrentFirstWrites_should_leaveOneRowAndFailNeither")
    void given_twoConcurrentFirstWrites_should_leaveOneRowAndFailNeither() throws Exception {
      // Arrange: the first writer holds its transaction open until the second has finished, or
      // for a while when the second has to wait for it
      ExecutorService writers = Executors.newFixedThreadPool(2);
      CountDownLatch secondDone = new CountDownLatch(1);
      try {
        Future<?> first =
            writers.submit(
                () ->
                    tenantScopedTransaction.executeAsPlatformWrite(
                        TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
                        () -> {
                          platformSettingsService.errorMessage(BANNER);
                          try {
                            secondDone.await(1500, TimeUnit.MILLISECONDS);
                          } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                          }
                        }));
        Thread.sleep(300);

        // Act
        Future<?> second =
            writers.submit(
                () -> {
                  tenantScopedTransaction.executeAsPlatformWrite(
                      TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
                      () -> platformSettingsService.errorMessage(BANNER));
                  secondDone.countDown();
                });

        // Assert
        assertDoesNotThrow(() -> get(first));
        assertDoesNotThrow(() -> get(second));
        assertEquals(
            1,
            settingRepository.findAllByKeyInAndTenantIsNull(List.of(BANNER_KEY)).size(),
            "one platform row for the banner");
      } finally {
        writers.shutdownNow();
      }
    }

    private void get(Future<?> future) throws InterruptedException, ExecutionException {
      try {
        future.get(30, TimeUnit.SECONDS);
      } catch (java.util.concurrent.TimeoutException e) {
        throw new IllegalStateException(e);
      }
    }
  }
}
