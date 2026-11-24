package io.openaev.rest.payload;

import static io.openaev.rest.payload.PayloadApi.PAYLOAD_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.TagLinkJdbc.column;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.ProductionTablesIntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Document;
import io.openaev.database.model.Domain;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.DocumentRepository;
import io.openaev.database.repository.DomainRepository;
import io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegrationFactory;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.payload.form.PayloadCreateInput;
import io.openaev.rest.payload.form.PayloadUpdateInput;
import io.openaev.rest.payload.service.PayloadCreationService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.BuiltinConnectorRegistration;
import io.openaev.utils.fixtures.DocumentFixture;
import io.openaev.utils.fixtures.PayloadInputFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
 * A payload's executable or file-drop document resolves in the tenant the payload is written to.
 * Covered here through an executable payload, whose built-in injector is seeded in every tenant
 * ({@code PayloadInputFixture.createDefaultPayloadCreateInputForExecutable}); the file-drop
 * resolution is the identical two lines in {@code PayloadCreationService}, {@code
 * PayloadUpdateService} and {@code PayloadUpsertService}.
 *
 * <p>{@code updatePayload} carries no tenant selector at all: the request's own scope (the header
 * route can name several tenants) is the only scope in force while the file document is resolved,
 * independently of which tenant the payload being edited belongs to, so a cross-tenant id is
 * genuinely reachable there. {@code createPayload} and {@code upsertPayload} pin the request scope
 * to a single tenant before writing ({@link io.openaev.config.TenantWriteScopeResolver}), so no red
 * is producible through either of those two endpoints directly; the same unscoped resolution is
 * still reachable through an importer that opens a wider scope and supplies the write tenant
 * explicitly ({@link PayloadCreationService#createPayload(TxCtx, String,
 * io.openaev.rest.payload.form.PayloadCreateInput)}, used by {@code V1_DataImporter} and the threat
 * arsenal import), which the last test exercises directly at the service level.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("A payload resolves its executable or file-drop document in its own write tenant")
class PayloadDocumentLinkTenantTest extends ProductionTablesIntegrationTest {

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String EXECUTABLE_FILE_SQL =
      "SELECT executable_file FROM payloads WHERE payload_id = ?";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private DomainRepository domainRepository;
  @Autowired private PayloadCreationService payloadCreationService;
  @Autowired private BuiltinConnectorRegistration builtinConnectorRegistration;
  @Autowired private OpenaevInjectorIntegrationFactory openaevInjectorIntegrationFactory;

  private String otherTenant;
  private String domainInDefault;
  private String fileInOther;
  private String fileInDefault;

  @BeforeEach
  void seedBothTenants() throws Exception {
    // The built-in injectors (Executable among them) are registered per tenant, not seeded by a
    // migration: without this, synchroniseInjectorContractBasedOnPayload finds none and returns
    // null for the default tenant's payload.
    builtinConnectorRegistration.register(
        openaevInjectorIntegrationFactory, Tenant.DEFAULT_TENANT_UUID);
    // The caller belongs to both tenants, so the header can name both of them.
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    otherTenant = tenantHelper.createTenantWithCurrentUser("payload-document-link-o").getId();

    domainInDefault = saveDomain(Tenant.DEFAULT_TENANT_UUID);
    fileInOther = saveDocument(otherTenant);
    fileInDefault = saveDocument(Tenant.DEFAULT_TENANT_UUID);

    TenantContext.clearCurrentTenant();
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("updatePayload, which carries no tenant selector")
  class UpdatePayload {

    @Test
    @DisplayName(
        "given an executable payload of the default tenant when the header names both tenants"
            + " and the body names the file of the other tenant then the update is refused"
            + " rather than linking it")
    void given_bodyNamingTheOtherTenantFile_should_refuseTheUpdate() throws Exception {
      // -- Arrange --
      armRequest();
      String payloadId = createExecutablePayload(fileInDefault);
      PayloadUpdateInput input =
          PayloadInputFixture.getDefaultExecutablePayloadUpdateInput(List.of(domainInDefault));
      input.setExecutableFile(fileInOther);

      // -- Act --
      mvc.perform(
              put(PAYLOAD_URI + "/{payloadId}", payloadId)
                  .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + otherTenant)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isNotFound());

      // -- Assert --
      assertThat(column(entityManager, EXECUTABLE_FILE_SQL, payloadId))
          .containsExactly(fileInDefault);
    }

    @Test
    @DisplayName(
        "given an executable payload of the default tenant when the body names its own file then"
            + " it is linked")
    void given_bodyNamingItsOwnFile_should_linkIt() throws Exception {
      // -- Arrange --
      armRequest();
      String payloadId = createExecutablePayload(fileInDefault);
      PayloadUpdateInput input =
          PayloadInputFixture.getDefaultExecutablePayloadUpdateInput(List.of(domainInDefault));
      String newDefaultFile = saveDocument(Tenant.DEFAULT_TENANT_UUID);
      input.setExecutableFile(newDefaultFile);

      // -- Act --
      mvc.perform(
              put(PAYLOAD_URI + "/{payloadId}", payloadId)
                  .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + otherTenant)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // -- Assert --
      assertThat(column(entityManager, EXECUTABLE_FILE_SQL, payloadId))
          .containsExactly(newDefaultFile);
    }
  }

  @Nested
  @DisplayName("the shared resolution an importer reaches with an explicit write tenant")
  class ImporterShapedCall {

    @Test
    @DisplayName(
        "given a transaction scoped to both tenants when the write tenant is pinned to the"
            + " default one then the executable file still resolves only inside it")
    void given_transactionScopedToBothTenants_should_resolveTheFileInTheWriteTenantAlone() {
      // -- Arrange: a scope as wide as an importer's (several tenants), the write tenant pinned
      // explicitly to the default one, same as V1_DataImporter#toPayload resolves it.
      PayloadCreateInput input =
          PayloadInputFixture.createDefaultPayloadCreateInputForExecutable(
              List.of(domainInDefault));
      input.setExecutableFile(fileInOther);
      TxCtx wideScope = TxCtx.forTenants(Set.of(Tenant.DEFAULT_TENANT_UUID, otherTenant));

      // -- Act & Assert --
      assertThrows(
          ElementNotFoundException.class,
          () -> payloadCreationService.createPayload(wideScope, Tenant.DEFAULT_TENANT_UUID, input));
    }
  }

  // -- Helpers --

  private String createExecutablePayload(String executableFile) throws Exception {
    PayloadCreateInput input =
        PayloadInputFixture.createDefaultPayloadCreateInputForExecutable(List.of(domainInDefault));
    input.setExecutableFile(executableFile);
    String response =
        mvc.perform(
                post(PAYLOAD_URI)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.payload_id");
  }

  private String saveDocument(String tenantId) {
    Document document = DocumentFixture.getDocumentJpeg();
    document.setName("payload-document-link-" + UUID.randomUUID());
    document.setTenant(new Tenant(tenantId));
    return documentRepository.save(document).getId();
  }

  private String saveDomain(String tenantId) {
    Domain domain = new Domain();
    domain.setName("payload-document-link-" + UUID.randomUUID());
    domain.setColor("#FFFFFF");
    domain.setTenant(new Tenant(tenantId));
    return domainRepository.save(domain).getId();
  }

  /**
   * Each seeded row carries the tenant the scenario relies on, then the ambient tenant is reset to
   * the default one the request thread of the header route carries.
   */
  private void armRequest() {
    assertThat(
            column(
                entityManager,
                "SELECT tenant_id FROM documents WHERE document_id = ?",
                fileInOther))
        .containsExactly(otherTenant);
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }
}
