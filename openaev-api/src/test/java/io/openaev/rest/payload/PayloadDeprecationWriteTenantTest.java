package io.openaev.rest.payload;

import static io.openaev.rest.payload.PayloadApi.PAYLOAD_URI;
import static io.openaev.rest.payload.PayloadApi.TENANT_PAYLOAD_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.rest.payload.form.PayloadsDeprecateInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.List;
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
 * The collector payload deprecation resolves exactly one write tenant: the path tenant, the one
 * tenant {@code X-Tenant-Ids} names, or, without a selector, the fallback of
 * {@code @RequireTenantSelector} (the default tenant for a caller of several tenants including it,
 * a 400 {@code TENANT_SELECTOR_REQUIRED} otherwise). A header naming two tenants is a 400 {@code
 * TENANT_WRITE_SCOPE}, as {@code tenantForWrite} refuses a multi-tenant scope. It reads and
 * deprecates only the collector's payloads of that tenant; the payloads carrying the same external
 * ids in another tenant are untouched.
 *
 * <p>The class is deliberately NOT {@code @Transactional}: the rows are committed through an
 * auto-committing {@link JdbcTemplate}, so each request runs its own transaction with its own
 * tenant scope, as in production, and the seeded rows are removed on teardown.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Collector payload deprecation resolves one write tenant")
class PayloadDeprecationWriteTenantTest extends IntegrationTest {

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;
  private static final String VERIFIED = "VERIFIED";
  private static final String DEPRECATED = "DEPRECATED";

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
  private final List<String> payloadIds = new ArrayList<>();
  private final List<String> identityIds = new ArrayList<>();
  private final List<String> collectorIds = new ArrayList<>();
  private final List<String> collectorTypeIds = new ArrayList<>();

  /**
   * The payloads of one tenant: the processed one (X), the one a deprecation is expected to
   * deprecate (Y) and the one of no collector (Z), each identified by its external id (Z has none a
   * collector can reach) and its payload id.
   */
  private record SeededPayloads(
      String typeId, String xExternalId, String yExternalId, String xId, String yId, String zId) {}

  /** What a test seeds in a tenant: the collector and its payloads. */
  private record SeededTenant(String tenantId, SeededPayloads payloads) {}

  @BeforeEach
  void startWithTheEmptyThreadLocalOfProduction() {
    jdbc = new JdbcTemplate(dataSource);
    TenantContext.clearCurrentTenant();
  }

  @AfterEach
  void cleanup() {
    TenantContext.clearCurrentTenant();
    for (String id : payloadIds) {
      jdbc.update("DELETE FROM payloads WHERE payload_id = ?", id);
    }
    for (String id : identityIds) {
      jdbc.update("DELETE FROM payload_identities WHERE payload_identity_id = ?", id);
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

  /**
   * A collector type and a collector of that type in the tenant: the collector id is the same in
   * every tenant, the type name too (it is unique per tenant), the type id is not.
   */
  private String seedCollectorType(String tenantId, String typeName) {
    String typeId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO collector_types (collector_type_id, collector_type_name, tenant_id)"
            + " VALUES (?, ?, ?)",
        typeId,
        typeName,
        tenantId);
    collectorTypeIds.add(typeId);
    return typeId;
  }

  private void seedCollector(String tenantId, String collectorId, String typeName, String typeId) {
    jdbc.update(
        "INSERT INTO collectors (collector_id, tenant_id, collector_name, collector_type,"
            + " collector_type_id, collector_period, collector_author, collector_external,"
            + " collector_created_at, collector_updated_at)"
            + " VALUES (?, ?, ?, ?, ?, 60, ?, false, now(), now())",
        collectorId,
        tenantId,
        "payload-deprecation-collector-" + collectorId,
        typeName,
        typeId,
        "payload-deprecation-author");
    collectorIds.add(collectorId);
  }

  private String seedPayload(
      String tenantId, String externalId, String typeId, String name, String status) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO payload_identities (payload_identity_id, tenant_id, payload_external_id,"
            + " payload_source, payload_collector_type) VALUES (?, ?, ?, 'COMMUNITY', ?)",
        id,
        tenantId,
        externalId,
        typeId);
    jdbc.update(
        "INSERT INTO payloads (payload_id, payload_type, payload_name, payload_status, tenant_id,"
            + " payload_identity, payload_version) VALUES (?, 'Command', ?, ?, ?, ?, '1.0.0')",
        id,
        name,
        status,
        tenantId,
        id);
    identityIds.add(id);
    payloadIds.add(id);
    return id;
  }

