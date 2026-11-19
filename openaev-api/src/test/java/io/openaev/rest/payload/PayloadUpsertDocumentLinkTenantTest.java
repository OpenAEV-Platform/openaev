package io.openaev.rest.payload;

import static org.junit.jupiter.api.Assertions.assertThrows;

import io.openaev.ProductionTablesIntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Document;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.DocumentRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.payload.form.PayloadUpsertInput;
import io.openaev.rest.payload.service.PayloadUpsertService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.DocumentFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.fixtures.PayloadInputFixture;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code PayloadUpsertService#upsertPayload} is reached only in a shape {@code
 * TenantWriteScopeResolver} has already narrowed to a single tenant (the HTTP route pins it with
 * {@code @RequireTenantSelector}; a collector calls it directly the same way), so unlike {@code
 * PayloadCreationService}'s importer overload there is no wider scope to construct here. What is
 * missing is a test proving that, inside that single-tenant write, the executable file named by the
 * request still resolves within it rather than anywhere the id happens to exist: both the
 * create-from-upsert and the update-from-upsert branch call {@code
 * DocumentService#documentInTenant} (see {@code PayloadUpsertService#createPayloadFromUpsert} and
 * {@code #updatePayloadFromUpsert}), and reverting either call to the unscoped {@code
 * DocumentService#document} would link a foreign-tenant file with nothing here going red first.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("A payload upsert resolves its executable document in its own write tenant")
class PayloadUpsertDocumentLinkTenantTest extends ProductionTablesIntegrationTest {

  @Autowired private PayloadUpsertService payloadUpsertService;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private PayloadRepository payloadRepository;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private EntityManager entityManager;

  private String otherTenant;
  private String fileInOther;

  @BeforeEach
  void seedOtherTenant() throws Exception {
    otherTenant =
        tenantHelper.createTenantWithCurrentUser("payload-upsert-document-link-o").getId();
    fileInOther = saveDocument(otherTenant);
    TenantContext.clearCurrentTenant();
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName(
      "given no payload yet for this external id when the body names a file of another tenant"
          + " then the create-from-upsert path is refused rather than linking it")
  void given_bodyNamingTheOtherTenantFile_should_refuseTheCreateFromUpsert() {
    // -- Arrange: an importer/collector-shaped call, a write scope already pinned to one tenant.
    PayloadUpsertInput input =
        PayloadInputFixture.getDefaultExecutablePayloadUpsertInput(
            "payload-upsert-create-" + UUID.randomUUID());
    input.setExecutableFile(fileInOther);
    TxCtx ctx = TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID);

    // -- Act & Assert --
    assertThrows(
        ElementNotFoundException.class, () -> payloadUpsertService.upsertPayload(ctx, input));
  }

  @Test
  @DisplayName(
      "given an existing payload of the default tenant when the body names a file of another"
          + " tenant then the update-from-upsert path is refused rather than linking it")
  void given_bodyNamingTheOtherTenantFile_should_refuseTheUpdateFromUpsert() {
    // -- Arrange: a payload the upsert's lookup will find, so the call takes the update branch.
    String externalId = "payload-upsert-update-" + UUID.randomUUID();
    String ownFile = saveDocument(Tenant.DEFAULT_TENANT_UUID);
    Payload existing =
        PayloadFixture.createDefaultExecutable(documentRepository.findById(ownFile).orElseThrow());
    existing.setExternalId(externalId);
    existing.setTenant(new Tenant(Tenant.DEFAULT_TENANT_UUID));
    payloadRepository.save(existing);
    entityManager.flush();

    PayloadUpsertInput input =
        PayloadInputFixture.getDefaultExecutablePayloadUpsertInput(externalId);
    input.setExecutableFile(fileInOther);
    TxCtx ctx = TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID);

    // -- Act & Assert --
    assertThrows(
        ElementNotFoundException.class, () -> payloadUpsertService.upsertPayload(ctx, input));
  }

  // -- Helpers --

  private String saveDocument(String tenantId) {
    Document document = DocumentFixture.getDocumentJpeg();
    document.setName("payload-upsert-document-link-" + UUID.randomUUID());
    document.setTenant(new Tenant(tenantId));
    return documentRepository.save(document).getId();
  }
}
