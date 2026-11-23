package io.openaev.rest.comcheck;

import static io.openaev.config.TenantUriUtils.TENANT_BASE_PATH;
import static io.openaev.rest.comcheck.ComcheckApi.ALREADY_ANSWERED;
import static io.openaev.rest.comcheck.ComcheckApi.CLOSED;
import static io.openaev.rest.comcheck.ComcheckApi.COMCHECK_URI;
import static io.openaev.utils.TagLinkJdbc.column;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Comcheck;
import io.openaev.database.model.ComcheckStatus;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.ComcheckRepository;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.utils.fixtures.ComcheckFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utilstest.RowTenantScope;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The page a comcheck mail links to ({@code <base>/comcheck/<status id>}) has no tenant in its URL,
 * so it calls the public route with no tenant either, anonymously. The status id alone names the
 * comcheck, whatever the tenant of its simulation. The rows are read through JDBC on the test
 * connection, which the statement inspector does not rewrite. The class arms the production list.
 */
@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Public comcheck route")
class ComcheckPublicRouteTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String RECEIVE_DATE =
      "SELECT status_receive_date FROM comchecks_statuses WHERE status_id = ?";
  private static final String COMCHECK_STATE =
      "SELECT comcheck_state FROM comchecks WHERE comcheck_id = ?";

  @Autowired private MockMvc mvc;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private ComcheckRepository comcheckRepository;
  @Autowired private RowTenantScope rowTenantScope;

  private String tenantB;
  private String comcheckId;
  private String statusId;

  @BeforeEach
  void seedComcheckInSecondTenant() {
    tenantB =
        tenantRepository
            .save(TenantFixture.getTenant("comcheck-public-" + UUID.randomUUID()))
            .getId();
    Seeded seeded = seedInSecondTenant(1, Comcheck.COMCHECK_STATUS.RUNNING);
    comcheckId = seeded.comcheckId();
    statusId = seeded.statusIds().getFirst();
  }

  private record Seeded(String comcheckId, List<String> statusIds) {}

  /** A comcheck of the second tenant in the given state, waiting for the answer of each player. */
  private Seeded seedInSecondTenant(int players, Comcheck.COMCHECK_STATUS state) {
    Seeded[] seeded = new Seeded[1];
    rowTenantScope.write(tenantB, () -> seeded[0] = seedComcheck(tenantB, players, state));
    entityManager.flush();
    entityManager.clear();
    // The mail link is opened by a browser with no session, on a thread that carries no tenant.
    TenantContext.clearCurrentTenant();
    return seeded[0];
  }

  /** A simulation of the tenant, and a comcheck of it with one status per player. */
  private Seeded seedComcheck(String tenantId, int players, Comcheck.COMCHECK_STATUS state) {
    Exercise simulation = ExerciseFixture.createDefaultExercise();
    simulation.setTenant(new Tenant(tenantId));
    simulation = exerciseRepository.save(simulation);
    Comcheck comcheck = ComcheckFixture.createComcheck(simulation);
    comcheck.setState(state);
    for (int i = 0; i < players; i++) {
      User player =
          userRepository.save(
              UserFixture.getUser(
                  "first", "last", "comcheck-" + UUID.randomUUID() + "@unittests.invalid", false));
      ComcheckStatus status = new ComcheckStatus(player);
      status.setComcheck(comcheck);
      comcheck.getComcheckStatus().add(status);
    }
    comcheck = comcheckRepository.save(comcheck);
    return new Seeded(
        comcheck.getId(),
        comcheck.getComcheckStatus().stream().map(ComcheckStatus::getId).toList());
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Anonymous caller")
  class AnonymousCaller {

    @Test
    @DisplayName(
        "Given a comcheck of a second tenant, should record the answer on the public route")
    void given_comcheckOfSecondTenant_should_recordTheAnswerOnThePublicRoute() throws Exception {
      // Act
      mvc.perform(get(COMCHECK_URI + "/" + statusId))
          // Assert
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.comcheckstatus_id").value(statusId))
          .andExpect(jsonPath("$.comcheckstatus_state").value("SUCCESS"));
      assertThat(column(entityManager, RECEIVE_DATE, statusId)).hasSize(1).doesNotContainNull();
      assertThat(column(entityManager, COMCHECK_STATE, comcheckId)).containsExactly("FINISHED");
    }

    @Test
    @DisplayName("Given the only answer already recorded, should answer 409 and keep the first one")
    void given_onlyAnswerAlreadyRecorded_should_answer409AndKeepTheFirstOne() throws Exception {
      // Arrange
      mvc.perform(get(COMCHECK_URI + "/" + statusId)).andExpect(status().isOk());
      List<String> firstAnswer = column(entityManager, RECEIVE_DATE, statusId);
      // Act
      mvc.perform(get(COMCHECK_URI + "/" + statusId))
          // Assert
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.message").value(ALREADY_ANSWERED));
      assertThat(column(entityManager, RECEIVE_DATE, statusId)).isEqualTo(firstAnswer);
      assertThat(column(entityManager, COMCHECK_STATE, comcheckId)).containsExactly("FINISHED");
    }

    @Test
    @DisplayName(
        "Given an answer already recorded in a comcheck still waiting for others, should answer 409 and keep the first one")
    void given_answerRecordedInRunningComcheck_should_answer409AndKeepTheFirstOne()
        throws Exception {
      // Arrange
      Seeded seeded = seedInSecondTenant(2, Comcheck.COMCHECK_STATUS.RUNNING);
      String answered = seeded.statusIds().get(0);
      mvc.perform(get(COMCHECK_URI + "/" + answered)).andExpect(status().isOk());
      List<String> firstAnswer = column(entityManager, RECEIVE_DATE, answered);
      // Act
      mvc.perform(get(COMCHECK_URI + "/" + answered))
          // Assert
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.message").value(ALREADY_ANSWERED));
      assertThat(column(entityManager, RECEIVE_DATE, answered)).isEqualTo(firstAnswer);
      assertThat(column(entityManager, COMCHECK_STATE, seeded.comcheckId()))
          .containsExactly("RUNNING");
    }

    @Test
    @DisplayName("Given an expired comcheck, should answer 409 and record nothing")
    void given_expiredComcheck_should_answer409AndRecordNothing() throws Exception {
      // Arrange
      Seeded seeded = seedInSecondTenant(1, Comcheck.COMCHECK_STATUS.EXPIRED);
      String expiredStatusId = seeded.statusIds().getFirst();
      // Act
      mvc.perform(get(COMCHECK_URI + "/" + expiredStatusId))
          // Assert
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.message").value(CLOSED));
      assertThat(column(entityManager, RECEIVE_DATE, expiredStatusId)).containsOnlyNulls();
      assertThat(column(entityManager, COMCHECK_STATE, seeded.comcheckId()))
          .containsExactly("EXPIRED");
    }

    @Test
    @DisplayName("Given a tenant in the path, should refuse it and record nothing")
    void given_tenantInThePath_should_refuseItAndRecordNothing() throws Exception {
      // Act
      mvc.perform(get(TENANT_BASE_PATH + Tenant.DEFAULT_TENANT_UUID + "/comcheck/" + statusId))
          // Assert
          .andExpect(status().isUnauthorized());
      assertThat(column(entityManager, RECEIVE_DATE, statusId)).containsOnlyNulls();
      assertThat(column(entityManager, COMCHECK_STATE, comcheckId)).containsExactly("RUNNING");
    }
  }
}