  /**
   * Seeds, in the tenant, the collector {@code collectorId} of a type of the tenant, the payloads X
   * and Y of that collector (external ids shared by every tenant of the test) and the payload Z of
   * no collector. Every payload is {@value #VERIFIED}.
   */
  private SeededTenant seedTenant(
      String tenantId, String collectorId, String xExternalId, String yExternalId) {
    String typeName = "payload-deprecation-type-" + collectorId;
    String typeId = seedCollectorType(tenantId, typeName);
    seedCollector(tenantId, collectorId, typeName, typeId);
    String xId = seedPayload(tenantId, xExternalId, typeId, "payload-deprecation-x", VERIFIED);
    String yId = seedPayload(tenantId, yExternalId, typeId, "payload-deprecation-y", VERIFIED);
    String zId = seedPayload(tenantId, null, null, "payload-deprecation-z", VERIFIED);
    return new SeededTenant(
        tenantId, new SeededPayloads(typeId, xExternalId, yExternalId, xId, yId, zId));
  }

  // -- request --

  private ResultActions deprecate(
      String uri, String tenantHeader, String collectorId, String processedExternalId)
      throws Exception {
    var request =
        post(uri + "/deprecate")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                asJsonString(
                    new PayloadsDeprecateInput(collectorId, List.of(processedExternalId))));
    if (tenantHeader != null) {
      request = request.header(TENANT_HEADER, tenantHeader);
    }
    return mvc.perform(request);
  }

  private static String pathOf(String tenantId) {
    return TENANT_PAYLOAD_URI.replace("{tenantId}", tenantId);
  }

  // -- read back --

  private String statusOf(String payloadId) {
    return jdbc.queryForObject(
        "SELECT payload_status FROM payloads WHERE payload_id = ?", String.class, payloadId);
  }

  /**
   * X processed, Y deprecated, Z of no collector untouched: the outcome of the request in a tenant.
   */
  private void assertDeprecated(SeededTenant tenant) {
    assertStatuses(tenant, VERIFIED, DEPRECATED, VERIFIED);
  }

  private void assertUntouched(SeededTenant tenant) {
    assertStatuses(tenant, VERIFIED, VERIFIED, VERIFIED);
  }

  private void assertStatuses(SeededTenant tenant, String x, String y, String z) {
    SeededPayloads payloads = tenant.payloads();
    assertThat(
            List.of(statusOf(payloads.xId()), statusOf(payloads.yId()), statusOf(payloads.zId())))
        .as("payload_status of X, Y and Z in tenant " + tenant.tenantId())
        .containsExactly(x, y, z);
  }

  @Nested
  @DisplayName("a caller of the default tenant and of tenant B")
  class MemberOfDefaultAndB {

    @BeforeEach
    void joinTheDefaultTenant() {
      tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT);
    }

    @Test
    @DisplayName("given_noSelector_should_deprecateOnlyTheDefaultTenantPayloads")
    void given_noSelector_should_deprecateOnlyTheDefaultTenantPayloads() throws Exception {
      // Arrange
      String tenantB = createTenant("payload-deprecation-default-b");
      String collectorId = UUID.randomUUID().toString();
      String x = "payload-deprecation-x-" + UUID.randomUUID();
      String y = "payload-deprecation-y-" + UUID.randomUUID();
      SeededTenant inDefault = seedTenant(DEFAULT_TENANT, collectorId, x, y);
      SeededTenant inB = seedTenant(tenantB, collectorId, x, y);

      // Act
      ResultActions result = deprecate(PAYLOAD_URI, null, collectorId, x);

      // Assert
      result.andExpect(status().isOk());
      assertDeprecated(inDefault);
      assertUntouched(inB);
    }

    @Test
    @DisplayName("given_headerNamingB_should_deprecateOnlyTenantBPayloads")
    void given_headerNamingB_should_deprecateOnlyTenantBPayloads() throws Exception {
      // Arrange
      String tenantB = createTenant("payload-deprecation-header-b");
      String collectorId = UUID.randomUUID().toString();
      String x = "payload-deprecation-x-" + UUID.randomUUID();
      String y = "payload-deprecation-y-" + UUID.randomUUID();
      SeededTenant inDefault = seedTenant(DEFAULT_TENANT, collectorId, x, y);
      SeededTenant inB = seedTenant(tenantB, collectorId, x, y);

      // Act
      ResultActions result = deprecate(PAYLOAD_URI, tenantB, collectorId, x);

      // Assert
      result.andExpect(status().isOk());
      assertDeprecated(inB);
      assertUntouched(inDefault);
    }

    @Test
    @DisplayName("given_pathOfB_should_deprecateOnlyTenantBPayloads")
    void given_pathOfB_should_deprecateOnlyTenantBPayloads() throws Exception {
      // Arrange
      String tenantB = createTenant("payload-deprecation-path-b");
      String collectorId = UUID.randomUUID().toString();
      String x = "payload-deprecation-x-" + UUID.randomUUID();
      String y = "payload-deprecation-y-" + UUID.randomUUID();
      SeededTenant inDefault = seedTenant(DEFAULT_TENANT, collectorId, x, y);
      SeededTenant inB = seedTenant(tenantB, collectorId, x, y);

      // Act
      ResultActions result = deprecate(pathOf(tenantB), null, collectorId, x);

      // Assert
      result.andExpect(status().isOk());
      assertDeprecated(inB);
      assertUntouched(inDefault);
    }

    @Test
    @DisplayName("given_pathOfBAndACollectorOfTheDefaultTenantOnly_should_changeNothing")
    void given_pathOfBAndACollectorOfTheDefaultTenantOnly_should_changeNothing() throws Exception {
      // Arrange
      String tenantB = createTenant("payload-deprecation-unknown-b");
      String defaultOnlyCollectorId = UUID.randomUUID().toString();
      String tenantBCollectorId = UUID.randomUUID().toString();
      String x = "payload-deprecation-x-" + UUID.randomUUID();
      String y = "payload-deprecation-y-" + UUID.randomUUID();
      SeededTenant inDefault = seedTenant(DEFAULT_TENANT, defaultOnlyCollectorId, x, y);
      SeededTenant inB = seedTenant(tenantB, tenantBCollectorId, x, y);

      // Act
      ResultActions result = deprecate(pathOf(tenantB), null, defaultOnlyCollectorId, x);

      // Assert
      result.andExpect(status().isOk());
      assertUntouched(inDefault);
      assertUntouched(inB);
    }
  }

  @Nested
  @DisplayName("a caller of tenants B and C, not of the default tenant")
  class MemberOfBAndCWithoutTheDefaultTenant {

    @Test
    @DisplayName("given_noSelector_should_refuseTheDeprecationAndChangeNothing")
    void given_noSelector_should_refuseTheDeprecationAndChangeNothing() throws Exception {
      // Arrange
      String tenantB = createTenant("payload-deprecation-b");
      String tenantC = createTenant("payload-deprecation-c");
      String collectorId = UUID.randomUUID().toString();
      String x = "payload-deprecation-x-" + UUID.randomUUID();
      String y = "payload-deprecation-y-" + UUID.randomUUID();
      SeededTenant inB = seedTenant(tenantB, collectorId, x, y);
      SeededTenant inC = seedTenant(tenantC, collectorId, x, y);

      // Act
      ResultActions result = deprecate(PAYLOAD_URI, null, collectorId, x);

      // Assert
      result
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("TENANT_SELECTOR_REQUIRED"));
      assertUntouched(inB);
      assertUntouched(inC);
    }

    @Test
    @DisplayName("given_headerNamingBAndC_should_refuseTheDeprecationAndChangeNothing")
    void given_headerNamingBAndC_should_refuseTheDeprecationAndChangeNothing() throws Exception {
      // Arrange
      String tenantB = createTenant("payload-deprecation-header-bc-b");
      String tenantC = createTenant("payload-deprecation-header-bc-c");
      String collectorId = UUID.randomUUID().toString();
      String x = "payload-deprecation-x-" + UUID.randomUUID();
      String y = "payload-deprecation-y-" + UUID.randomUUID();
      SeededTenant inB = seedTenant(tenantB, collectorId, x, y);
      SeededTenant inC = seedTenant(tenantC, collectorId, x, y);

      // Act
      ResultActions result = deprecate(PAYLOAD_URI, tenantB + "," + tenantC, collectorId, x);

      // Assert
      result
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("TENANT_WRITE_SCOPE"));
      assertUntouched(inB);
      assertUntouched(inC);
    }
  }
}
