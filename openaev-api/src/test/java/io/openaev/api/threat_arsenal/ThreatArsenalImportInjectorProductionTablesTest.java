package io.openaev.api.threat_arsenal;

import static io.openaev.api.threat_arsenal.ThreatArsenalApi.TENANT_THREAT_ARSENAL_URL;
import static io.openaev.api.threat_arsenal.ThreatArsenalApi.THREAT_ARSENAL_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalAction;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.injectors.openaev.OpenAEVImplantContract;
import io.openaev.jsonapi.IncludeOptions;
import io.openaev.jsonapi.ZipJsonApi;
import io.openaev.service.threat_arsenal.ThreatArsenalImportService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.PayloadComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code injectors} is a tenant table, so the statement inspector already restricts {@code
 * findAllByPayloads(true)} to the scope a caller selects. A single-tenant HTTP selector (the header
 * or the tenant-prefixed route) therefore hides a foreign tenant's payload injector from the lookup
 * before {@link ThreatArsenalImportService} ever runs its own write-tenant filter, so it cannot
 * exercise that filter. Only a scope that spans both the default tenant and the write tenant lets
 * the unfiltered lookup meet both tenants' copies, which is what the service-level case below sets
 * up directly. The header and tenant-prefixed cases are kept alongside it as end-to-end coverage of
 * the import on both routes once the table is armed.
 *
 * <p>Deliberately NOT {@code @Transactional}: the HTTP calls run in their own transaction, the way
 * a real request does, and the service-level case opens its own background transaction through
 * {@link TenantScopedTransaction}.
 */
@DisplayName("Importing a threat arsenal action links the payload injectors of the write tenant")
@WithMockUser(isAdmin = true)
class ThreatArsenalImportInjectorProductionTablesTest extends IntegrationTest {

  private static final String TENANT_HEADER = "X-Tenant-Ids";

  @Autowired private MockMvc mvc;
  @Autowired private EntityManager entityManager;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private PayloadComposer payloadComposer;
  @Autowired private InjectorFixture injectorFixture;
  @Autowired private InjectorRepository injectorRepository;
  @Autowired private ZipJsonApi<InjectorContract> injectorContractZipJsonApi;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private ThreatArsenalImportService threatArsenalImportService;

  private String tenantB;
  private InjectorContractComposer.Composer sourceContractComposer;
  private Injector foreignInjector;

  @BeforeEach
  void setUp() throws Exception {
    injectorContractComposer.reset();
    payloadComposer.reset();
    tenantB = tenantHelper.createTenantWithCurrentUser("arsenal-import-injectors-prod-b").getId();
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
  }

  @AfterEach
  void cleanup() {
    TenantContext.clearCurrentTenant();
    // The source contract, its payload and the foreign injector are planted under the default
    // tenant by exportOfSourceContractWithForeignInjector(): the well-known injector lookup there
    // searches by type under the ambient tenant, so a row left behind by one test's foreign
    // injector (same type) is a candidate the next test's lookup can mistake for the real
    // well-known injector, whatever the lookup's row order.
    tenantTx.execute(
        TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
        () -> {
          if (sourceContractComposer != null) {
            sourceContractComposer.delete();
          }
          if (foreignInjector != null) {
            injectorRepository.delete(foreignInjector);
          }
        });
    if (tenantB != null) {
      tenantHelper.deleteCommittedTenants(tenantB);
    }
  }

