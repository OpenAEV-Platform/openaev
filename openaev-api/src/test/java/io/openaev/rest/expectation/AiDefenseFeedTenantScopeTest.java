package io.openaev.rest.expectation;

import static io.openaev.expectation.ExpectationPropertiesConfig.DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME;
import static io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegration.OPENAEV_INJECTOR_NAME;
import static io.openaev.rest.expectation.ExpectationApi.INJECTS_EXPECTATIONS_URI;
import static io.openaev.utils.fixtures.ExpectationFixture.createDetectionExpectations;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Collector;
import io.openaev.database.model.CollectorType;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.model.TechnicalInjectExpectation;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.AssetGroupRepository;
import io.openaev.database.repository.CollectorRepository;
import io.openaev.database.repository.CollectorTypeRepository;
import io.openaev.database.repository.EndpointRepository;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.database.repository.SecurityPlatformRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.expectation.Expectation;
import io.openaev.service.InjectExpectationService;
import io.openaev.utils.fixtures.AssetGroupFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.SecurityPlatformFixture;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regression test for #7014: with {@code collectors} on v2 tenant isolation, the AI defense feed
 * endpoint must carry a {@link io.openaev.context.TxCtx} tenant scope. Its native query reads the
 * collectors table inside the expected-security-platforms guard; without a scope the guard is
 * fail-closed EMPTY (no error), so every platform-restricted AI adversarial expectation silently
 * disappears from the feed and is never validated. This runs the real HTTP endpoint with the table
 * active - the production configuration that {@code ExpectationApiTest} does not exercise (the test
 * properties leave {@code openaev.tenant.active-tables} empty).
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=collectors")
@WithMockUser(isAdmin = true)
@DisplayName("AI defense feed carries the tenant scope with collectors v2-activated")
class AiDefenseFeedTenantScopeTest extends IntegrationTest {

  private static final String INJECTION_NAME = "AI adversarial - direct prompt injection";
  private static final String INJECTOR_TYPE = "openaev_ai_feed_scope_test";

  @Autowired private MockMvc mvc;
  @Autowired private EntityManager em;
  @Autowired private AssetGroupRepository assetGroupRepository;
  @Autowired private EndpointRepository endpointRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private InjectorRepository injectorRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private CollectorTypeRepository collectorTypeRepository;
  @Autowired private CollectorRepository collectorRepository;
  @Autowired private SecurityPlatformRepository securityPlatformRepository;
  @Autowired private InjectExpectationRepository injectExpectationRepository;
  @Autowired private InjectExpectationService injectExpectationService;

  private Inject savedInject;
  private Endpoint agentlessEndpoint;
  private Collector llmCollector;

  @BeforeEach
  void setUp() throws Exception {
    // The TxCtx scope is the caller's authorized tenants: the mock user must be a member of the
    // default tenant (where the collector and the inject live) for the scope to cover them.
    String userId = testUserHolder.get().getId();
    tenantRepository.addUserToTenant(userId, Tenant.DEFAULT_TENANT_UUID);
    tenantMembershipCacheManager.evict(userId, Tenant.DEFAULT_TENANT_UUID);

    InjectorContract injectorContract =
        InjectorContractFixture.createInjectorContract(Map.of("en", INJECTION_NAME));
    injectorContract.setCustom(true);
    Injector savedInjector =
        injectorRepository.save(
            InjectorFixture.createInjector(
                UUID.randomUUID().toString(), OPENAEV_INJECTOR_NAME + "-ai-scope", INJECTOR_TYPE));
    injectorContract.addInjector(savedInjector);
    InjectorContract savedInjectorContract = injectorContractRepository.save(injectorContract);
    em.flush();
    savedInjector.linkContract(savedInjectorContract);
    injectorRepository.save(savedInjector);

    agentlessEndpoint =
        endpointRepository.save(EndpointFixture.createEndpoint("ai-agentless-endpoint"));
    AssetGroup savedAssetGroup =
        assetGroupRepository.save(
            AssetGroupFixture.createAssetGroupWithAssets(
                "ai-scope-asset-group", List.of(agentlessEndpoint)));
    savedInject =
        injectRepository.save(
            InjectFixture.createTechnicalInjectWithAssetGroup(
                savedInjectorContract, INJECTION_NAME, savedAssetGroup));

    // An LLM firewall collector in the default tenant, and an agentless DETECTION expectation
    // restricted to that platform type - the production AI red team shape.
    SecurityPlatform securityPlatform =
        securityPlatformRepository.save(
            SecurityPlatformFixture.createDefault(
                "LLM_FIREWALL-platform-" + UUID.randomUUID(), "LLM_FIREWALL"));
    CollectorType collectorType = new CollectorType(UUID.randomUUID().toString());
    collectorTypeRepository.save(collectorType);
    Collector collector = new Collector();
    collector.setId(UUID.randomUUID().toString());
    collector.setTenantId(Tenant.DEFAULT_TENANT_UUID);
    collector.setName("llm-firewall-collector");
    collector.setType(collectorType.getName());
    collector.setCollectorType(collectorType);
    collector.setExternal(true);
    collector.setSecurityPlatform(securityPlatform);
    llmCollector = collectorRepository.save(collector);

    ExecutableInject executableInject =
        new ExecutableInject(
            false,
            true,
            savedInject,
            emptyList(),
            List.of(agentlessEndpoint),
            emptyList(),
            emptyList());
    List<Expectation> detectionExpectations =
        createDetectionExpectations(
            emptyList(), agentlessEndpoint, null, DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME);
    injectExpectationService.buildAndSaveInjectExpectations(
        executableInject, detectionExpectations);

    TechnicalInjectExpectation leaf =
        (TechnicalInjectExpectation)
            injectExpectationRepository
                .findAllByInjectAndAsset(savedInject.getId(), agentlessEndpoint.getId())
                .getFirst();
    leaf.setExpectedSecurityPlatforms(
        List.of(SecurityPlatform.SECURITY_PLATFORM_TYPE.LLM_FIREWALL));
    injectExpectationRepository.save(leaf);
    em.flush();
    em.clear();
  }

  @Test
  @DisplayName("a platform-restricted agentless expectation reaches its matching collector")
  void platformRestrictedExpectationReachesMatchingCollector() throws Exception {
    String response =
        mvc.perform(
                get(INJECTS_EXPECTATIONS_URI + "/ai/" + llmCollector.getId())
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    // Without the TxCtx scope the collectors read is fail-closed empty and the feed silently
    // returns [] - the #7014 production regression.
    assertEquals(1, ((List<?>) JsonPath.read(response, "$")).size());
  }
}
