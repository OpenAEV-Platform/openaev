package io.openaev.rest.payload;

import static io.openaev.rest.payload.PayloadApi.PAYLOAD_URI;
import static io.openaev.rest.payload.PayloadApi.TENANT_PAYLOAD_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.rest.injector_contract.form.InjectorContractDomainDTO;
import io.openaev.rest.payload.form.PayloadCreateInput;
import io.openaev.rest.payload.form.PayloadUpsertInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PayloadInputFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * A payload create or upsert resolves exactly one write tenant, and every row the request creates
 * (the payload, its injector contract, the organization and the domains of an upsert) carries it:
 * the path tenant, the one tenant {@code X-Tenant-Ids} names, or, without a selector, the fallback
 * of {@code @RequireTenantSelector} (the default tenant for a caller of several tenants including
 * it, a 400 {@code TENANT_SELECTOR_REQUIRED} otherwise).
 *
 * <p>The class is deliberately NOT {@code @Transactional}: the rows are committed through an
 * auto-committing {@link JdbcTemplate}, so each request runs its own transaction with its own
 * tenant scope, as in production, and the created rows are removed on teardown.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Payload create and upsert resolve one write tenant")
class PayloadWriteTenantSelectorTest extends IntegrationTest {

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private final List<String> tenantsToDelete = new ArrayList<>();
  private final List<String> collectorIds = new ArrayList<>();
  private final List<String> collectorTypeIds = new ArrayList<>();
  private final List<String> organizationNames = new ArrayList<>();
  private final List<String> domainNames = new ArrayList<>();
  private final List<String> payloadIds = new ArrayList<>();
  private final List<String> injectorIds = new ArrayList<>();

  /** A collector seeded in a tenant, with the author its upsert names the organization after. */
  private record SeededCollector(String id, String author) {}

  @BeforeEach
  void startWithTheEmptyThreadLocalOfProduction() {
    jdbc = new JdbcTemplate(dataSource);
    TenantContext.clearCurrentTenant();
  }

  @AfterEach
  void cleanup() {
    TenantContext.clearCurrentTenant();
    for (String payloadId : payloadIds) {
      jdbc.update("DELETE FROM injectors_contracts WHERE injector_contract_payload = ?", payloadId);
      jdbc.update("DELETE FROM payloads WHERE payload_id = ?", payloadId);
    }
    for (String id : injectorIds) {
      jdbc.update("DELETE FROM injectors WHERE injector_id = ?", id);
    }
    for (String name : organizationNames) {
      jdbc.update("DELETE FROM organizations WHERE organization_name = ?", name);
    }
    for (String name : domainNames) {
      jdbc.update("DELETE FROM domains WHERE domain_name = ?", name);
    }
    for (String id : collectorIds) {
      jdbc.update("DELETE FROM collectors WHERE collector_id = ?", id);
    }
    for (String id : collectorTypeIds) {
      jdbc.update("DELETE FROM collector_types WHERE collector_type_id = ?", id);
    }
    tenantHelper.deleteCommittedTenants(tenantsToDelete.toArray(new String[0]));
  }

  // -- seeding --