  @Test
  @DisplayName("given_aScopeSpanningBothTenantsAndWriteTenantB_should_linkWriteTenantInjectorOnly")
  void given_aScopeSpanningBothTenantsAndWriteTenantB_should_linkWriteTenantInjectorOnly() {
    // Arrange
    MockMultipartFile file = multipartFile(exportOfSourceContractWithForeignInjector());

    // Act
    InjectorContract imported =
        tenantTx.execute(
            bothTenantsScope(),
            () -> {
              try {
                ThreatArsenalAction action =
                    threatArsenalImportService.importThreatArsenalAction(
                        bothTenantsScope(), file, tenantB);
                return injectorContractRepository.findById(action.getId()).orElseThrow();
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });

    // Assert
    assertThat(imported.getInjectors())
        .as("the imported contract links the payload injector of the write tenant only")
        .isNotEmpty()
        .extracting(Injector::getTenantId)
        .containsOnly(tenantB);
  }

  @Test
  @DisplayName("given_theHeaderRouteSelectsWriteTenantB_should_linkWriteTenantInjectorOnly")
  void given_theHeaderRouteSelectsWriteTenantB_should_linkWriteTenantInjectorOnly()
      throws Exception {
    // Arrange
    MockMultipartFile file = multipartFile(exportOfSourceContractWithForeignInjector());

    // Act
    String response =
        mvc.perform(
                multipart(THREAT_ARSENAL_URL + "/import")
                    .file(file)
                    .header(TENANT_HEADER, tenantB)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Assert
    assertThat(importedInjectorTenants(response)).containsOnly(tenantB);
  }

  @Test
  @DisplayName("given_theTenantPrefixedRouteSelectsWriteTenantB_should_linkWriteTenantInjectorOnly")
  void given_theTenantPrefixedRouteSelectsWriteTenantB_should_linkWriteTenantInjectorOnly()
      throws Exception {
    // Arrange
    MockMultipartFile file = multipartFile(exportOfSourceContractWithForeignInjector());

    // Act
    String response =
        mvc.perform(
                multipart(TENANT_THREAT_ARSENAL_URL + "/import", tenantB).file(file).with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Assert
    assertThat(importedInjectorTenants(response)).containsOnly(tenantB);
  }

  // -- Helpers --

  private TxCtx bothTenantsScope() {
    return TxCtx.forTenants(List.of(Tenant.DEFAULT_TENANT_UUID, tenantB));
  }

  private static MockMultipartFile multipartFile(byte[] export) {
    return new MockMultipartFile("file", "action.zip", "application/zip", export);
  }

  private List<String> importedInjectorTenants(String response) {
    String importedId = JsonPath.read(response, "$.injector_contract_id");
    return tenantTx.execute(
        TxCtx.forTenant(tenantB),
        () ->
            injectorContractRepository.findById(importedId).orElseThrow().getInjectors().stream()
                .map(Injector::getTenantId)
                .toList());
  }

  /**
   * Builds and exports a source injector contract with a payload, then plants a payload-capable
   * injector of a distinct id under the default tenant: a foreign tenant to the write tenant used
   * by every test here. The well-known injector linked for the export's own validity (its id
   * repeats across tenants, #8485) does not need this: {@code InjectorContract#addInjector} dedupes
   * same-id injectors by id, so whichever tenant's copy an unordered lookup meets first is the only
   * one that survives; a distinct id is never deduped that way, so it always pollutes the result
   * when the write-tenant filter is not applied, whatever the lookup's row order.
   */
  private byte[] exportOfSourceContractWithForeignInjector() {
    return tenantTx.execute(
        TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
        () -> {
          try {
            sourceContractComposer =
                injectorContractComposer
                    .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
                    .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
                    .withPayload(payloadComposer.forPayload(PayloadFixture.createDefaultCommand()))
                    .persist();
            InjectorContract source = sourceContractComposer.get();
            foreignInjector = createForeignPayloadInjector(Tenant.DEFAULT_TENANT_UUID);
            entityManager.flush();
            entityManager.clear();
            byte[] export =
                injectorContractZipJsonApi
                    .handleExport(
                        injectorContractRepository.findById(source.getId()).orElseThrow(),
                        null,
                        IncludeOptions.of(
                            Map.of(
                                "exclude from action export",
                                IncludeOptions.IncludeMode.FALSE,
                                "exclude from payload export",
                                IncludeOptions.IncludeMode.FALSE)),
                        null)
                    .getBody();
            entityManager.clear();
            return export;
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  /** A payload-capable injector of a distinct id, held by {@code tenantId}. */
  private Injector createForeignPayloadInjector(String tenantId) {
    Injector injector =
        InjectorFixture.createInjector(
            UUID.randomUUID().toString(),
            "foreign-tenant-payload-injector",
            OpenAEVImplantContract.TYPE);
    injector.setTenantId(tenantId);
    injector.setPayloads(true);
    return injectorRepository.save(injector);
  }
}
