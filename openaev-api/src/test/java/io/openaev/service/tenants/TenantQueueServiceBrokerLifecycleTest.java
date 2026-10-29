package io.openaev.service.tenants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ShutdownSignalException;
import io.openaev.IntegrationTest;
import io.openaev.config.OpenAEVConfig;
import io.openaev.config.QueueConfig;
import io.openaev.config.RabbitmqConfig;
import io.openaev.database.model.Tenant;
import io.openaev.multitenancy.DependenciesManager;
import io.openaev.multitenancy.DependenciesManagerException;
import io.openaev.service.RabbitmqService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.DatabaseSnapshotManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A tenant's broker objects follow the tenant: they exist while its row exists, and go when the row
 * goes, whether it is deleted or never committed.
 *
 * <p>Onboarding declares, per configured queue, a quorum queue and its topic exchange, plus one
 * connector exchange for the tenant. The broker is not transactional, so a rollback of the
 * onboarding transaction does not remove them, and nothing removes them later because no tenant row
 * is left to purge. Each case is observed on the broker itself, by passive declaration.
 *
 * <p>NOT {@code @Transactional}: the commit or rollback of the onboarding is the subject, so each
 * case owns its transactions.
 *
 * <p>{@link RabbitmqService} is a spy that calls the real broker, except where a case makes one
 * broker call fail on purpose.
 */
@WithMockUser(isAdmin = true)
@Import(TenantQueueServiceBrokerLifecycleTest.FailingOnboardingConfiguration.class)
@DisplayName("Tenant broker objects lifecycle")
class TenantQueueServiceBrokerLifecycleTest extends IntegrationTest {

  /**
   * A {@link DependenciesManager} that fails the onboarding of the tenants it is told to. It runs
   * after {@link TenantQueueService}, inside the real {@link TenantService#create} loop, and
   * records the tenant's broker objects as they stand when it throws.
   */
  static class FailingOnboardingManager implements DependenciesManager {

    final Set<String> failingTenantNames = ConcurrentHashMap.newKeySet();
    final Map<String, List<String>> brokerObjectsAtFailure = new ConcurrentHashMap<>();
    volatile Function<String, List<String>> brokerProbe;

    @Override
    public void createDependencyForTenant(Tenant tenant) {
      if (failingTenantNames.contains(tenant.getName())) {
        brokerObjectsAtFailure.put(tenant.getId(), brokerProbe.apply(tenant.getId()));
        throw new IllegalStateException("onboarding failure for tenant " + tenant.getId());
      }
    }

    @Override
    public void deleteDependencyForTenant(String tenantId) {
      // Nothing to clean: this manager only fails the create side.
    }

    @Override
    public List<Class<? extends DependenciesManager>> getPrerequisite() {
      return List.of(TenantQueueService.class);
    }
  }

  @TestConfiguration
  static class FailingOnboardingConfiguration {
    @Bean
    FailingOnboardingManager failingOnboardingManager() {
      return new FailingOnboardingManager();
    }
  }

  @Autowired private TenantService tenantService;
  @Autowired private TenantQueueService tenantQueueService;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private FailingOnboardingManager failingOnboardingManager;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private ConnectionFactory connectionFactory;
  @Autowired private RabbitmqConfig rabbitmqConfig;
  @Autowired private OpenAEVConfig openAEVConfig;
  @Autowired private DatabaseSnapshotManager databaseSnapshotManager;
  @MockitoSpyBean private RabbitmqService rabbitmqService;

  private final List<String> tenantIds = new ArrayList<>();

  /** Tenants among which the next broker deletion fails, once; empty when no failure is armed. */
  private final Set<String> failNextBrokerDeletionAmong = ConcurrentHashMap.newKeySet();

  private final AtomicReference<String> tenantWhoseBrokerDeletionFailed = new AtomicReference<>();
  private final AtomicBoolean failDeclarationAfterFirstQueue = new AtomicBoolean();
  private final AtomicReference<List<String>> brokerObjectsAtDeclarationFailure =
      new AtomicReference<>();

  /** While set, every broker deletion of a tenant's objects fails, as with the broker down. */
  private final AtomicBoolean brokerDeletionsDown = new AtomicBoolean();

  /** While the broker is down, whether a deletion removes the first queue before it fails. */
  private final AtomicBoolean deleteFirstQueueBeforeFailing = new AtomicBoolean();

  private final Set<String> tenantsWhoseBrokerDeletionWasRefused = ConcurrentHashMap.newKeySet();

