package io.openaev.rest.inject;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.CollectorType;
import io.openaev.database.model.ConnectorCompositeId;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.InjectorContractId;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.CollectorTypeRepository;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Every response embedding a full {@code InjectorContract} (inject lists, inject and contract
 * details) serializes the contract's payload, whose {@code payload_collector_type} is written by
 * {@code CollectorTypeNameSerializer} through {@code getName()}. Serialization runs after the
 * {@code @Transactional} controller method has returned, so a lazy {@code Payload.collectorType}
 * would be loaded with no tenant scope: on v2-active {@code collector_types} that read fails
 * closed, the already-committed 200 body is cut mid-stream and the inject screens crash.
 *
 * <p>Deliberately NOT {@code @Transactional}, same as {@code PayloadCollectorTypeScopeTest}: a
 * test-managed transaction around the MockMvc call would keep the scope open through serialization
 * and hide the defect. Seed and clean through auto-committed, tenant-scoped transactions.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=collector_types")
@WithMockUser(isAdmin = true)
@DisplayName("Inject responses serialize the payload collector type outside the request scope")
class InjectPayloadCollectorTypeScopeTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private CollectorTypeRepository collectorTypeRepository;
  @Autowired private PayloadRepository payloadRepository;
  @Autowired private InjectorRepository injectorRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private InjectRepository injectRepository;

  private String tenantId;
  private String collectorTypeName;
  private String collectorTypeId;
  private String payloadId;
  private String injectorId;
  private String injectorContractId;
  private String scenarioId;
  private String exerciseId;
  private String scenarioInjectId;
  private String exerciseInjectId;

  @BeforeEach
  void seedInjectsWithCollectorTypePayload() throws Exception {
    tenantId = tenantHelper.createTenantWithCurrentUser("inject-payload-collector-type").getId();
    collectorTypeName = "openaev_scope_test_" + UUID.randomUUID();

    // One transaction so every referenced entity stays managed when the next one points to it.
    tenantTx.execute(
        TxCtx.forTenant(tenantId),
        () -> {
          CollectorType collectorType = new CollectorType();
          collectorType.setName(collectorTypeName);
          collectorType.setTenant(new Tenant(tenantId));
          CollectorType savedCollectorType = collectorTypeRepository.save(collectorType);
          collectorTypeId = savedCollectorType.getId();

          Payload payload = PayloadFixture.createDefaultCommand();
          payload.setTenant(new Tenant(tenantId));
          payload.setCollectorType(savedCollectorType);
          Payload savedPayload = payloadRepository.save(payload);
          payloadId = savedPayload.getId();

          Injector injector = InjectorFixture.createDefaultPayloadInjector();
          injector.setTenantId(tenantId);
          Injector savedInjector = injectorRepository.save(injector);
          injectorId = savedInjector.getId();

          InjectorContract injectorContract;
          try {
            injectorContract =
                InjectorContractFixture.createPayloadInjectorContractWithFieldsContent(List.of());
          } catch (Exception e) {
            throw new IllegalStateException(e);
          }
          // Tenant before linking: the injector link captures the contract's composite key.
          injectorContract.setTenant(new Tenant(tenantId));
          injectorContract.addInjector(savedInjector);
          injectorContract.setPayload(savedPayload);
          injectorContract.setDomains(new HashSet<>());
          InjectorContract savedContract = injectorContractRepository.save(injectorContract);
          injectorContractId = savedContract.getId();

          Scenario scenario = ScenarioFixture.getScenario();
          scenario.setTenant(new Tenant(tenantId));
          scenarioId = scenarioRepository.save(scenario).getId();
          Inject scenarioInject = InjectFixture.createInject(savedContract, "scenario inject");
          scenarioInject.setScenario(scenario);
          scenarioInject.setTenant(new Tenant(tenantId));
          scenarioInjectId = injectRepository.save(scenarioInject).getId();

          Exercise exercise = ExerciseFixture.createDefaultExercise();
          exercise.setTenant(new Tenant(tenantId));
          exerciseId = exerciseRepository.save(exercise).getId();
          Inject exerciseInject = InjectFixture.createInject(savedContract, "simulation inject");
          exerciseInject.setExercise(exercise);
          exerciseInject.setTenant(new Tenant(tenantId));
          exerciseInjectId = injectRepository.save(exerciseInject).getId();
        });
  }

  @AfterEach
  void cleanup() {
    tenantTx.execute(
        TxCtx.forTenant(tenantId),
        () -> {
          injectRepository.deleteAllById(List.of(scenarioInjectId, exerciseInjectId));
          scenarioRepository.deleteById(scenarioId);
          exerciseRepository.deleteById(exerciseId);
          injectorContractRepository.deleteById(
              new InjectorContractId(injectorContractId, tenantId));
          injectorRepository.deleteById(ConnectorCompositeId.of(injectorId, tenantId));
          payloadRepository.deleteById(payloadId);
          collectorTypeRepository.deleteById(collectorTypeId);
        });
  }

  @Test
  @DisplayName("simulation inject list returns the payload collector type name")
  void given_simulationInjectWithCollectorTypePayload_should_serializeCollectorTypeName()
      throws Exception {
    mvc.perform(
            post(
                    "/api/tenants/{tenantId}/exercises/{exerciseId}/injects/simple",
                    tenantId,
                    exerciseId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(PaginationFixture.getDefault().build())))
        .andExpect(status().is2xxSuccessful())
        .andExpect(
            jsonPath(
                    "$.content[0].inject_injector_contract.injector_contract_payload.payload_collector_type")
                .value(collectorTypeName));
  }

  @Test
  @DisplayName("scenario inject list returns the payload collector type name")
  void given_scenarioInjectWithCollectorTypePayload_should_serializeCollectorTypeName()
      throws Exception {
    mvc.perform(
            post(
                    "/api/tenants/{tenantId}/scenarios/{scenarioId}/injects/simple",
                    tenantId,
                    scenarioId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(PaginationFixture.getDefault().build())))
        .andExpect(status().is2xxSuccessful())
        .andExpect(
            jsonPath(
                    "$.content[0].inject_injector_contract.injector_contract_payload.payload_collector_type")
                .value(collectorTypeName));
  }

  @Test
  @DisplayName("inject detail returns the payload collector type name")
  void given_injectWithCollectorTypePayload_should_serializeCollectorTypeNameOnDetail()
      throws Exception {
    mvc.perform(get("/api/tenants/{tenantId}/injects/{injectId}", tenantId, exerciseInjectId))
        .andExpect(status().is2xxSuccessful())
        .andExpect(
            jsonPath("$.inject_injector_contract.injector_contract_payload.payload_collector_type")
                .value(collectorTypeName));
  }

  @Test
  @DisplayName("injector contract detail returns the payload collector type name")
  void given_contractWithCollectorTypePayload_should_serializeCollectorTypeNameOnDetail()
      throws Exception {
    mvc.perform(
            get(
                "/api/tenants/{tenantId}/injector_contracts/{injectorContractId}",
                tenantId,
                injectorContractId))
        .andExpect(status().is2xxSuccessful())
        .andExpect(
            jsonPath("$.injector_contract_payload.payload_collector_type")
                .value(collectorTypeName));
  }
}
