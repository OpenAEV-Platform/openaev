package io.openaev.rest.inject;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.InjectorContractId;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Not {@code @Transactional}, same shape as {@code InjectorContractPayloadGroupByTest}. */
@TestPropertySource(properties = "openaev.tenant.active-tables=payloads")
@WithMockUser(isAdmin = true)
@DisplayName("SimulationInjectApi search with payloads active")
class SimulationInjectSearchPayloadGroupByTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private PayloadRepository payloadRepository;
  @Autowired private InjectorRepository injectorRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private EntityManager entityManager;

  private String tenantId;
  private String payloadId;
  private String injectorId;
  private String injectorContractId;
  private String exerciseId;
  private String injectId;

  @BeforeEach
  void seedExerciseWithPayloadInject() throws Exception {
    tenantId = tenantHelper.createTenantWithCurrentUser("sim-inject-payload-groupby").getId();

    tenantTx.execute(
        TxCtx.forTenant(tenantId),
        () -> {
          Payload payload = PayloadFixture.createDefaultCommand();
          payload.setTenant(new Tenant(tenantId));
          Payload savedPayload = payloadRepository.save(payload);
          payloadId = savedPayload.getId();

          Injector injector = InjectorFixture.createDefaultPayloadInjector();
          injector.setTenantId(tenantId);
          injector.setCreatedAt(Instant.now());
          injector.setUpdatedAt(Instant.now());
          Injector savedInjector = injectorRepository.save(injector);
          injectorId = savedInjector.getId();

          InjectorContract injectorContract =
              InjectorContractFixture.createDefaultInjectorContract();
          injectorContract.setTenant(new Tenant(tenantId));
          injectorContract.setPayload(savedPayload);
          injectorContract.clearInjectors();
          injectorContract.addInjector(savedInjector);
          InjectorContract savedContract = injectorContractRepository.save(injectorContract);
          injectorContractId = savedContract.getId();

          Exercise exercise = ExerciseFixture.createDefaultExercise();
          exercise.setTenant(new Tenant(tenantId));
          Exercise savedExercise = exerciseRepository.save(exercise);
          exerciseId = savedExercise.getId();

          Inject inject = InjectFixture.createInject(savedContract, "Payload inject");
          inject.setTenant(new Tenant(tenantId));
          inject.setExercise(savedExercise);
          injectId = injectRepository.save(inject).getId();
        });
  }

  @AfterEach
  void cleanup() {
    tenantTx.execute(
        TxCtx.forTenant(tenantId),
        () -> {
          injectRepository.deleteByIdNative(injectId);
          exerciseRepository.deleteById(exerciseId);
          // Direct query, same as InjectorContractPayloadGroupByTest: avoids a double delete.
          entityManager
              .createNativeQuery(
                  "DELETE FROM injectors_injector_contracts WHERE injector_contract_id = :contractId"
                      + " AND injector_id = :injectorId")
              .setParameter("contractId", injectorContractId)
              .setParameter("injectorId", injectorId)
              .executeUpdate();
          injectorContractRepository.deleteById(
              new InjectorContractId(injectorContractId, tenantId));
          payloadRepository.deleteById(payloadId);
          injectorRepository.deleteByInjectorId(injectorId);
        });
  }

  @Test
  @DisplayName("POST /exercises/{id}/injects/search does not 500 once payloads is active")
  void given_payloadsActive_should_notFailOnSimulationInjectSearch() throws Exception {
    SearchPaginationInput input = new SearchPaginationInput();

    mvc.perform(
            post(
                    "/api/tenants/{tenantId}/exercises/{exerciseId}/injects/search",
                    tenantId,
                    exerciseId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input)))
        .andExpect(status().is2xxSuccessful());
  }
}