  @BeforeEach
  void setUp() throws Exception {
    failingOnboardingManager.failingTenantNames.clear();
    failingOnboardingManager.brokerObjectsAtFailure.clear();
    failingOnboardingManager.brokerProbe = this::existingBrokerObjects;
    doAnswer(
            invocation -> {
              String tenantId = invocation.getArgument(0);
              List<QueueConfig> configs = invocation.getArgument(1);
              if (brokerDeletionsDown.get() && configs.size() > 1) {
                tenantsWhoseBrokerDeletionWasRefused.add(tenantId);
                if (deleteFirstQueueBeforeFailing.get()) {
                  rabbitmqService.deleteQueuesForTenant(tenantId, configs.subList(0, 1));
                }
                throw new IOException("broker unavailable for tenant " + tenantId);
              }
              if (failNextBrokerDeletionAmong.contains(tenantId)
                  && tenantWhoseBrokerDeletionFailed.compareAndSet(null, tenantId)) {
                failNextBrokerDeletionAmong.clear();
                throw new IOException("broker deletion failure for tenant " + tenantId);
              }
              return invocation.callRealMethod();
            })
        .when(rabbitmqService)
        .deleteQueuesForTenant(anyString(), anyList());
    doAnswer(
            invocation -> {
              String tenantId = invocation.getArgument(0);
              List<QueueConfig> configs = invocation.getArgument(1);
              if (configs.size() > 1 && failDeclarationAfterFirstQueue.getAndSet(false)) {
                rabbitmqService.declareQueuesForTenant(tenantId, configs.subList(0, 1));
                brokerObjectsAtDeclarationFailure.set(existingBrokerObjects(tenantId));
                throw new IOException("declaration failure for tenant " + tenantId);
              }
              return invocation.callRealMethod();
            })
        .when(rabbitmqService)
        .declareQueuesForTenant(anyString(), any());
  }

  @AfterEach
  void cleanup() {
    failingOnboardingManager.failingTenantNames.clear();
    failNextBrokerDeletionAmong.clear();
    failDeclarationAfterFirstQueue.set(false);
    brokerDeletionsDown.set(false);
    deleteFirstQueueBeforeFailing.set(false);
    for (String tenantId : tenantIds) {
      tenantHelper.deleteCommittedTenants(tenantId);
    }
    tenantIds.clear();
  }

  @Nested
  @DisplayName("Deleting a tenant's broker objects")
  class Delete {

