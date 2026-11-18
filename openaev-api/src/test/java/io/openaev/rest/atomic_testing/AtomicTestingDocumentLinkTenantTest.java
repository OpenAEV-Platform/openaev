package io.openaev.rest.atomic_testing;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.rest.atomic_testing.AtomicTestingApi.ATOMIC_TESTING_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.TagLinkJdbc.column;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Document;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.DocumentRepository;
import io.openaev.rest.atomic_testing.form.AtomicTestingInput;
import io.openaev.rest.inject.form.InjectDocumentInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.DocumentFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
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
 * An atomic testing resolves the document ids of a create or an edit within its own tenant, never
 * within the wider scope of the request that carries them.
 *
 * <p>An atomic testing has no parent simulation or scenario to take its tenant from: a create pins
 * the single tenant of the request scope ({@link io.openaev.config.RequireTenantSelector}), but an
 * edit resolves the tenant of the atomic testing already loaded, independently of how wide the
 * request's own scope is. On the non-prefixed route {@code X-Tenant-Ids} can name several tenants,
 * and the edit carries no tenant selector, so the request scope holds all of them. A document id of
 * another tenant is treated like an unknown one: the lookup refuses with a 404, the same way it
 * already does for a document id that does not exist at all. The links are read through {@code
 * injects_documents} on the test connection, which the statement inspector does not rewrite. The
 * class arms the production list.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("An atomic testing links only the documents of its own tenant")
class AtomicTestingDocumentLinkTenantTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String INJECT_DOCUMENTS =
      "SELECT document_id FROM injects_documents WHERE inject_id = ?";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private InjectorContractFixture injectorContractFixture;
  @Autowired private DocumentRepository documentRepository;

  private String otherTenant;
  private String injectorContract;
  private String documentInOther;
  private String documentInDefault;

  @BeforeEach
  void seedBothTenants() throws Exception {
    // The caller belongs to both tenants, so the header can name both of them.
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    otherTenant = tenantHelper.createTenantWithCurrentUser("atomic-document-link-o").getId();

    documentInOther = saveDocument(otherTenant);
    documentInDefault = saveDocument(Tenant.DEFAULT_TENANT_UUID);

    TenantContext.clearCurrentTenant();
    injectorContract = injectorContractFixture.getWellKnownSingleEmailContract().getId();
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
        "given an atomic testing of the default tenant when it is edited with a document of the"
            + " other tenant then the edit fails and nothing is linked")
    void given_bodyNamingTheOtherTenant_should_refuseTheEdit() throws Exception {
      // -- Arrange --
      armRequest();
      String injectId = createAtomicTesting();
      AtomicTestingInput input = baseInput();
      input.setDocuments(List.of(documentInput(documentInOther)));

      // -- Act & Assert: the id does not resolve inside the atomic testing's own tenant, so it is
      // treated like any other unknown document id, refused with a 404.
      mvc.perform(
              put(ATOMIC_TESTING_URI + "/{injectId}", injectId)
                  .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + otherTenant)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isNotFound());
      assertThat(column(entityManager, INJECT_DOCUMENTS, injectId)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Prefixed route")
  class PrefixedRoute {

    @Test
    @DisplayName(
        "given an atomic testing of the default tenant when the body under its path names the"
            + " document of the other tenant then the edit fails and nothing is linked")
    void given_bodyNamingTheOtherTenant_should_refuseTheEditOnThePrefixedRoute() throws Exception {
      // -- Arrange --
      armRequest();
      String injectId = createAtomicTesting();
      AtomicTestingInput input = baseInput();
      input.setDocuments(List.of(documentInput(documentInOther)));

      // -- Act & Assert --
      mvc.perform(
              put(
                      TENANT_PREFIX + "/atomic-testings/{injectId}",
                      Tenant.DEFAULT_TENANT_UUID,
                      injectId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isNotFound());
      assertThat(column(entityManager, INJECT_DOCUMENTS, injectId)).isEmpty();
    }

    @Test
    @DisplayName(
        "given an atomic testing of the default tenant when the body under its path names its own"
            + " document then it is linked")
    void given_bodyNamingItsOwnDocument_should_linkIt() throws Exception {
      // -- Arrange --
      armRequest();
      String injectId = createAtomicTesting();
      AtomicTestingInput input = baseInput();
      input.setDocuments(List.of(documentInput(documentInDefault)));

      // -- Act --
      mvc.perform(
              put(
                      TENANT_PREFIX + "/atomic-testings/{injectId}",
                      Tenant.DEFAULT_TENANT_UUID,
                      injectId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, INJECT_DOCUMENTS, injectId))
          .containsExactly(documentInDefault);
    }
  }

  // -- Helpers --

  private AtomicTestingInput baseInput() {
    AtomicTestingInput input = new AtomicTestingInput();
    input.setTitle("atomic-document-link");
    input.setInjectorContract(injectorContract);
    return input;
  }

  private InjectDocumentInput documentInput(String documentId) {
    InjectDocumentInput input = new InjectDocumentInput();
    input.setDocumentId(documentId);
    input.setAttached(true);
    return input;
  }

  private String createAtomicTesting() throws Exception {
    String response =
        mvc.perform(
                post(ATOMIC_TESTING_URI)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(baseInput()))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.inject_id");
  }

  private String saveDocument(String tenantId) {
    Document document = DocumentFixture.getDocumentJpeg();
    document.setName("atomic-document-link-" + UUID.randomUUID());
    document.setTenant(new Tenant(tenantId));
    return documentRepository.save(document).getId();
  }

  /**
   * Each seeded document carries the tenant the scenario relies on, then the ambient tenant is
   * reset to the default one the request thread of the header route carries.
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
