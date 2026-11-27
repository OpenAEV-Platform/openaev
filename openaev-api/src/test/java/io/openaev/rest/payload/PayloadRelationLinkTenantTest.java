package io.openaev.rest.payload;

import static io.openaev.rest.payload.PayloadApi.PAYLOAD_URI;
import static io.openaev.rest.payload.PayloadApi.TENANT_PAYLOAD_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.TagLinkJdbc.column;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Domain;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.AttackPatternRepository;
import io.openaev.database.repository.DomainRepository;
import io.openaev.integration.ManagerFactory;
import io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegrationFactory;
import io.openaev.rest.payload.form.PayloadCreateInput;
import io.openaev.rest.payload.form.PayloadUpdateInput;
import io.openaev.rest.payload.service.PayloadCreationService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.BuiltinConnectorRegistration;
import io.openaev.utils.fixtures.DomainFixture;
import io.openaev.utils.fixtures.PayloadInputFixture;
import io.openaev.utils.fixtures.files.AttackPatternFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * A payload create or update links the payload to the domains and the attack patterns of the
 * payload's own tenant.
 *
 * <p>The payload lives in the default tenant, the one the ambient thread-local falls back to. On
 * the non-prefixed route {@code X-Tenant-Ids} can name several tenants, and a payload request
 * carries no tenant selector for an update, so the request scope holds all of them. The domain and
 * attack pattern ids of the body resolve in the tenant of the payload alone. The payload holds them
 * on its injector contract, read from {@code injectors_contracts_domains} and {@code
 * injectors_contracts_attack_patterns} through JDBC on the test connection, which the statement
 * inspector does not rewrite.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Payload create and update link only the domains and attack patterns of its tenant")
class PayloadRelationLinkTenantTest extends IntegrationTest {

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String CONTRACT_DOMAINS =
      "SELECT d.domain_id FROM injectors_contracts_domains d"
          + " JOIN injectors_contracts c ON c.injector_contract_id = d.injector_contract_id"
          + " WHERE c.injector_contract_payload = ?";
  private static final String CONTRACT_ATTACK_PATTERNS =
      "SELECT a.attack_pattern_id FROM injectors_contracts_attack_patterns a"
          + " JOIN injectors_contracts c ON c.injector_contract_id = a.injector_contract_id"
          + " WHERE c.injector_contract_payload = ?";

  @Autowired private MockMvc mvc;
  @Autowired private PayloadCreationService payloadCreationService;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private DomainRepository domainRepository;
  @Autowired private AttackPatternRepository attackPatternRepository;
  @Autowired private BuiltinConnectorRegistration builtinConnectorRegistration;
  @Autowired private OpenaevInjectorIntegrationFactory openaevInjectorIntegrationFactory;
  @Autowired private ManagerFactory managerFactory;

  private String otherTenant;
  private String domainInOther;
  private String domainInDefault;
  private String attackPatternInOther;
  private String attackPatternInDefault;

  @BeforeEach
  void seedBothTenants() throws Exception {
    // The caller belongs to both tenants, so the header can name both of them.
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    // The payload of the default tenant needs its payload-supporting injector, which carries the
    // contract the relations are linked to.
    builtinConnectorRegistration.register(
        openaevInjectorIntegrationFactory, Tenant.DEFAULT_TENANT_UUID);
    managerFactory.getManager(Tenant.DEFAULT_TENANT_UUID).monitorIntegrations();
    otherTenant = tenantHelper.createTenantWithCurrentUser("payload-relation-link-o").getId();
    domainInOther = saveDomain(otherTenant);
    domainInDefault = saveDomain(Tenant.DEFAULT_TENANT_UUID);
    attackPatternInOther =
        attackPatternRepository
            .save(AttackPatternFixture.createDefaultAttackPattern(otherTenant))
            .getId();
    attackPatternInDefault =
        attackPatternRepository
            .save(AttackPatternFixture.createDefaultAttackPattern(Tenant.DEFAULT_TENANT_UUID))
            .getId();
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Header route naming two tenants")
  class HeaderRouteNamingTwoTenants {

    @Test
    @DisplayName(
        "given a payload of the default tenant when an update names a domain and an attack pattern"
            + " of each tenant then only those of the default tenant are linked")
    void given_updateNamingBothTenants_should_linkOnlyTheRelationsOfDefaultTenant()
        throws Exception {
      // -- Arrange --
      String payload = createPayloadInDefaultTenant();
      armRequest(payload);

      // -- Act --
      mvc.perform(
              put(PAYLOAD_URI + "/" + payload)
                  .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + otherTenant)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      asJsonString(
                          updateBody(
                              List.of(domainInOther, domainInDefault),
                              List.of(attackPatternInOther, attackPatternInDefault))))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      assertSoftly(
          soft -> {
            soft.assertThat(column(entityManager, CONTRACT_DOMAINS, payload))
                .as(
                    "domains linked to the payload: kept %s, dropped %s",
                    domainInDefault, domainInOther)
                .containsExactly(domainInDefault);
            soft.assertThat(column(entityManager, CONTRACT_ATTACK_PATTERNS, payload))
                .as(
                    "attack patterns linked to the payload: kept %s, dropped %s",
                    attackPatternInDefault, attackPatternInOther)
                .containsExactly(attackPatternInDefault);
          });
    }

    @Test
    @DisplayName(
        "given a scope of two tenants and the default tenant as explicit write tenant when a create"
            + " names a domain and an attack pattern of each tenant then only those of the default"
            + " tenant are linked")
    void given_createWithExplicitWriteTenant_should_linkOnlyTheRelationsOfThatTenant() {
      // -- Arrange --
      armRequest(null);
      PayloadCreateInput input =
          createBody(
              List.of(domainInOther, domainInDefault),
              List.of(attackPatternInOther, attackPatternInDefault));
      TxCtx bothTenants = TxCtx.forTenants(List.of(Tenant.DEFAULT_TENANT_UUID, otherTenant));

      // -- Act --
      String payload =
          payloadCreationService
              .createPayload(bothTenants, Tenant.DEFAULT_TENANT_UUID, input)
              .payload()
              .getId();

      // -- Assert --
      assertSoftly(
          soft -> {
            soft.assertThat(column(entityManager, CONTRACT_DOMAINS, payload))
                .as(
                    "domains linked to the payload: kept %s, dropped %s",
                    domainInDefault, domainInOther)
                .containsExactly(domainInDefault);
            soft.assertThat(column(entityManager, CONTRACT_ATTACK_PATTERNS, payload))
                .as(
                    "attack patterns linked to the payload: kept %s, dropped %s",
                    attackPatternInDefault, attackPatternInOther)
                .containsExactly(attackPatternInDefault);
          });
    }
  }