    @Test
    @DisplayName("Removes every queue and exchange the onboarding declared")
    void given_aCommittedTenant_should_removeEveryBrokerObjectItsOnboardingDeclared()
        throws Exception {
      // Arrange
      String tenantId = tenantHelper.createTenant("broker-lifecycle-delete").getId();
      tenantIds.add(tenantId);
      assertThat(existingBrokerObjects(tenantId)).isEqualTo(brokerObjectNames(tenantId));

      // Act
      tenantQueueService.deleteDependencyForTenant(tenantId);

      // Assert
      assertThat(existingBrokerObjects(tenantId)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Test helper teardown")
  class HelperTeardown {

    @Test
    @DisplayName("deleteCommittedTenants removes the tenant's broker objects")
    void given_aCommittedTenant_should_leaveNoBrokerObjectAfterDeleteCommittedTenants() {
      // Arrange
      String tenantId;
      try {
        tenantId = tenantHelper.createTenant("broker-lifecycle-helper").getId();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
      tenantIds.add(tenantId);
      assertThat(existingBrokerObjects(tenantId)).isEqualTo(brokerObjectNames(tenantId));

      // Act
      tenantHelper.deleteCommittedTenants(tenantId);

      // Assert
      assertThat(tenantRepository.existsById(tenantId)).isFalse();
      assertThat(existingBrokerObjects(tenantId)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Snapshot restore between test classes")
  class SnapshotRestore {

    @Test
    @DisplayName("A committed tenant removed by the restore takes its broker objects with it")
    void given_aCommittedTenantLeftForTheRestore_should_leaveNoBrokerObjectAfterIt() {
      // Arrange
      String tenantId;
      try {
        tenantId = tenantHelper.createTenant("broker-lifecycle-restore").getId();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
      tenantIds.add(tenantId);
      assertThat(existingBrokerObjects(tenantId)).isEqualTo(brokerObjectNames(tenantId));

      // Act
      databaseSnapshotManager.restoreToSnapshotState();

      // Assert
      assertThat(tenantRepository.existsById(tenantId)).isFalse();
      assertThat(existingBrokerObjects(tenantId)).isEmpty();
    }

    @Test
    @DisplayName("A tenant whose broker teardown fails does not stop the teardown of the others")
    void given_aBrokerTeardownFailingForTheFirstTenant_should_stillRemoveTheOtherTenantsObjects() {
      // Arrange
      String firstTenantId = committedTenant("broker-lifecycle-restore-a");
      String secondTenantId = committedTenant("broker-lifecycle-restore-b");
      failNextBrokerDeletionAmong.addAll(List.of(firstTenantId, secondTenantId));

      // Act
      Throwable thrown = catchThrowable(databaseSnapshotManager::restoreToSnapshotState);

      // Assert
      String failedTenantId = tenantWhoseBrokerDeletionFailed.get();
      assertThat(failedTenantId).isIn(firstTenantId, secondTenantId);
      String otherTenantId = failedTenantId.equals(firstTenantId) ? secondTenantId : firstTenantId;
      assertThat(thrown).isNotNull().hasMessageContaining(failedTenantId);
      assertThat(thrown.getMessage()).doesNotContain(otherTenantId);
      assertThat(tenantRepository.existsById(failedTenantId)).isFalse();
      assertThat(tenantRepository.existsById(otherTenantId)).isFalse();
      assertThat(existingBrokerObjects(failedTenantId))
          .isEqualTo(brokerObjectNames(failedTenantId));
      assertThat(existingBrokerObjects(otherTenantId)).isEmpty();
    }

    @Test
    @DisplayName("A tenant whose broker teardown failed is torn down by the next restore")
    void given_aBrokerTeardownThatFailedAtARestore_should_beRetriedByTheNextRestore() {
      // Arrange
      String tenantId = committedTenant("broker-lifecycle-restore-retry");
      failNextBrokerDeletionAmong.add(tenantId);
      Throwable firstRestore = catchThrowable(databaseSnapshotManager::restoreToSnapshotState);
      assertThat(firstRestore).isNotNull().hasMessageContaining(tenantId);
      assertThat(tenantRepository.existsById(tenantId)).isFalse();
      assertThat(existingBrokerObjects(tenantId)).isEqualTo(brokerObjectNames(tenantId));

      // Act
      databaseSnapshotManager.restoreToSnapshotState();

      // Assert
      assertThat(existingBrokerObjects(tenantId)).isEmpty();
    }

    private String committedTenant(String name) {
      String tenantId;
      try {
        tenantId = tenantHelper.createTenant(name).getId();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
      tenantIds.add(tenantId);
      assertThat(existingBrokerObjects(tenantId)).isEqualTo(brokerObjectNames(tenantId));
      return tenantId;
    }
  }

  @Nested
  @DisplayName("Onboarding that does not commit")
  class RolledBackOnboarding {

    @Test
    @DisplayName("A later manager failing the onboarding leaves no broker object")
    void given_anOnboardingFailedByALaterManager_should_leaveNoBrokerObject() {
      // Arrange
      Tenant tenant =
          TenantFixture.getTenant(
              "broker-lifecycle-failed-" + UUID.randomUUID().toString().substring(0, 8));
      failingOnboardingManager.failingTenantNames.add(tenant.getName());

      // Act
      Throwable thrown = catchThrowable(() -> tenantService.create(tenant));

      // Assert
      assertThat(thrown).hasMessageStartingWith("onboarding failure for tenant ");
      String tenantId = tenant.getId();
      assertThat(tenantId).isNotNull();
      tenantIds.add(tenantId);
      assertThat(failingOnboardingManager.brokerObjectsAtFailure.get(tenantId))
          .as("the broker objects existed when the later manager failed")
          .isEqualTo(brokerObjectNames(tenantId));
      assertThat(tenantRepository.existsById(tenantId)).isFalse();
      assertThat(existingBrokerObjects(tenantId)).isEmpty();
    }

    @Test
    @DisplayName("A declaration failing after its first queue leaves no broker object")
    void given_aDeclarationFailingAfterItsFirstQueue_should_leaveNoBrokerObject() {
      // Arrange
      Tenant tenant =
          TenantFixture.getTenant(
              "broker-lifecycle-partial-" + UUID.randomUUID().toString().substring(0, 8));
      failDeclarationAfterFirstQueue.set(true);

      // Act
      Throwable thrown = catchThrowable(() -> tenantService.create(tenant));

      // Assert
      assertThat(thrown)
          .isInstanceOf(DependenciesManagerException.class)
          .hasRootCauseMessage("declaration failure for tenant " + tenant.getId());
      String tenantId = tenant.getId();
      tenantIds.add(tenantId);
      assertThat(brokerObjectsAtDeclarationFailure.get())
          .as("the declaration created part of the broker objects before failing")
          .isNotEmpty()
          .hasSizeLessThan(brokerObjectNames(tenantId).size())
          .isSubsetOf(brokerObjectNames(tenantId));
      assertThat(tenantRepository.existsById(tenantId)).isFalse();
      assertThat(existingBrokerObjects(tenantId)).isEmpty();
    }

    @Test
    @DisplayName("A surrounding transaction rolled back after onboarding leaves no broker object")
    void given_aSurroundingTransactionRolledBackAfterOnboarding_should_leaveNoBrokerObject() {
      // Arrange
      AtomicReference<String> tenantId = new AtomicReference<>();
      AtomicReference<List<String>> objectsBeforeRollback = new AtomicReference<>();
      TransactionTemplate transaction = new TransactionTemplate(transactionManager);

      // Act
      Throwable thrown =
          catchThrowable(
              () ->
                  transaction.executeWithoutResult(
                      status -> {
                        try {
                          tenantId.set(
                              tenantHelper.createTenant("broker-lifecycle-rollback").getId());
                        } catch (Exception e) {
                          throw new IllegalStateException(e);
                        }
                        objectsBeforeRollback.set(existingBrokerObjects(tenantId.get()));
                        throw new IllegalStateException("failure after onboarding");
                      }));

      // Assert
      assertThat(thrown).hasMessage("failure after onboarding");
      tenantIds.add(tenantId.get());
      assertThat(objectsBeforeRollback.get()).isEqualTo(brokerObjectNames(tenantId.get()));
      assertThat(tenantRepository.existsById(tenantId.get())).isFalse();
      assertThat(existingBrokerObjects(tenantId.get())).isEmpty();
    }

    @Test
    @DisplayName("A committed onboarding keeps its broker objects")
    void given_anOnboardingInACommittedTransaction_should_keepItsBrokerObjects() {
      // Arrange
      AtomicReference<String> tenantId = new AtomicReference<>();
      TransactionTemplate transaction = new TransactionTemplate(transactionManager);

      // Act
      transaction.executeWithoutResult(
          status -> {
            try {
              tenantId.set(tenantHelper.createTenant("broker-lifecycle-commit").getId());
            } catch (Exception e) {
              throw new IllegalStateException(e);
            }
          });

      // Assert
      tenantIds.add(tenantId.get());
      assertThat(tenantRepository.existsById(tenantId.get())).isTrue();
      assertThat(existingBrokerObjects(tenantId.get()))
          .isEqualTo(brokerObjectNames(tenantId.get()));
    }

    @Test
    @DisplayName("An onboarding whose transaction outcome is unknown keeps its broker objects")
    void given_anOnboardingWhoseTransactionOutcomeIsUnknown_should_keepItsBrokerObjects()
        throws Exception {
      // Arrange
      Tenant tenant = TenantFixture.getTenant("broker-lifecycle-unknown");
      tenant.setId(UUID.randomUUID().toString());
      tenantIds.add(tenant.getId());
      List<TransactionSynchronization> synchronizations;
      TransactionSynchronizationManager.initSynchronization();
      try {
        tenantQueueService.createDependencyForTenant(tenant);
        synchronizations = TransactionSynchronizationManager.getSynchronizations();
      } finally {
        TransactionSynchronizationManager.clearSynchronization();
      }
      assertThat(synchronizations).hasSize(1);
      assertThat(existingBrokerObjects(tenant.getId()))
          .isEqualTo(brokerObjectNames(tenant.getId()));

      // Act
      synchronizations.getFirst().afterCompletion(TransactionSynchronization.STATUS_UNKNOWN);
      tenantQueueService.retryFailedRollbackCleanups();

      // Assert
      assertThat(existingBrokerObjects(tenant.getId()))
          .isEqualTo(brokerObjectNames(tenant.getId()));
    }
  }

  @Nested
  @DisplayName("Rollback cleanup that fails")
  class FailedRollbackCleanup {

    @Test
    @DisplayName("Keeps the objects while the broker is down and deletes them once it is back")
    void given_aRollbackCleanupFailingWhileTheBrokerIsDown_should_deleteTheObjectsOnceItIsBack() {
      // Arrange
      String tenantId = onboardingRolledBackWhileTheBrokerIsDown("broker-lifecycle-down-");
      assertThat(existingBrokerObjects(tenantId)).isEqualTo(brokerObjectNames(tenantId));
      tenantQueueService.retryFailedRollbackCleanups();
      assertThat(existingBrokerObjects(tenantId))
          .as("a retry while the broker is still down deletes nothing")
          .isEqualTo(brokerObjectNames(tenantId));
      brokerDeletionsDown.set(false);

      // Act
      tenantQueueService.retryFailedRollbackCleanups();

      // Assert
      assertThat(existingBrokerObjects(tenantId)).isEmpty();
    }

    @Test
    @DisplayName("Deletes the remaining objects of a cleanup that failed partway")
    void given_aRollbackCleanupFailingAfterItsFirstQueue_should_deleteTheRemainingObjectsOnRetry() {
      // Arrange
      deleteFirstQueueBeforeFailing.set(true);
      String tenantId = onboardingRolledBackWhileTheBrokerIsDown("broker-lifecycle-partly-");
      assertThat(existingBrokerObjects(tenantId))
          .as("the failed cleanup deleted part of the broker objects")
          .isNotEmpty()
          .hasSizeLessThan(brokerObjectNames(tenantId).size())
          .isSubsetOf(brokerObjectNames(tenantId));
      brokerDeletionsDown.set(false);

      // Act
      tenantQueueService.retryFailedRollbackCleanups();

      // Assert
      assertThat(existingBrokerObjects(tenantId)).isEmpty();
    }

    /** Rolls back an onboarding through a later manager, with every broker deletion failing. */
    private String onboardingRolledBackWhileTheBrokerIsDown(String namePrefix) {
      Tenant tenant =
          TenantFixture.getTenant(namePrefix + UUID.randomUUID().toString().substring(0, 8));
      failingOnboardingManager.failingTenantNames.add(tenant.getName());
      brokerDeletionsDown.set(true);
      Throwable thrown = catchThrowable(() -> tenantService.create(tenant));
      assertThat(thrown).hasMessageStartingWith("onboarding failure for tenant ");
      String tenantId = tenant.getId();
      tenantIds.add(tenantId);
      assertThat(tenantRepository.existsById(tenantId)).isFalse();
      assertThat(tenantsWhoseBrokerDeletionWasRefused)
          .as("the rollback cleanup ran and failed")
          .contains(tenantId);
      return tenantId;
    }
  }

  private String tenantPrefix(String tenantId) {
    return rabbitmqConfig.getPrefix() + "-" + tenantId;
  }

  private List<String> queueNames(String tenantId) {
    return queueConfigNames().map(name -> tenantPrefix(tenantId) + "_execution_" + name).toList();
  }

  private List<String> exchangeNames(String tenantId) {
    return Stream.concat(
            queueConfigNames().map(name -> tenantPrefix(tenantId) + "_amqp." + name + ".exchange"),
            Stream.of(tenantPrefix(tenantId) + RabbitmqService.EXCHANGE_KEY))
        .toList();
  }

  private Stream<String> queueConfigNames() {
    return openAEVConfig.getQueueConfig().values().stream().map(QueueConfig::getQueueName);
  }

  /** Every broker object the tenant's onboarding declares, queues first, then exchanges. */
  private List<String> brokerObjectNames(String tenantId) {
    return Stream.concat(queueNames(tenantId).stream(), exchangeNames(tenantId).stream()).toList();
  }

  /** The tenant's broker objects that exist on the broker, in {@link #brokerObjectNames} order. */
  private List<String> existingBrokerObjects(String tenantId) {
    try (Connection connection = connectionFactory.newConnection()) {
      return Stream.concat(
              queueNames(tenantId).stream().filter(name -> exists(connection, name, true)),
              exchangeNames(tenantId).stream().filter(name -> exists(connection, name, false)))
          .toList();
    } catch (IOException | java.util.concurrent.TimeoutException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Whether the object exists. Only the broker's 404 reads as absent: any other failure of the
   * probe is rethrown, so it can never pass for a deleted object.
   */
  private static boolean exists(Connection connection, String name, boolean queue) {
    // A passive declaration of a missing object closes its channel, so one channel per probe.
    try (Channel channel = connection.createChannel()) {
      if (queue) {
        channel.queueDeclarePassive(name);
      } else {
        channel.exchangeDeclarePassive(name);
      }
      return true;
    } catch (IOException e) {
      if (isNotFound(e)) {
        return false;
      }
      throw new IllegalStateException("Cannot probe broker object " + name, e);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static boolean isNotFound(IOException e) {
    return e.getCause() instanceof ShutdownSignalException signal
        && signal.getReason() instanceof AMQP.Channel.Close close
        && close.getReplyCode() == AMQP.NOT_FOUND;
  }
}
