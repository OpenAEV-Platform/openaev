package io.openaev.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Grant;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Scenario;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.GrantFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.TenantGroupFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.GrantComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.composers.TenantGroupComposer;
import io.openaev.utils.mockUser.WithMockUser;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pins #8921: a grant names its resource by id with no foreign key, so the database deletes the
 * grants of a scenario, a simulation or an atomic testing with the resource itself, and leaves the
 * grants of every other resource in place.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Grants are deleted with their resource")
class GrantDeletedWithResourceTest extends IntegrationTest {

  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private TenantGroupComposer tenantGroupComposer;
  @Autowired private GrantComposer grantComposer;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    jdbc = new JdbcTemplate(dataSource);
  }

  private void grant(String resourceId, Grant.GRANT_RESOURCE_TYPE type) {
    tenantGroupComposer
        .forGroup(TenantGroupFixture.getGroup("grant-cleanup-" + resourceId))
        .withGrant(
            grantComposer.forGrant(
                GrantFixture.getGrant(resourceId, type, Grant.GRANT_TYPE.OBSERVER, null)))
        .persist();
    entityManager.flush();
  }

  private int grantsOn(String resourceId) {
    entityManager.flush();
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM grants WHERE grant_resource = ?", Integer.class, resourceId);
    return count == null ? 0 : count;
  }

  @Test
  @DisplayName("given a deleted scenario, its grants are deleted and another scenario keeps its own")
  void given_deletedScenario_should_deleteItsGrantsOnly() {
    // Arrange
    Scenario deleted = scenarioComposer.forScenario(ScenarioFixture.getScenario()).persist().get();
    Scenario kept = scenarioComposer.forScenario(ScenarioFixture.getScenario()).persist().get();
    grant(deleted.getId(), Grant.GRANT_RESOURCE_TYPE.SCENARIO);
    grant(kept.getId(), Grant.GRANT_RESOURCE_TYPE.SCENARIO);
    assertThat(grantsOn(deleted.getId())).isEqualTo(1);

    // Act
    scenarioRepository.deleteById(deleted.getId());

    // Assert
    assertThat(grantsOn(deleted.getId())).isZero();
    assertThat(grantsOn(kept.getId())).isEqualTo(1);
  }

  @Test
  @DisplayName("given a deleted simulation, its grants are deleted")
  void given_deletedSimulation_should_deleteItsGrants() {
    // Arrange
    Exercise simulation =
        exerciseComposer.forExercise(ExerciseFixture.getExercise()).persist().get();
    grant(simulation.getId(), Grant.GRANT_RESOURCE_TYPE.SIMULATION);
    assertThat(grantsOn(simulation.getId())).isEqualTo(1);

    // Act
    exerciseRepository.deleteById(simulation.getId());

    // Assert
    assertThat(grantsOn(simulation.getId())).isZero();
  }

  @Test
  @DisplayName("given a deleted atomic testing, its grants are deleted")
  void given_deletedAtomicTesting_should_deleteItsGrants() {
    // Arrange
    Inject atomicTesting =
        injectComposer.forInject(InjectFixture.getDefaultInject()).persist().get();
    grant(atomicTesting.getId(), Grant.GRANT_RESOURCE_TYPE.ATOMIC_TESTING);
    assertThat(grantsOn(atomicTesting.getId())).isEqualTo(1);

    // Act
    injectRepository.deleteByIdNative(atomicTesting.getId());

    // Assert
    assertThat(grantsOn(atomicTesting.getId())).isZero();
  }

  @Test
  @DisplayName("given a grant of another type on the same id, the delete leaves it in place")
  void given_grantOfAnotherTypeOnTheSameId_should_beKept() {
    // Arrange: a SIMULATION grant naming the scenario's id is not a grant of that scenario (another
    // grant name, so the unique index on group, resource and name accepts it).
    Scenario scenario = scenarioComposer.forScenario(ScenarioFixture.getScenario()).persist().get();
    grant(scenario.getId(), Grant.GRANT_RESOURCE_TYPE.SCENARIO);
    jdbc.update(
        "INSERT INTO grants (grant_id, grant_name, grant_group, grant_resource,"
            + " grant_resource_type) SELECT gen_random_uuid()::text, 'PLANNER', grant_group,"
            + " grant_resource, 'SIMULATION' FROM grants WHERE grant_resource = ?",
        scenario.getId());
    assertThat(grantsOn(scenario.getId())).isEqualTo(2);

    // Act
    scenarioRepository.deleteById(scenario.getId());

    // Assert
    assertThat(grantsOn(scenario.getId())).isEqualTo(1);
  }
}
