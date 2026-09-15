package io.openaev.rest.document;

import static io.openaev.rest.document.DocumentApi.DOCUMENT_API;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Document;
import io.openaev.database.model.Tenant;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The by-id request-scope guard must not tighten the behaviour of a request that resolves an empty
 * scope. On the non-prefixed route a caller with no tenant membership and no {@code X-Tenant-Ids}
 * selector resolves {@code TxCtx.missing()}: there is no request scope to hold the document to, so
 * the guard is not applied and the endpoint behaves as it did before the guard (the row is loaded
 * by primary key, which is exempt from the tenant filter). This class pins that today's behaviour
 * is preserved: a member-less caller still reads and writes a document of any tenant by id.
 *
 * <p>{@code @WithMockUser} builds a user with no row in {@code users_tenants}, so its scope
 * resolves to {@code TxCtx.missing()}. This class deliberately keeps the caller member-less: the
 * owning tenant is inserted as a bare row rather than through {@code TenantService.create}, whose
 * onboarding would enroll the platform admin in it and turn the resolved scope into a single-tenant
 * one. The document is then owned by a tenant the caller is not a member of, so the request scope
 * is genuinely empty rather than narrowed to another tenant.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Document by-id endpoints keep today's behaviour when the request scope is empty")
class DocumentByIdEmptyScopeTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  private String otherTenant;

  @BeforeEach
  void seedNonMemberTenant() {
    // A bare tenant row, not an onboarded one: onboarding would enroll the platform admin and give
    // the caller a single-tenant scope. Inserting it directly keeps the caller member-less, so a
    // request with no selector resolves an empty scope.
    Tenant tenant = TenantFixture.getTenant("doc-empty-scope-" + UUID.randomUUID());
    entityManager.persist(tenant);
    entityManager.flush();
    otherTenant = tenant.getId();
  }

  @AfterEach
  void clearContext() {
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName(
      "GET by id, non-prefixed route, member-less caller: another tenant's document is still"
          + " returned")
  void given_emptyRequestScope_should_returnDocumentByIdOnNonPrefixedRoute() throws Exception {
    // Arrange
    String id = seedDocument(otherTenant);

    // Act & Assert
    mvc.perform(get(DOCUMENT_API + "/{id}", id)).andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "PUT tags by id, non-prefixed route, member-less caller: another tenant's document is still"
          + " updated")
  void given_emptyRequestScope_should_updateTagsByIdOnNonPrefixedRoute() throws Exception {
    // Arrange
    String id = seedDocument(otherTenant);

    // Act
    String response =
        mvc.perform(
                put(DOCUMENT_API + "/{id}/tags", id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"tags\":[]}")
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Assert
    assertThatJson(response).node("document_id").isEqualTo(id);
  }

  private String seedDocument(String tenantId) {
    Document document = new Document();
    document.setName("doc-empty-" + UUID.randomUUID());
    document.setTarget(UUID.randomUUID() + ".txt");
    document.setType(MediaType.TEXT_PLAIN_VALUE);
    document.setTenant(new Tenant(tenantId));
    entityManager.persist(document);
    entityManager.flush();
    return document.getId();
  }
}