  private String createTenant(String name) throws Exception {
    String id = tenantHelper.createTenantWithCurrentUser(name).getId();
    tenantsToDelete.add(id);
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.hasCurrentTenant()).isFalse();
    return id;
  }

  /** The payload-supporting injector the platform onboards in every tenant but the test default. */
  private void seedPayloadInjector(String tenantId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO injectors (injector_id, tenant_id, injector_name, injector_type,"
            + " injector_payloads) VALUES (?, ?, ?, ?, true)",
        id,
        tenantId,
        "payload-write-injector-" + id,
        "payload-write-injector-type-" + id);
    injectorIds.add(id);
  }

  private SeededCollector seedCollector(String tenantId) {
    String typeId = UUID.randomUUID().toString();
    String typeName = "payload-write-type-" + UUID.randomUUID();
    String collectorId = UUID.randomUUID().toString();
    String author = "payload-write-author-" + UUID.randomUUID();
    jdbc.update(
        "INSERT INTO collector_types (collector_type_id, collector_type_name, tenant_id)"
            + " VALUES (?, ?, ?)",
        typeId,
        typeName,
        tenantId);
    jdbc.update(
        "INSERT INTO collectors (collector_id, tenant_id, collector_name, collector_type,"
            + " collector_type_id, collector_period, collector_author, collector_external,"
            + " collector_created_at, collector_updated_at)"
            + " VALUES (?, ?, ?, ?, ?, 60, ?, false, now(), now())",
        collectorId,
        tenantId,
        "payload-write-collector-" + collectorId,
        typeName,
        typeId,
        author);
    collectorTypeIds.add(typeId);
    collectorIds.add(collectorId);
    organizationNames.add(author);
    return new SeededCollector(collectorId, author);
  }

  // -- requests --

  private PayloadUpsertInput upsertInput(SeededCollector collector, String domainName) {
    PayloadUpsertInput input = PayloadInputFixture.getDefaultCommandPayloadUpsertInput(Set.of());
    input.setExternalId("payload-write-" + UUID.randomUUID());
    input.setCollector(collector.id());
    InjectorContractDomainDTO domain = new InjectorContractDomainDTO();
    domain.setId(UUID.randomUUID().toString());
    domain.setName(domainName);
    domain.setColor("#ff0000");
    input.setDomains(Set.of(domain));
    domainNames.add(domainName);
    return input;
  }

  private ResultActions upsert(String uri, String tenantHeader, PayloadUpsertInput input)
      throws Exception {
    var request =
        post(uri).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(asJsonString(input));
    if (tenantHeader != null) {
      request = request.header(TENANT_HEADER, tenantHeader);
    }
    return mvc.perform(request);
  }

  private ResultActions create(String uri, String tenantHeader) throws Exception {
    return create(uri, tenantHeader, null);
  }

  private ResultActions create(String uri, String tenantHeader, String name) throws Exception {
    PayloadCreateInput input =
        PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine(List.of());
    if (name != null) {
      input.setName(name);
    }
    var request =
        post(uri).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(asJsonString(input));
    if (tenantHeader != null) {
      request = request.header(TENANT_HEADER, tenantHeader);
    }
    return mvc.perform(request);
  }

  // -- read back --

  private String trackedPayloadId(ResultActions result) throws Exception {
    String payloadId =
        JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.payload_id");
    payloadIds.add(payloadId);
    return payloadId;
  }

  private void assertPayloadAndContractIn(String payloadId, String tenantId) {
    assertThat(jdbc.queryForList("SELECT tenant_id FROM payloads WHERE payload_id = ?", payloadId))
        .extracting(row -> row.get("tenant_id"))
        .as("tenant of the payload")
        .containsExactly(tenantId);
    assertThat(
            jdbc.queryForList(
                "SELECT tenant_id FROM injectors_contracts WHERE injector_contract_payload = ?",
                payloadId))
        .extracting(row -> row.get("tenant_id"))
        .as("tenant of the injector contract of the payload")
        .containsExactly(tenantId);
  }

  private void assertUpsertedRowsIn(
      String payloadId, SeededCollector collector, String domainName, String tenantId) {
    assertPayloadAndContractIn(payloadId, tenantId);
    assertThat(
            jdbc.queryForList(
                "SELECT tenant_id FROM organizations WHERE organization_name = ?",
                collector.author()))
        .extracting(row -> row.get("tenant_id"))
        .as("tenant of the organization named after the collector's author")
        .containsExactly(tenantId);
    assertThat(jdbc.queryForList("SELECT tenant_id FROM domains WHERE domain_name = ?", domainName))
        .extracting(row -> row.get("tenant_id"))
        .as("tenant of the domain the upsert creates")
        .containsExactly(tenantId);
  }

  private void assertNothingCreated(SeededCollector collector, String domainName) {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM organizations WHERE organization_name = ?",
                Integer.class,
                collector.author()))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM domains WHERE domain_name = ?", Integer.class, domainName))
        .isZero();
  }

  private int payloadsNamed(String name) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM payloads WHERE payload_name = ?", Integer.class, name);
  }

  @Nested
  @DisplayName("an upsert by a caller of the default tenant and of tenant B")
  class UpsertByMemberOfDefaultAndB {

    @BeforeEach
    void joinTheDefaultTenant() {
      tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT);
      seedPayloadInjector(DEFAULT_TENANT);
    }

    @Test
    @DisplayName("given_noSelector_should_createEveryRowInTheDefaultTenant")
    void given_noSelector_should_createEveryRowInTheDefaultTenant() throws Exception {
      // Arrange
      createTenant("payload-upsert-default-b");
      SeededCollector collector = seedCollector(DEFAULT_TENANT);
      String domainName = "payload-write-domain-" + UUID.randomUUID();
      PayloadUpsertInput input = upsertInput(collector, domainName);

      // Act
      ResultActions result = upsert(PAYLOAD_URI + "/upsert", null, input);

      // Assert
      result.andExpect(status().isOk());
      assertUpsertedRowsIn(trackedPayloadId(result), collector, domainName, DEFAULT_TENANT);
    }

    @Test
    @DisplayName("given_headerNamingB_should_createEveryRowInB")
    void given_headerNamingB_should_createEveryRowInB() throws Exception {
      // Arrange
      String tenantB = createTenant("payload-upsert-header-b");
      SeededCollector collector = seedCollector(tenantB);
      String domainName = "payload-write-domain-" + UUID.randomUUID();
      PayloadUpsertInput input = upsertInput(collector, domainName);

      // Act
      ResultActions result = upsert(PAYLOAD_URI + "/upsert", tenantB, input);

      // Assert
      result.andExpect(status().isOk());
      assertUpsertedRowsIn(trackedPayloadId(result), collector, domainName, tenantB);
    }

    @Test
    @DisplayName("given_pathOfB_should_createEveryRowInB")
    void given_pathOfB_should_createEveryRowInB() throws Exception {
      // Arrange
      String tenantB = createTenant("payload-upsert-path-b");
      SeededCollector collector = seedCollector(tenantB);
      String domainName = "payload-write-domain-" + UUID.randomUUID();
      PayloadUpsertInput input = upsertInput(collector, domainName);

      // Act
      ResultActions result =
          upsert(TENANT_PAYLOAD_URI.replace("{tenantId}", tenantB) + "/upsert", null, input);

      // Assert
      result.andExpect(status().isOk());
      assertUpsertedRowsIn(trackedPayloadId(result), collector, domainName, tenantB);
    }
  }

  @Nested
  @DisplayName("a create by a caller of the default tenant and of tenant B")
  class CreateByMemberOfDefaultAndB {

    @BeforeEach
    void joinTheDefaultTenant() {
      tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT);
      seedPayloadInjector(DEFAULT_TENANT);
    }

    @Test
    @DisplayName("given_noSelector_should_createThePayloadInTheDefaultTenant")
    void given_noSelector_should_createThePayloadInTheDefaultTenant() throws Exception {
      // Arrange
      createTenant("payload-create-default-b");

      // Act
      ResultActions result = create(PAYLOAD_URI, null);

      // Assert
      result.andExpect(status().isOk());
      assertPayloadAndContractIn(trackedPayloadId(result), DEFAULT_TENANT);
    }

    @Test
    @DisplayName("given_headerNamingB_should_createThePayloadInB")
    void given_headerNamingB_should_createThePayloadInB() throws Exception {
      // Arrange
      String tenantB = createTenant("payload-create-header-b");

      // Act
      ResultActions result = create(PAYLOAD_URI, tenantB);

      // Assert
      result.andExpect(status().isOk());
      assertPayloadAndContractIn(trackedPayloadId(result), tenantB);
    }

    @Test
    @DisplayName("given_pathOfB_should_createThePayloadInB")
    void given_pathOfB_should_createThePayloadInB() throws Exception {
      // Arrange
      String tenantB = createTenant("payload-create-path-b");

      // Act
      ResultActions result = create(TENANT_PAYLOAD_URI.replace("{tenantId}", tenantB), null);

      // Assert
      result.andExpect(status().isOk());
      assertPayloadAndContractIn(trackedPayloadId(result), tenantB);
    }
  }

  @Nested
  @DisplayName("a caller of tenants B and C, not of the default tenant")
  class MemberOfBAndCWithoutTheDefaultTenant {

    @Test
    @DisplayName("given_noSelector_should_refuseTheUpsertAndCreateNothing")
    void given_noSelector_should_refuseTheUpsertAndCreateNothing() throws Exception {
      // Arrange
      String tenantB = createTenant("payload-upsert-b");
      createTenant("payload-upsert-c");
      SeededCollector collector = seedCollector(tenantB);
      String domainName = "payload-write-domain-" + UUID.randomUUID();
      PayloadUpsertInput input = upsertInput(collector, domainName);
      String payloadName = "payload-write-refused-" + UUID.randomUUID();
      input.setName(payloadName);

      // Act
      ResultActions result = upsert(PAYLOAD_URI + "/upsert", null, input);

      // Assert
      result
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("TENANT_SELECTOR_REQUIRED"));
      assertNothingCreated(collector, domainName);
      assertThat(payloadsNamed(payloadName)).isZero();
    }

    @Test
    @DisplayName("given_noSelector_should_refuseTheCreateAndCreateNothing")
    void given_noSelector_should_refuseTheCreateAndCreateNothing() throws Exception {
      // Arrange
      createTenant("payload-create-b");
      createTenant("payload-create-c");
      String payloadName = "payload-write-refused-" + UUID.randomUUID();

      // Act
      ResultActions result = create(PAYLOAD_URI, null, payloadName);

      // Assert
      result
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("TENANT_SELECTOR_REQUIRED"));
      assertThat(payloadsNamed(payloadName)).isZero();
    }
  }
}
