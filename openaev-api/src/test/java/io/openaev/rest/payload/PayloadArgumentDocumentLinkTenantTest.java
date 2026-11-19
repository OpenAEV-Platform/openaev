package io.openaev.rest.payload;

import static io.openaev.rest.payload.PayloadApi.PAYLOAD_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.TagLinkJdbc.column;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Document;
import io.openaev.database.model.Payload;
import io.openaev.database.model.PayloadArgument;
import io.openaev.database.model.PrimitiveType;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.DocumentRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegrationFactory;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.payload.form.PayloadCreateInput;
import io.openaev.rest.payload.form.PayloadUpdateInput;
import io.openaev.rest.payload.service.PayloadCreationService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.BuiltinConnectorRegistration;
import io.openaev.utils.fixtures.DocumentFixture;
import io.openaev.utils.fixtures.PayloadFixture;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * A Document-type payload argument's default value resolves in the tenant the payload is written
 * to, the same as the payload's own executable or file-drop document ({@link
 * PayloadDocumentLinkTenantTest}).
 *
 * <p>{@code updatePayload} carries no tenant selector at all, so the request's own scope (several
 * tenants on the header route) is the only scope in force while the argument is validated,
 * independently of which tenant the payload being edited belongs to: a cross-tenant id is genuinely
 * reachable there. {@code createPayload} pins the request scope to a single tenant before writing,
 * so no red is producible through that endpoint directly; the same unscoped resolution is still
 * reachable through an importer that opens a wider scope and supplies the write tenant explicitly,
 * which the importer-shaped test exercises directly at the service level.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("A Document-type payload argument resolves its default value in the payload's tenant")
class PayloadArgumentDocumentLinkTenantTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String ARGUMENT_KEY = "file";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private PayloadRepository payloadRepository;
  @Autowired private PayloadCreationService payloadCreationService;
  @Autowired private BuiltinConnectorRegistration builtinConnectorRegistration;
  @Autowired private OpenaevInjectorIntegrationFactory openaevInjectorIntegrationFactory;

  private String otherTenant;
  private String documentInOther;
  private String documentInDefault;

  @BeforeEach
  void seedBothTenants() throws Exception {
    // The built-in injector is registered per tenant, not seeded by a migration: without this,
    // synchroniseInjectorContractBasedOnPayload finds none and the update's own conversion to
    // PayloadOutput throws on a null injector contract, independently of this test's concern.
    builtinConnectorRegistration.register(
        openaevInjectorIntegrationFactory, Tenant.DEFAULT_TENANT_UUID);
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    otherTenant = tenantHelper.createTenantWithCurrentUser("payload-argument-link-o").getId();

    documentInOther = saveDocument(otherTenant);
    documentInDefault = saveDocument(Tenant.DEFAULT_TENANT_UUID);

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
        "given a command payload of the default tenant when the header names both tenants and"
            + " the body names a document argument of the other tenant then the update is"
            + " refused rather than storing it")
    void given_bodyNamingTheOtherTenantDocument_should_refuseTheUpdate() throws Exception {
      // -- Arrange --
      armRequest();
      String payloadId = saveCommandPayload(documentInDefault);
      PayloadUpdateInput input = PayloadInputFixture.getDefaultCommandPayloadUpdateInput(List.of());
      input.setArguments(List.of(documentArgument(documentInOther)));

      // -- Act --
      mvc.perform(
              put(PAYLOAD_URI + "/{payloadId}", payloadId)
                  .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + otherTenant)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isNotFound());

      // -- Assert: the argument's stored default value is unchanged --
      assertThat(column(entityManager, ARGUMENT_SQL, payloadId).get(0)).contains(documentInDefault);
    }

    @Test
    @DisplayName(
        "given a command payload of the default tenant when the body names its own document"
            + " argument then it is stored")
    void given_bodyNamingItsOwnDocument_should_storeIt() throws Exception {
      // -- Arrange --
      armRequest();
      String payloadId = saveCommandPayload(documentInDefault);
      String newDefaultDocument = saveDocument(Tenant.DEFAULT_TENANT_UUID);
      PayloadUpdateInput input = PayloadInputFixture.getDefaultCommandPayloadUpdateInput(List.of());
      input.setArguments(List.of(documentArgument(newDefaultDocument)));

      // -- Act --
      mvc.perform(
              put(PAYLOAD_URI + "/{payloadId}", payloadId)
                  .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + otherTenant)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // -- Assert --
      assertThat(column(entityManager, ARGUMENT_SQL, payloadId).get(0))
          .contains(newDefaultDocument);
    }
  }

  @Nested
  @DisplayName("the shared resolution an importer reaches with an explicit write tenant")
  class ImporterShapedCall {

    @Test
    @DisplayName(
        "given a transaction scoped to both tenants when the write tenant is pinned to the"
            + " default one then the document argument still resolves only inside it")
    void given_transactionScopedToBothTenants_should_resolveTheArgumentInTheWriteTenantAlone() {
      // -- Arrange: a scope as wide as an importer's (several tenants), the write tenant pinned
      // explicitly to the default one, same as V1_DataImporter#toPayload resolves it.
      PayloadCreateInput input =
          PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine(List.of());
      input.setArguments(List.of(documentArgument(documentInOther)));
      TxCtx wideScope = TxCtx.forTenants(Set.of(Tenant.DEFAULT_TENANT_UUID, otherTenant));

      // -- Act & Assert --
      assertThrows(
          ElementNotFoundException.class,
          () -> payloadCreationService.createPayload(wideScope, Tenant.DEFAULT_TENANT_UUID, input));
    }
  }

  // -- Helpers --

  private static final String ARGUMENT_SQL =
      "SELECT payload_arguments FROM payloads WHERE payload_id = ?";

  private static PayloadArgument documentArgument(String documentId) {
    return PayloadFixture.createPayloadArgument(
        ARGUMENT_KEY, PrimitiveType.Document, documentId, null);
  }

  private String saveCommandPayload(String documentId) {
    Payload payload =
        PayloadFixture.createDefaultCommandWithArguments(List.of(documentArgument(documentId)));
    payload.setTenant(new Tenant(Tenant.DEFAULT_TENANT_UUID));
    return payloadRepository.save(payload).getId();
  }

  private String saveDocument(String tenantId) {
    Document document = DocumentFixture.getDocumentJpeg();
    document.setName("payload-argument-link-" + UUID.randomUUID());
    document.setTenant(new Tenant(tenantId));
    return documentRepository.save(document).getId();
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
                documentInOther))
        .containsExactly(otherTenant);
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }
}