  @Nested
  @DisplayName("Prefixed route")
  class PrefixedRoute {

    @Test
    @DisplayName(
        "given a payload of the default tenant when an update under its path names the relations"
            + " of the other tenant then none is linked")
    void given_updateNamingTheOtherTenant_should_linkNothing() throws Exception {
      // -- Arrange --
      String payload = createPayloadInDefaultTenant();
      armRequest(payload);

      // -- Act --
      mvc.perform(
              put(TENANT_PAYLOAD_URI + "/" + payload, Tenant.DEFAULT_TENANT_UUID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      asJsonString(
                          updateBody(List.of(domainInOther), List.of(attackPatternInOther))))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, CONTRACT_DOMAINS, payload)).isEmpty();
      assertThat(column(entityManager, CONTRACT_ATTACK_PATTERNS, payload)).isEmpty();
    }

    @Test
    @DisplayName(
        "given a payload of the default tenant when an update under its path names its own"
            + " relations then they are linked")
    void given_updateNamingItsOwnRelations_should_linkThem() throws Exception {
      // -- Arrange --
      String payload = createPayloadInDefaultTenant();
      armRequest(payload);

      // -- Act --
      mvc.perform(
              put(TENANT_PAYLOAD_URI + "/" + payload, Tenant.DEFAULT_TENANT_UUID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      asJsonString(
                          updateBody(List.of(domainInDefault), List.of(attackPatternInDefault))))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, CONTRACT_DOMAINS, payload)).containsExactly(domainInDefault);
      assertThat(column(entityManager, CONTRACT_ATTACK_PATTERNS, payload))
          .containsExactly(attackPatternInDefault);
    }

    @Test
    @DisplayName(
        "given the default tenant when a create under its path names the relations of the other"
            + " tenant then none is linked, and its own relations are linked")
    void given_createUnderItsPath_should_linkOnlyItsOwnRelations() throws Exception {
      // -- Arrange --
      armRequest(null);

      // -- Act --
      String response =
          mvc.perform(
                  post(TENANT_PAYLOAD_URI, Tenant.DEFAULT_TENANT_UUID)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          asJsonString(
                              createBody(
                                  List.of(domainInOther, domainInDefault),
                                  List.of(attackPatternInOther, attackPatternInDefault))))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- Assert --
      String payload = JsonPath.read(response, "$.payload_id");
      assertThat(column(entityManager, CONTRACT_DOMAINS, payload)).containsExactly(domainInDefault);
      assertThat(column(entityManager, CONTRACT_ATTACK_PATTERNS, payload))
          .containsExactly(attackPatternInDefault);
    }
  }

  // -- Helpers --

  private PayloadUpdateInput updateBody(List<String> domains, List<String> attackPatterns) {
    PayloadUpdateInput input = PayloadInputFixture.getDefaultCommandPayloadUpdateInput(domains);
    input.setAttackPatternsIds(attackPatterns);
    return input;
  }

  private PayloadCreateInput createBody(List<String> domains, List<String> attackPatterns) {
    PayloadCreateInput input =
        PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine(domains);
    input.setAttackPatternsIds(attackPatterns);
    return input;
  }

  private String saveDomain(String tenantId) {
    Domain domain = DomainFixture.getRandomDomain();
    domain.setTenant(new Tenant(tenantId));
    return domainRepository.save(domain).getId();
  }

  private String createPayloadInDefaultTenant() throws Exception {
    PayloadCreateInput input =
        PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine(List.of());
    String response =
        mvc.perform(
                post(TENANT_PAYLOAD_URI, Tenant.DEFAULT_TENANT_UUID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.payload_id");
  }

  /**
   * The relations carry the tenant the scenario relies on, then the ambient tenant is reset to the
   * default one the request thread of the header route carries. A payload id, when given, is
   * checked to be of the default tenant.
   */
  private void armRequest(String payload) {
    if (payload != null) {
      assertThat(
              column(entityManager, "SELECT tenant_id FROM payloads WHERE payload_id = ?", payload))
          .containsExactly(Tenant.DEFAULT_TENANT_UUID);
    }
    assertThat(
            column(
                entityManager, "SELECT tenant_id FROM domains WHERE domain_id = ?", domainInOther))
        .containsExactly(otherTenant);
    assertThat(
            column(
                entityManager,
                "SELECT tenant_id FROM attack_patterns WHERE attack_pattern_id = ?",
                attackPatternInOther))
        .containsExactly(otherTenant);
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }
}
