package io.openaev.service.tenants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.openaev.IntegrationTest;
import io.openaev.config.OpenAEVConfig;
import io.openaev.config.QueueConfig;
import io.openaev.config.RabbitmqConfig;
import io.openaev.database.model.Tenant;
import io.openaev.multitenancy.DependenciesManager;
import io.openaev.service.RabbitmqService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.DatabaseSnapshotManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
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
import org.springframework.transaction.PlatformTransactionManager;
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
 */
@WithMockUser(isAdmin = true)
@Import(TenantQueueServiceBrokerLifecycleTest.FailingOnboardingConfiguration.class)
@DisplayName("Tenant broker objects lifecycle")
class TenantQueueServiceBrokerLifecycleTest extends IntegrationTest {

  /**
   * A {@link DependenciesManager} that fails the onboarding of the tenants it is told to. It runs
   * after {@link TenantQueueService}, so the broker objects already exist when it throws, inside
   * the real {@link TenantService#create} loop.
   */
  static class FailingOnboardingManager implements DependenciesManager {

    final Set<String> failingTenantNames = ConcurrentHashMap.newKeySet();

    @Override
    public void createDependencyForTenant(Tenant tenant) {
      if (failingTenantNames.contains(tenant.getName())) {
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

  private final List<String> tenantIds = new ArrayList<>();

  @BeforeEach
  void setUp() {
    failingOnboardingManager.failingTenantNames.clear();
  }

  @AfterEach
  void cleanup() {
    failingOnboardingManager.failingTenantNames.clear();
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
      return false;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
