package io.openaev.migration;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.migration.MigrationSessions.contextOn;
import static io.openaev.migration.MigrationSessions.withConnection;
import static io.openaev.utils.fixtures.UserFixture.getUser;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.User;
import io.openaev.database.repository.UserRepository;
import io.openaev.opencti.connectors.impl.SecurityCoverageConnector;
import io.openaev.service.account.ReservedServiceAccount;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.SecurityCoverageFixture;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.composers.SecurityCoverageComposer;
import jakarta.persistence.EntityManager;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rows are seeded through the session, then the migration runs and the rows are read back over the
 * session connection; the test transaction ({@code @Transactional}) rolls them back.
 */
@Transactional
@DisplayName("Migration V6_20261127110000000: record the scheduler of the OpenCTI scenarios")
class RecordOpenctiScenariosSchedulerMigrationTest extends IntegrationTest {

  private static final String RECURRENCE = "P1D";

  @Autowired private V6_20261127110000000__Record_opencti_scenarios_scheduler migration;
  @Autowired private UserRepository userRepository;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private SecurityCoverageComposer securityCoverageComposer;
  @Autowired private EntityManager entityManager;

  @Test
  @DisplayName(
      "given OpenCTI scenarios, should record the connector account as scheduler of the scheduled"
          + " ones with none")
  void given_openctiScenarios_should_recordConnectorAccountAsSchedulerOfUnattributedOnes() {
    // Arrange
    User connectorAccount = connectorAccountOfDefaultTenant();
    User manualScheduler = userRepository.save(getUser());
    Scenario unattributed = coverageScenario(ScenarioFixture.getScenarioWithRecurrence(RECURRENCE));
    Scenario manuallyScheduled = ScenarioFixture.getScenarioWithRecurrence(RECURRENCE);
    manuallyScheduled.setScheduledBy(manualScheduler);
    coverageScenario(manuallyScheduled);
    Scenario unscheduled = coverageScenario(ScenarioFixture.getScenario());
    Scenario withoutCoverage =
        scenarioComposer
            .forScenario(ScenarioFixture.getScenarioWithRecurrence(RECURRENCE))
            .persist()
            .get();
    entityManager.flush();

    // Act
    withConnection(entityManager, connection -> migration.migrate(contextOn(connection)));

    // Assert
    assertThat(schedulerOf(unattributed)).isEqualTo(connectorAccount.getId());
    assertThat(schedulerOf(manuallyScheduled)).isEqualTo(manualScheduler.getId());
    assertThat(schedulerOf(unscheduled)).isNull();
    assertThat(schedulerOf(withoutCoverage)).isNull();
  }

  /** The shared test database may already hold it, from a test that registered the connector. */
  private User connectorAccountOfDefaultTenant() {
    String email =
        ReservedServiceAccount.OPENCTI_CONNECTOR.emailFor(
            SecurityCoverageConnector.idOf(DEFAULT_TENANT_UUID));
    return userRepository
        .findByEmailIgnoreCase(email)
        .orElseGet(
            () -> {
              User user = getUser();
              user.setEmail(email);
              return userRepository.save(user);
            });
  }

  private Scenario coverageScenario(Scenario scenario) {
    ScenarioComposer.Composer scenarioWrapper = scenarioComposer.forScenario(scenario);
    securityCoverageComposer
        .forSecurityCoverage(SecurityCoverageFixture.createDefaultSecurityCoverage())
        .withScenario(scenarioWrapper)
        .persist();
    return scenarioWrapper.get();
  }

  private String schedulerOf(Scenario scenario) {
    AtomicReference<String> scheduler = new AtomicReference<>();
    withConnection(
        entityManager,
        connection -> {
          try (var read =
              connection.prepareStatement(
                  "SELECT scenario_scheduled_by FROM scenarios WHERE scenario_id = ?")) {
            read.setString(1, scenario.getId());
            try (var row = read.executeQuery()) {
              assertThat(row.next()).isTrue();
              scheduler.set(row.getString(1));
            }
          }
        });
    return scheduler.get();
  }
}
