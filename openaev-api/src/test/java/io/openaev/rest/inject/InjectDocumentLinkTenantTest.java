package io.openaev.rest.inject;

import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.rest.inject.InjectApi.INJECT_URI;
import static io.openaev.rest.inject.InjectApi.TENANT_INJECT_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.TagLinkJdbc.column;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Document;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.DocumentRepository;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.rest.inject.form.InjectDocumentInput;
import io.openaev.rest.inject.form.InjectInput;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * A simulation inject resolves the document ids of a create or an edit within its own tenant, never
 * within the wider scope of the request that carries them.
 *
 * <p>The simulation and the inject live in the default tenant, the one the ambient thread-local
 * falls back to. On the non-prefixed route {@code X-Tenant-Ids} can name several tenants, and these
 * edits carry no tenant selector, so the request scope holds all of them. A document id of another
 * tenant is treated like an unknown one: on create that already answers 404 (the same as a wholly
 * unknown id); on edit it is silently skipped (the same as any other unknown id on that endpoint),
 * so only the document(s) resolving inside the inject's own tenant get linked. The links are read
 * through {@code injects_documents} on the test connection, which the statement inspector does not
 * rewrite.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("A simulation inject links only the documents of its own tenant")
class InjectDocumentLinkTenantTest extends IntegrationTest {

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String INJECT_DOCUMENTS =
      "SELECT document_id FROM injects_documents WHERE inject_id = ?";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private ExerciseService exerciseService;
  @Autowired private InjectorContractFixture injectorContractFixture;
  @Autowired private DocumentRepository documentRepository;

  private String otherTenant;
  private String exercise;
  private String injectorContract;
  private String documentInOther;
  private String documentInDefault;

  @BeforeEach
  void seedBothTenants() throws Exception {
    // The caller belongs to both tenants, so the header can name both of them.
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    otherTenant = tenantHelper.createTenantWithCurrentUser("inject-document-link-o").getId();

    Exercise createdExercise = new Exercise();
    createdExercise.setName("inject-document-link-exercise");
    createdExercise.setTenant(new Tenant(Tenant.DEFAULT_TENANT_UUID));
    exercise = exerciseService.createExercise(createdExercise).getId();

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
        "given a simulation of the default tenant when an inject is created naming a document of"
            + " the other tenant then the create is refused rather than linking it")
    void given_bodyNamingTheOtherTenant_should_refuseTheCreate() throws Exception {
      // -- Arrange --
      armRequest();
      InjectInput input = baseInput();
      input.setDocuments(List.of(documentInput(documentInOther)));

      // -- Act & Assert: the id does not resolve inside the simulation's own tenant, so it is
      // treated like an unknown document id, which this create already answers with a 404.
      mvc.perform(
              post(EXERCISE_URI + "/{exerciseId}/injects", exercise)
                  .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + otherTenant)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName(
        "given a simulation inject of the default tenant when it is edited with a document of"
            + " each tenant then only the document of the default tenant is linked")
    void given_bodyNamingBothTenants_should_linkOnlyTheDefaultTenantDocument_onUpdateInject()
        throws Exception {
      // -- Arrange --
      armRequest();
      String injectId = createInject();
      InjectInput input = baseInput();
      input.setDocuments(List.of(documentInput(documentInOther), documentInput(documentInDefault)));

      // -- Act --
      mvc.perform(
              put(INJECT_URI + "/{exerciseId}/{injectId}", exercise, injectId)
                  .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + otherTenant)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, INJECT_DOCUMENTS, injectId))
          .as("documents linked: kept %s, dropped %s", documentInDefault, documentInOther)
          .containsExactly(documentInDefault);
    }
  }

  @Nested
  @DisplayName("Prefixed route")
  class PrefixedRoute {

    @Test
    @DisplayName(
        "given a simulation inject of the default tenant when the body under its path names the"
            + " document of the other tenant then none is linked")
    void given_bodyNamingTheOtherTenant_should_linkNothing() throws Exception {
      // -- Arrange --
      armRequest();
      String injectId = createInject();
      InjectInput input = baseInput();
      input.setDocuments(List.of(documentInput(documentInOther)));

      // -- Act --
      mvc.perform(
              put(
                      TENANT_INJECT_URI + "/{exerciseId}/{injectId}",
                      Tenant.DEFAULT_TENANT_UUID,
                      exercise,
                      injectId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, INJECT_DOCUMENTS, injectId)).isEmpty();
    }

    @Test
    @DisplayName(
        "given a simulation inject of the default tenant when the body under its path names its"
            + " own document then it is linked")
    void given_bodyNamingItsOwnDocument_should_linkIt() throws Exception {
      // -- Arrange --
      armRequest();
      String injectId = createInject();
      InjectInput input = baseInput();
      input.setDocuments(List.of(documentInput(documentInDefault)));

      // -- Act --
      mvc.perform(
              put(
                      TENANT_INJECT_URI + "/{exerciseId}/{injectId}",
                      Tenant.DEFAULT_TENANT_UUID,
                      exercise,
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

  private InjectInput baseInput() {
    InjectInput input = new InjectInput();
    input.setTitle("inject-document-link");
    input.setInjectorContract(injectorContract);
    input.setDependsDuration(0L);
    return input;
  }

  private InjectDocumentInput documentInput(String documentId) {
    InjectDocumentInput input = new InjectDocumentInput();
    input.setDocumentId(documentId);
    input.setAttached(true);
    return input;
  }

  private String createInject() throws Exception {
    String response =
        mvc.perform(
                post(EXERCISE_URI + "/{exerciseId}/injects", exercise)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(baseInput()))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    // Flush and clear: the create built the inject's documents as an immutable Stream.toList(),
    // and without this the update below would reuse that same managed instance from the shared
    // test transaction's persistence context instead of reloading it, as two separate production
    // requests would. See InjectService#buildInject.
    entityManager.flush();
    entityManager.clear();
    return JsonPath.read(response, "$.inject_id");
  }

  private String saveDocument(String tenantId) {
    Document document = DocumentFixture.getDocumentJpeg();
    document.setName("inject-document-link-" + UUID.randomUUID());
    document.setTenant(new Tenant(tenantId));
    return documentRepository.save(document).getId();
  }

  /**
   * Each seeded row carries the tenant the simulation relies on, then the ambient tenant is reset
   * to the default one the request thread of the header route carries.
   */
  private void armRequest() {
    assertThat(
            column(
                entityManager,
                "SELECT tenant_id FROM documents WHERE document_id = ?",
                documentInOther))
        .containsExactly(otherTenant);
    assertThat(
            column(
                entityManager, "SELECT tenant_id FROM exercises WHERE exercise_id = ?", exercise))
        .containsExactly(Tenant.DEFAULT_TENANT_UUID);
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }
}
